package com.aneto.registo_horas_service.models.Enum;

import com.fasterxml.jackson.annotation.JsonValue;

public enum EstadoPedido {
    PENDENTE("PENDENTE"),
    FINALIZADO("FINALIZADO"),
    FECHADO("FECHADO"),
    A_PROCESSAR("A PROCESSAR"),
    ERRO("ERRO");

    private final String descricao;

    EstadoPedido(String descricao) {
        this.descricao = descricao;
    }

    @JsonValue
    public String getDescricao() {
        return descricao;
    }

    /** Aceita tanto a descrição ("A PROCESSAR") como o nome da constante ("A_PROCESSAR"). */
    public static EstadoPedido fromDescricao(String texto) {
        for (EstadoPedido estado : values()) {
            if (estado.descricao.equalsIgnoreCase(texto)) {
                return estado;
            }
        }
        return valueOf(texto.replace(" ", "_").toUpperCase());
    }
}