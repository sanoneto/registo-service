package com.aneto.registo_horas_service.repository;


import com.aneto.registo_horas_service.dto.response.MonthlySummary;
import com.aneto.registo_horas_service.dto.response.PerfilResponse;
import com.aneto.registo_horas_service.models.RegistosHoras;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RegistroHorasRepository extends JpaRepository<RegistosHoras, Long> {

    List<RegistosHoras> findByUserName(String estagiarioUsername);

    @Query("SELECT SUM(r.horasTrabalhadas) FROM RegistosHoras r WHERE r.userName = :username")
    Double sumHorasCalculadasByEstagiarioUsername(@Param("username") String username);

    // 1. Uso de @Query para consulta nativa
    // 2. Uso de nativeQuery = true (IMPRESCINDÍVEL)
    // 3. Tipo de retorno List<MonthlySummary> (a sua nova interface/projeção)
    @Query(value = "SELECT TO_CHAR(data_registo, 'YYYY-MM') AS mes_e_ano, " +
            "SUM(horas_trabalhadas) AS total_horas_trabalhadas " +
            "FROM registos.registos_horas rh " +
            "GROUP BY mes_e_ano " +
            "ORDER BY mes_e_ano DESC",
            nativeQuery = true)
    List<MonthlySummary> findMonthlySummary();

    // Se precisar da versão do utilizador (que tinha no erro anterior, mas corrigida):
    @Query(value = "SELECT TO_CHAR(data_registo, 'YYYY-MM') AS mes_e_ano, " +
            "SUM(horas_trabalhadas) AS total_horas_trabalhadas " +
            "FROM registos.registos_horas rh " +
            "WHERE rh.username = :username " +
            "GROUP BY mes_e_ano " +
            "ORDER BY mes_e_ano DESC",
            nativeQuery = true)
    List<MonthlySummary> findMonthlySummaryByUsername(@Param("username") String username);

    Page<RegistosHoras> findByUserName(String name, Pageable pageable);

    Optional<RegistosHoras> findByPublicId(UUID attr0);

    @Query(
            value = "select u.username, tp.project_name, u.email, tp.required_hours,\n" +
                    "sum(r.horas_trabalhadas ) as Total_horas_trabalhadas \n" +
                    "from auth.tb_users u\n" +
                    "inner join registos.registos_horas r on u.username = r.username\n" +
                    "inner join auth.tb_projetos tp  on u.username = tp.username\n" +
                    "where u.username =:username\n" +
                    "group by u.username,  u.email, tp.required_hours, tp.project_name",
            nativeQuery = true
    )
    List<PerfilResponse> findTotalHoursAndRequiredHoursByUserName(@Param("username") String name );



}
