package com.aneto.registo_horas_service.repository;


import com.aneto.registo_horas_service.models.Training.PrecoPT;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PrecoPTRepository extends JpaRepository<PrecoPT, Long> {
    // JpaRepository já traz métodos como save, findById, findAll, deleteById
}