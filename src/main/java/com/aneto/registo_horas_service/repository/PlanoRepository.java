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
    Page<Plano> findByNomeAlunoContainingIgnoreCase(String nomeAluno, Pageable pageable);

    // Para o Especialista (Seu nome OR nulo)
    @Query("SELECT p FROM Plano p WHERE (p.especialista = :nome OR p.especialista IS NULL OR p.especialista = '' OR p.especialista='sem Especialista')")
    Page<Plano> findForEspecialista(@Param("nome") String nome, Pageable pageable);

    // Para o Estagiário (Apenas seu nome)
    @Query("SELECT p FROM Plano p WHERE p.nomeAluno = :nome")
    Page<Plano> findForEstagiario(@Param("nome") String nome, Pageable pageable);
}

