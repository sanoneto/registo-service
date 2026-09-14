package com.aneto.registo_horas_service.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlanoResponseDTO implements Serializable {
    private String id;              // String pura
    private String nomeAluno;
    private String objetivo;
    private String especialista;
    private String estadoPlano;
    private String estadoPedido;
    private String link;
    private String dataCreate;      // String pura
    private String dataUpdate;      // String pura
    private String recommended;

    // >>> NOVO: periodização de treino
    private int semanaCiclo;
    private boolean deload;

    private boolean contaAssociada;
    private String alunoTempId;
}