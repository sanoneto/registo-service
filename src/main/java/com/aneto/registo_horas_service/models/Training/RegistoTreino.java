package com.aneto.registo_horas_service.models.Training;

import jakarta.persistence.*;
import lombok.*;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.LocalDate;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "registo_treino", schema = "REGISTOS")
@JsonIgnoreProperties({"planoPagamento"})
public class RegistoTreino {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String noSocio;
    private String nomeSocio;
    private LocalDate data;
    private String hora;

    // ---> NOVO CAMPO PARA O NOME DO PACK <---
    @Column(name = "pack_name")
    private String packName;

    @Column(name = "valor", nullable = false)
    private Double valor;

    @Column(name = "pack_valor")
    private Double packValor;

    @Column(name = "aulas_pack")
    private Integer aulasPack;

    private int aulasFeitas;
    private int saldo;

    @Column(columnDefinition = "TEXT")
    private String assinatura;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "plano_pagamento_id")
    private PlanoPagamento planoPagamento;
}