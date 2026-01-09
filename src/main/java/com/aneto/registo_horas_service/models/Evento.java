package com.aneto.registo_horas_service.models;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

@Entity
@Table(name = "eventos" , schema = "REGISTOS")
@Data // Gera getters, setters, equals e hashcode via Lombok
public class Evento {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(nullable = false)
    private String username;

    @Column(nullable = false)
    private String title;

    private String project;

    @Column(name = "reference_date", nullable = false)
    private LocalDate referenceDate;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    private LocalTime endTime;

    @Column(columnDefinition = "TEXT")
    private String notes;

    private boolean sendAlert;
}