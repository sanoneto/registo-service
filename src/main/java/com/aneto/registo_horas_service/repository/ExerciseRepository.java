package com.aneto.registo_horas_service.repository;

import com.aneto.registo_horas_service.models.Training.Exercises;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ExerciseRepository extends JpaRepository<Exercises, Long> {
    Optional<Exercises> findByNameIgnoreCase(String name);
}