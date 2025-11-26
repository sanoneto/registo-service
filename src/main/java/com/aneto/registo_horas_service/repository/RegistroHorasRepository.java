package com.aneto.registo_horas_service.repository;


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


    Page<RegistosHoras> findByUserName(String name, Pageable pageable);


    Optional<RegistosHoras> findByPublicId(UUID attr0);

    @Query(
            value = "select u.username, u.email, u.required_hours,\n" +
                    "sum(r.horas_trabalhadas ) as Total_horas_trabalhadas \n" +
                    "from auth.tb_users u\n" +
                    "inner join registos.registos_horas r on u.username = r.username\n" +
                    "where u.username = :username\n" +
                    "group by u.username,  u.email, u.required_hours",
            nativeQuery = true
    )
    List<PerfilResponse> findTotalHoursAndRequiredHoursByUserName(@Param("username") String name );

}
