package com.aneto.registo_horas_service.repository;

import com.aneto.registo_horas_service.models.RegistoHistorico;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
@Repository
public interface RegistoHistoricoRepository extends JpaRepository<RegistoHistorico, Long> {
    List<RegistoHistorico> findByRegistoPublicIdOrderByDataAlteracaoDesc(String registoPublicId);
    List<RegistoHistorico> findByUtilizadorAlteracaoOrderByDataAlteracaoDesc(String utilizadorAlteracao);
}
