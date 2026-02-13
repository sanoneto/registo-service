package com.aneto.registo_horas_service.repository;

import com.aneto.registo_horas_service.models.Training.PlanoPagamento;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PlanoPagamentoRepository extends JpaRepository<PlanoPagamento, Long> {

    @Query("SELECT SUM(p.aulasPack) FROM PlanoPagamento p WHERE p.noSocio = :noSocio")
    Integer sumAulasByNoSocio(@Param("noSocio") String noSocio);


    Optional<PlanoPagamento> findFirstByNoSocioOrderByIdDesc(String noSocio);
}
