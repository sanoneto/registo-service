package com.aneto.registo_horas_service.repository;

import com.aneto.registo_horas_service.models.Training.RegistoTreino;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RegistroTreinoRepository extends JpaRepository<RegistoTreino, Long> {


    // --- MÉTODO ADICIONADO PARA BUSCA PAGINADA ---
    // Busca por nome do sócio ou número do sócio, ignorando maiúsculas/minúsculas
    Page<RegistoTreino> findByNomeSocioContainingIgnoreCaseOrNoSocioContaining(
            String nomeSocio,
            String noSocio,
            Pageable pageable
    );

}