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

    @Column(name = "semana_ciclo")
    private int semanaCiclo = 1;

    @Column(name = "deload")
    private boolean deload;

    // >>> NOVO: distingue plano de conta real vs aluno sem registo
    @Column(name = "conta_associada")
    private boolean contaAssociada = true;

    // >>> NOVO: identificador estável do aluno fictício (UUID em texto).
    // Null quando contaAssociada = true (aí usa-se nomeAluno/username normalmente).
    @Column(name = "aluno_temp_id")
    private String alunoTempId;
}