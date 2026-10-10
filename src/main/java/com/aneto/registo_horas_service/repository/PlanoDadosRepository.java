package com.aneto.registo_horas_service.repository;

import com.aneto.registo_horas_service.models.PlanoDados;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlanoDadosRepository extends JpaRepository<PlanoDados, String> {
}