package com.aneto.registo_horas_service.models.Training;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalTime;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "registo_treino",schema = "REGISTOS")
public class RegistoTreino {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String nomeSocio;

    // Atualizado para garantir que guarda o número corretamente
    @Column(name = "no_socio")
    private String noSocio;

    private LocalTime hora;
    private LocalDate data;
    private String programa;
    private String servicoId; // Corresponde ao nomePack enviado pelo front

    // Novos campos adicionados para a lógica do Front-end
    private int aulasPack;
    private int aulasFeitas;
    private int saldo;

    private double valor;

    // Assinatura em formato base64
    @Column(columnDefinition = "TEXT")
    private String assinatura;
}