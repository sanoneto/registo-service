package com.aneto.registo_horas_service.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.util.UUID;

@Data
@Entity
@Table(name = "planos", schema = "REGISTOS")
@AllArgsConstructor
@NoArgsConstructor
// ESTA LINHA É CRUCIAL: Impede que o Jackson tente ler proxies do Hibernate
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class Plano {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private String nomeAluno;

    @Column(columnDefinition = "TEXT")
    private String objetivo;

    private String especialista;

    @Enumerated(EnumType.STRING)
    private Enum.EstadoPlano estadoPlano;

    @Enumerated(EnumType.STRING)
    private Enum.EstadoPedido estadoPedido;

    private String link;

    @CreationTimestamp
    private LocalDate dataCreate;

    @UpdateTimestamp
    private LocalDate dataUpdate;

    private String recommended;

    // >>> NOVO: periodização de treino (deload automático a cada 4ª semana)
    // Semana do ciclo de treino em que este plano foi gerado (1, 2, 3, 4...).
    @Column(name = "semana_ciclo")
    private int semanaCiclo = 1;

    // Indica se este plano específico foi gerado como semana de deload
    // (volume/intensidade reduzidos para recuperação).
    @Column(name = "deload")
    private boolean deload;
}