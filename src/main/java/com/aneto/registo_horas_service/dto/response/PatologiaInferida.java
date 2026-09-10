package com.aneto.registo_horas_service.dto.response;

import java.util.Set;

public record PatologiaInferida(String categorias, Set<String> termosEncontrados) {
}
