package com.aneto.registo_horas_service.repository;

import com.aneto.registo_horas_service.models.Training.RegistoTreino;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
@Repository
public interface RegistroTreinoRepository extends JpaRepository<RegistoTreino, Long> {

    // Soma o campo aulasFeitas de todos os treinos registados para o sócio
    @Query("SELECT SUM(r.aulasFeitas) FROM RegistoTreino r WHERE r.noSocio = :noSocio")
    Integer sumAulasFeitasByNoSocio(@Param("noSocio") String noSocio);

    // Método para a pesquisa no histórico (usado no Controller/Service)
    Page<RegistoTreino> findByNoSocioContainingIgnoreCaseOrPlanoPagamentoNomeSocioContainingIgnoreCase(
            String noSocio,
            String nomeSocio,
            Pageable pageable
    );

    // Para o histórico cronológico
    List<RegistoTreino> findByNoSocioOrderByDataDesc(String noSocio);
}