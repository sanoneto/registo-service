package com.aneto.registo_horas_service.models;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "exercise_history", schema = "REGISTOS")
public class ExerciseHistoryEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    @Builder.Default
    private String publicId = UUID.randomUUID().toString();

    private String username;
    private String planId;
    private String exerciseName;
    private String muscleGroup;
    private String weight; // O Hibernate salvou o "25" aqui, mantenha este nome.

    @Builder.Default
    private LocalDateTime registeredAt = LocalDateTime.now();
    private String clientDate;

    // Remova o 'private String name' e 'private String cargaAtual'
    // se eles estiverem sobrando, para evitar colunas vazias no banco.
}