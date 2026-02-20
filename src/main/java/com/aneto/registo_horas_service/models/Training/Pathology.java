package com.aneto.registo_horas_service.models.Training;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Data
@Table(name = "pathologies", schema = "REGISTOS")
@AllArgsConstructor
@NoArgsConstructor
public class Pathology {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 50)
    private String category;
}