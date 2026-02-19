package com.aneto.registo_horas_service.controller;

import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import jakarta.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
public class CustomErrorController implements ErrorController {

    @RequestMapping("/error")
    public ResponseEntity<Map<String, Object>> handleError(HttpServletRequest request) {
        Map<String, Object> body = new LinkedHashMap<>();

        Object status = request.getAttribute("jakarta.servlet.error.status_code");
        String uri = (String) request.getAttribute("jakarta.servlet.error.request_uri");

        if (status != null && Integer.parseInt(status.toString()) == HttpStatus.NOT_FOUND.value()) {
            body.put("error", "Recurso não encontrado");
            body.put("message", "A URL " + uri + " não existe na nossa API.");
            body.put("path", uri);
            return new ResponseEntity<>(body, HttpStatus.NOT_FOUND);
        }

        // Para outros erros (500, etc)
        body.put("error", "Erro interno do servidor");
        body.put("message", "Ocorreu um problema inesperado.");
        return new ResponseEntity<>(body, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}