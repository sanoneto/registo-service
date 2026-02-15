package com.aneto.registo_horas_service.models.Training;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.util.List;

@Entity
@Table(name = "plano_pagamento", schema = "REGISTOS")
@AllArgsConstructor
@NoArgsConstructor
@Data
@Builder
public class PlanoPagamento {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String nomeSocio;
    private String noSocio; // Este campo ligará ao RegistoTreino
    private String packName;
    private LocalDate dataPack;
    private int aulasPack;
    private double valor;
    private String tipoPack;
    @Column(columnDefinition = "TEXT")
    private String assinatura;

    // Relacionamento: Um Plano tem vários Registos de Treino
    @OneToMany(mappedBy = "planoPagamento", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<RegistoTreino> registosTreino;
}