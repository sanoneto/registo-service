package com.aneto.registo_horas_service.repository;

import com.aneto.registo_horas_service.models.Absence;
import com.aneto.registo_horas_service.models.AbsenceStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AbsenceRepository extends JpaRepository<Absence, Long> {


    Page<Absence> findAll(Pageable pageable);

    // Filtragem para o Service
    Page<Absence> findByUserName(String userName, Pageable pageable);

    Page<Absence> findByStatus(AbsenceStatus status, Pageable pageable);

    Page<Absence> findByUserNameAndStatus(String userName, AbsenceStatus status, Pageable pageable);

    // Para ações (DELETE e PATCH Status)
    Optional<Absence> findByPublicId(String publicId);

    void deleteByPublicId(String publicId);
}


