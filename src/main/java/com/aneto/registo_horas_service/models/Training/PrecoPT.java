package com.aneto.registo_horas_service.models.Training;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "PrecoPT" ,schema = "REGISTOS")
public class PrecoPT {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY) // Adiciona esta linha
    private Long id;
    private String nomePack;
    private String descricao;
    private double valor;
    private String tipo;
}