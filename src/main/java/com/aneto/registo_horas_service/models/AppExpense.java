package com.aneto.registo_horas_service.models;

import jakarta.persistence.*;
import lombok.Data;
import org.springframework.data.annotation.CreatedDate;

import java.time.LocalDate;

@Entity
@Table(name = "app_expenses", schema = "REGISTOS")
@Data // Se não usares Lombok, cria os Getters e Setters
public class AppExpense {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String service;

    private Double amount;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDate date;

    private String expiryDate;

    private String createdBy; // Para saberes qual admin registou
}