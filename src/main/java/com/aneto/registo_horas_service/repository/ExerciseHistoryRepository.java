package com.aneto.registo_horas_service.repository;

import com.aneto.registo_horas_service.models.ExerciseHistoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ExerciseHistoryRepository extends JpaRepository<ExerciseHistoryEntity, Long> {
    // No futuro, podes buscar a evolução de um exercício específico:
    List<ExerciseHistoryEntity> findByUsernameAndExerciseNameOrderByRegisteredAtDesc(String username, String exerciseName);
}