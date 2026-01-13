package com.aneto.registo_horas_service.models;

import com.fasterxml.jackson.annotation.JsonValue;

public enum EstadoPedido {
    PENDENTE("PENDENTE"),
    FINALIZADO("FINALIZADO"),
    FECHADO("FECHADO"),
    A_PROCESSAR("A PROCESSAR");

    private final String descricao;

    EstadoPedido(String descricao) {
        this.descricao = descricao;
    }

    @JsonValue
    public String getDescricao() {
        return descricao;
    }

    // Método para converter String (com espaço) em Enum
    public static EstadoPedido fromDescricao(String texto) {
        for (EstadoPedido estado : EstadoPedido.values()) {
            if (estado.descricao.equalsIgnoreCase(texto)) {
                return estado;
            }
        }
        // Fallback para o valueOf padrão caso não encontre pela descrição
        return EstadoPedido.valueOf(texto.replace(" ", "_").toUpperCase());
    }
}