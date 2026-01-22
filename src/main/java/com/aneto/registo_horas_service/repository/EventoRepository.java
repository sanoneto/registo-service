package com.aneto.registo_horas_service.repository;

import com.aneto.registo_horas_service.models.Evento;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface EventoRepository extends JpaRepository<Evento, UUID> {
    Optional<Evento> findById(UUID eventoId);
    // O método que estava faltando
    boolean existsByTitle(String title);

    // Melhor alternativa: evitar duplicatas pelo ID único do Google
    boolean existsByGoogleEventId(String googleEventId);

    boolean existsByGoogleEventIdAndUsername(String id, String userId);
    @Query("SELECT e FROM Evento e WHERE e.sendAlert = true AND e.alertConfirmed = false AND e.referenceDate <= :hoje")
    List<Evento> findPendentesParaNotificar(@Param("hoje") LocalDate hoje);
}
