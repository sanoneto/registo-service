package com.aneto.registo_horas_service.repository;

import com.aneto.registo_horas_service.models.Training.RegistoTreino;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RegistroTreinoRepository extends JpaRepository<RegistoTreino, Long> {

    long countByPlanoPagamentoId(Long planoPagamentoId);

    List<RegistoTreino> findByPlanoPagamentoIdOrderByIdAsc(Long planoPagamentoId);

    Page<RegistoTreino> findByNoSocioContainingIgnoreCaseOrPlanoPagamentoNomeSocioContainingIgnoreCase(
            String noSocio, String nomeSocio, Pageable pageable);

    // Fallback direto na tabela auth.tb_users caso o emailSocio não tenha sido preenchido no plano
    @Query(value = "SELECT u.email FROM auth.tb_users u WHERE u.numero_socio = :noSocio OR u.username = :noSocio LIMIT 1", nativeQuery = true)
    Optional<String> findEmailByNoSocioInAuth(@Param("noSocio") String noSocio);
}