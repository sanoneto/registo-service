package com.aneto.registo_horas_service.models;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.UUID;
@Data
@Builder
@Entity
@Table(name = "absences")
@NoArgsConstructor
@AllArgsConstructor
public class Absence {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String publicId; // ID único para uso no frontend (UUID)

    @Column(nullable = false)
    private String userName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Enum.AbsenceType type;

    @Column(nullable = false)
    private LocalDate startDate;

    @Column(nullable = false)
    private LocalDate endDate;

    @Column(nullable = false, length = 255)
    private String reason;

    @Column(nullable = false)
    private LocalDate registrationDate; // Data do pedido

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Enum.AbsenceStatus status;

    // Construtor, Getters e Setters

    @PrePersist
    protected void onCreate() {
        this.publicId = UUID.randomUUID().toString();
        this.registrationDate = LocalDate.now();
        this.status = Enum.AbsenceStatus.PENDENTE; // Todos os pedidos começam como Pendentes
    }
}

