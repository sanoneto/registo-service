package com.aneto.registo_horas_service.models;

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
@Table(name = "planos",schema = "REGISTOS")
@AllArgsConstructor
@NoArgsConstructor
public class Plano {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "nome_aluno", nullable = false)
    private String nomeAluno;

    @Column(columnDefinition = "TEXT")
    private String objetivo;

    private String especialista;

    @Column(name = "estado_plano")
    @Enumerated(EnumType.STRING)
    private Enum.EstadoPlano estadoPlano;

    @Column(name = "estado_pedido")
    @Enumerated(EnumType.STRING)
    private Enum.EstadoPedido estadoPedido;

    private String link;

    @CreationTimestamp
    @Column(name = "data_create", nullable = false, updatable = false)
    private LocalDate dataCreate;

    @UpdateTimestamp
    @Column(name = "data_update")
    private LocalDate dataUpdate;
    private String recommended;
}