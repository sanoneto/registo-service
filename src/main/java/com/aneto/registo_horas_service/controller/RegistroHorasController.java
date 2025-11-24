// language: java
package com.aneto.registo_horas_service.controller;

import com.aneto.registo_horas_service.dto.request.RegisterRequest;
import com.aneto.registo_horas_service.dto.response.RegisterResponse;
import com.aneto.registo_horas_service.service.RegistosHorasService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/registos")
@RequiredArgsConstructor
public class RegistroHorasController {

    private final RegistosHorasService registroHorasService;

    // Header injetado pelo Gateway
    private static final String X_USER_ID = "X-User-Id";

    @Operation(summary = "Submete um novo registro de horas")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Registro criado com sucesso"),
            @ApiResponse(responseCode = "401", description = "Não autorizado")
    })

    @PreAuthorize("hasRole('ADMIN') or (hasRole('ESTAGIARIO') and #username == authentication.name)")
    @PostMapping
    public ResponseEntity<RegisterResponse> submeterHoras(
            @Valid @RequestBody RegisterRequest request,
            // Obtém o username do header injetado pelo Gateway
            @RequestHeader(X_USER_ID) String username) {

        RegisterResponse response = registroHorasService.submeterHoras(request, username);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @Operation(summary = "Busca registros de horas do usuário autenticado")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Lista de registros do usuário retornada"),
            @ApiResponse(responseCode = "401", description = "Não autorizado")
    })

    @PreAuthorize("hasRole('ADMIN') or (hasRole('ESTAGIARIO') and #username == authentication.name)")
    @GetMapping
    public ResponseEntity<List<RegisterResponse>> buscarMeusRegistros(
            @RequestHeader(X_USER_ID) String username) {
        // Retorna apenas os registros do usuário
        List<RegisterResponse> registros = registroHorasService.buscarRegistrosPorUsuario(username);
        return ResponseEntity.ok(registros);
    }

    @Operation(summary = "Busca todos os registros de horas")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Lista de registros retornada"),
            @ApiResponse(responseCode = "403", description = "Acesso negado")
    })

    @PreAuthorize("hasRole('ADMIN')") // Apenas ADMIN pode ver todos
    @GetMapping("/todos")
    public ResponseEntity<List<RegisterResponse>> buscarTodosRegistros() {
        List<RegisterResponse> registros = registroHorasService.buscarTodosRegistros();
        return ResponseEntity.ok(registros);
    }

    @Operation(summary = "Atualiza um registro existente")
    @PreAuthorize("hasRole('ESTAGIARIO')")
    // Apenas estagiários podem atualizar (o service deve validar se é o seu próprio)
    @PutMapping("/{publicId}")
    public ResponseEntity<RegisterResponse> atualizarRegistro(
            @PathVariable UUID publicId,
            @Valid @RequestBody RegisterRequest request) {
        // Implementar validação de propriedade dentro do Service para garantir que o usuário só edita os seus.
        RegisterResponse response = registroHorasService.atualizarRegistro(publicId, request);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Deleta um registro")
    @PreAuthorize("hasRole('ADMIN')") // Apenas ADMIN pode deletar (ou estagiário no seu service)
    @DeleteMapping("/{publicId}")
    public ResponseEntity<Void> deletarRegistro(@PathVariable UUID publicId) {
        registroHorasService.deletarRegistro(publicId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Retorna o total de horas de um usuário")
    @GetMapping("/total_user")
    @PreAuthorize("hasRole('ADMIN') or (hasRole('ESTAGIARIO') and #username == authentication.name)")
    public ResponseEntity<Map<String, Object>> getTotalHoras(
            @RequestHeader(X_USER_ID) String username,
            Authentication authentication) {

        // Nota: authentication.name é o username setado pelo GatewayAuthFilter do serviço (8082)
        // Isso é uma medida de segurança extra.

        double total = registroHorasService.getTotalHorasPorUsuario(username);

        Map<String, Object> body = Map.of(
                "user", username,
                "totalHoursDecimal", total
        );
        return ResponseEntity.ok(body);
    }
}