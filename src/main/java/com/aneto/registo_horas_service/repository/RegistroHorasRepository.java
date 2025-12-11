package com.aneto.registo_horas_service.repository;

import com.aneto.registo_horas_service.dto.response.MonthlySummary;
import com.aneto.registo_horas_service.dto.response.PerfilResponse;
import com.aneto.registo_horas_service.models.RegistosHoras;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RegistroHorasRepository extends JpaRepository<RegistosHoras, Long> {

    @Query("SELECT ROUND(SUM(r.horasTrabalhadas), 1) " +
            "FROM RegistosHoras r " +
            "WHERE r.userName = :userName " +
            "AND (:projectName IS NULL OR :projectName = 'all' OR r.projectName = :projectName)")
    Optional<Double> findSumHorasTrabalhadasByUserNameAndProjectName(
            @Param("userName") String userName,
            @Param("projectName") String projectName
    );
    // 4. Nativa: Injetada do YAML (monthly-summary-by-user)
    @Query(
            value = "${app.queries.monthly-summary-by-user}",
            nativeQuery = true
    )
    List<MonthlySummary> findMonthlySummaryByUsername(@Param("username") String username);

    // Métodos Spring Data JPA (Mantidos)
    List<RegistosHoras> findByUserName(String estagiarioUsername);

    Page<RegistosHoras> findByUserName(String name, Pageable pageable);

    Optional<RegistosHoras> findByPublicId(UUID attr0);
}