package com.aneto.registo_horas_service.models.Training;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// Entidade
@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "exercises" ,schema = "REGISTOS")
public class Exercises {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String name;

    @Column(nullable = false)
    private String videoUrl;

    private String category;

    // Getters e Setters
}
