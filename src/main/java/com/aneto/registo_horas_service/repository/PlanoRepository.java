package com.aneto.registo_horas_service.repository;

import com.aneto.registo_horas_service.models.EstadoPedido;
import com.aneto.registo_horas_service.models.EstadoPlano;
import com.aneto.registo_horas_service.models.Plano;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;
@Repository
public interface PlanoRepository extends JpaRepository<Plano, UUID> {

    // Esta query é mais segura para lidar com valores nulos ou vazios
    @Query("SELECT p FROM Plano p WHERE (:nome IS NULL OR :nome = '' OR LOWER(p.nomeAluno) LIKE LOWER(CONCAT('%', :nome, '%')))")
    Page<Plano> findByNomeCustom(@Param("nome") String nome, Pageable pageable);

    Optional<Plano> findByNomeAlunoContainingAndEstadoPlanoAndEstadoPedido(
            String nomeAluno,
            EstadoPlano estadoPlano,
            EstadoPedido estadoPedido
    );
}

