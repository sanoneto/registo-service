package com.aneto.registo_horas_service.repository;

import com.aneto.registo_horas_service.models.Training.RegistoTreino;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RegistroTreinoRepository extends JpaRepository<RegistoTreino, Long> {
}