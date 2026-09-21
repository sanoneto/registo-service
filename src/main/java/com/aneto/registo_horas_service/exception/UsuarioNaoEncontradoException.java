package com.aneto.registo_horas_service.exception; // Use o seu pacote real aqui

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Exceção personalizada para quando um usuário não é encontrado no sistema.
 * O @ResponseStatus garante que, se não houver um Handler, o Spring retorne 404 por padrão.
 */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class UsuarioNaoEncontradoException extends RuntimeException {

    public UsuarioNaoEncontradoException(String mensagem) {
        super(mensagem);
    }

    public UsuarioNaoEncontradoException(String username, String detalhe) {
        super(String.format("Usuário [%s] não encontrado: %s", username, detalhe));
    }
}