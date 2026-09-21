package com.aneto.registo_horas_service.models.Training;

import jakarta.persistence.*;
import lombok.*;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties; // 1. IMPORTA ISTO

import java.time.LocalDate;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "registo_treino", schema = "REGISTOS")
// 2. ADICIONA ESTA ANOTAÇÃO AQUI:
@JsonIgnoreProperties({"planoPagamento"})
public class RegistoTreino {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String noSocio;
    private String nomeSocio;
    private LocalDate data;
    private String hora;
    private String packValor;
    private int aulasFeitas;
    private int saldo;

    @Column(columnDefinition = "TEXT")
    private String assinatura;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "plano_pagamento_id")
    private PlanoPagamento planoPagamento;
}