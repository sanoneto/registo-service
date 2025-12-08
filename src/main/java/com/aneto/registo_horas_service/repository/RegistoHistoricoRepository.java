package com.aneto.registo_horas_service.repository;

import com.aneto.registo_horas_service.models.RegistoHistorico;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RegistoHistoricoRepository extends JpaRepository<RegistoHistorico, Long> {
    List<RegistoHistorico> findByRegistoPublicIdOrderByDataAlteracaoDesc(String registoPublicId);
    List<RegistoHistorico> findByUtilizadorAlteracaoOrderByDataAlteracaoDesc(String utilizadorAlteracao);
}
