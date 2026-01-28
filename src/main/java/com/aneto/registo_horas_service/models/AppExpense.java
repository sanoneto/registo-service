package com.aneto.registo_horas_service.models;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "app_expenses")
@Data // Se não usares Lombok, cria os Getters e Setters
public class AppExpense {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String service;

    private Double amount;

    private LocalDateTime date;

    @Column(name = "created_by")
    private String createdBy; // Para saberes qual admin registou
}