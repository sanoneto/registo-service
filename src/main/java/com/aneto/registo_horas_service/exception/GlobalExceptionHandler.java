package com.aneto.registo_horas_service.exception;

import com.aneto.registo_horas_service.dto.response.ErrorResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.ResourceAccessException;

import java.util.HashMap;
import java.util.Map;

/**
 * Captura centralizada de erros para toda a aplicação.
 * Formata as mensagens de erro para que o Frontend as possa interpretar facilmente.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // Captura erros de lógica de negócio (ex: "Limite do Pack atingido")
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ErrorResponse> handleRuntimeException(RuntimeException e) {
        log.error("Erro de lógica/negócio: {}", e.getMessage());
        ErrorResponse error = new ErrorResponse(
                e.getMessage(),
                HttpStatus.BAD_REQUEST.value()
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    // Captura erros de argumentos inválidos enviados pelo cliente
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgumentException(IllegalArgumentException e) {
        log.error("Argumento inválido: {}", e.getMessage());
        ErrorResponse error = new ErrorResponse(
                e.getMessage(),
                HttpStatus.BAD_REQUEST.value()
        );
        return ResponseEntity.badRequest().body(error);
    }

    // Captura erros de validação (ex: campos @NotNull ou @NotBlank falhando)
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidationExceptions(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach((error) -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            errors.put(fieldName, errorMessage);
        });
        log.error("Erro de validação nos campos: {}", errors);
        return ResponseEntity.badRequest().body(errors);
    }

    // Captura erros de permissão (Spring Security)
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDeniedException(AccessDeniedException e) {
        log.warn("Tentativa de acesso não autorizado: {}", e.getMessage());
        ErrorResponse error = new ErrorResponse(
                "Acesso negado: Não tem permissões para esta ação.",
                HttpStatus.FORBIDDEN.value()
        );
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error);
    }

    // Captura falhas de login/credenciais
    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleBadCredentialsException(BadCredentialsException e) {
        log.warn("Falha de autenticação: Credenciais incorretas.");
        ErrorResponse error = new ErrorResponse(
                "Utilizador ou password incorretos.",
                HttpStatus.UNAUTHORIZED.value()
        );
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error);
    }

    // Captura erros de conectividade (Ex: Vault, base de dados externa)
    @ExceptionHandler(ResourceAccessException.class)
    public ResponseEntity<ErrorResponse> handleResourceAccessException(ResourceAccessException e) {
        log.error("Erro de comunicação externa (SSL/Timeout): {}", e.getMessage());
        ErrorResponse error = new ErrorResponse(
                "O sistema não conseguiu comunicar com os serviços de segurança. Tente novamente.",
                HttpStatus.SERVICE_UNAVAILABLE.value()
        );
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(error);
    }

    // Captura falha total de conexão de rede
    @ExceptionHandler(java.net.ConnectException.class)
    public ResponseEntity<ErrorResponse> handleConnectException(java.net.ConnectException e) {
        log.error("Falha de conexão física/rede: {}", e.getMessage());
        ErrorResponse error = new ErrorResponse(
                "Servidor remoto indisponível. Verifique a sua ligação.",
                HttpStatus.SERVICE_UNAVAILABLE.value()
        );
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(error);
    }

    // Erro genérico (Catch-all para erros 500)
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(Exception e) {
        log.error("ERRO CRÍTICO NÃO MAPEADO: {}", e.getMessage(), e);
        ErrorResponse error = new ErrorResponse(
                "Ocorreu um erro interno inesperado. Contacte o administrador.",
                HttpStatus.INTERNAL_SERVER_ERROR.value()
        );
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }
}