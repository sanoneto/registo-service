package com.aneto.registo_horas_service.controller;

import com.aneto.registo_horas_service.dto.request.RegisterRequest;
import com.aneto.registo_horas_service.dto.response.MonthlySummary;
import com.aneto.registo_horas_service.dto.response.PageResponse;
import com.aneto.registo_horas_service.dto.response.PerfilResponse;
import com.aneto.registo_horas_service.dto.response.RegisterResponse;
import com.aneto.registo_horas_service.service.RegistosHorasService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/registos")
@RequiredArgsConstructor
public class RegistroHorasController {

    private final RegistosHorasService registroHorasService;

    // Header injetado pelo API Gateway para identificar o utilizador de forma segura
    private static final String X_USER_ID = "X-User-Id";

    @Operation(summary = "Submete um novo registro de horas")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Registro criado com sucesso"),
            @ApiResponse(responseCode = "401", description = "Não autorizado")
    })
    @PreAuthorize("hasAnyRole('ADMIN', 'ESPECIALISTA') or (hasRole('ESTAGIARIO') and #username == authentication.name)")
    @PostMapping
    public ResponseEntity<RegisterResponse> submeterHoras(
            @Valid @RequestBody RegisterRequest request,
            @RequestHeader(X_USER_ID) String username) {

        RegisterResponse response = registroHorasService.submeterHoras(request, username);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @Operation(summary = "Busca registros de horas do usuário autenticado")
    @PreAuthorize("hasAnyRole('ADMIN', 'ESPECIALISTA', 'ESTAGIARIO')")
    @GetMapping
    public ResponseEntity<List<RegisterResponse>> buscarMeusRegistros(
            @RequestHeader(X_USER_ID) String username) {
        List<RegisterResponse> registros = registroHorasService.buscarRegistrosPorUsuario(username);
        return ResponseEntity.ok(registros);
    }

    @Operation(summary = "Busca um registro específico por UUID")
    @GetMapping("/{uuid}")
    @PreAuthorize("hasAnyRole('ADMIN', 'ESPECIALISTA') or (hasRole('ESTAGIARIO'))")
    public ResponseEntity<RegisterResponse> getRegisterById(@PathVariable UUID uuid) {
        return registroHorasService.findAllRegisteredHours().stream()
                .filter(r -> r.publicId().equals(uuid))
                .findFirst()
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @Operation(summary = "Busca todos os registros de horas (Apenas Admin)")
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/all")
    public ResponseEntity<List<RegisterResponse>> getAllRegisterHoras() {
        List<RegisterResponse> registros = registroHorasService.findAllRegisteredHours();
        return ResponseEntity.ok(registros);
    }

    @Operation(summary = "Retorna o sumário mensal de todos os utilizadores")
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("monthly-summary/all")
    public ResponseEntity<List<MonthlySummary>> findMonthlySummaryAll() {
        List<MonthlySummary> registros = registroHorasService.findMonthlySummary("all");
        return ResponseEntity.ok(registros);
    }

    @Operation(summary = "Atualiza um registro existente")
    @PreAuthorize("hasAnyRole('ADMIN', 'ESPECIALISTA') or (hasRole('ESTAGIARIO') and #username == authentication.name)")
    @PutMapping("/{publicId}")
    public ResponseEntity<RegisterResponse> updateRegister(
            @PathVariable UUID publicId,
            @Valid @RequestBody RegisterRequest request,
            @RequestHeader(X_USER_ID) String username) throws Exception {
        RegisterResponse response = registroHorasService.atualizarRegistro(publicId, request, username);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Deleta um registro (Apenas Admin)")
    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{publicId}")
    public ResponseEntity<Void> deleteRegistry(@PathVariable UUID publicId) {
        registroHorasService.deleteRegistry(publicId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Retorna o total de horas acumulado do usuário")
    @GetMapping("/total_user")
    @PreAuthorize("hasAnyRole('ADMIN', 'ESPECIALISTA', 'ESTAGIARIO', 'USER')")
    public ResponseEntity<Map<String, Object>> getTotalHoras(
            @RequestHeader(X_USER_ID) String username) {
        double total = registroHorasService.getTotalHorasPorUsuarioProjrct(username, null);
        Map<String, Object> body = Map.of(
                "user", username,
                "totalHoursDecimal", total
        );
        return ResponseEntity.ok(body);
    }

    @Operation(summary = "Retorna o total de horas por usuário e projeto")
    @GetMapping("/total-user-project")
    @PreAuthorize("hasAnyRole('ADMIN', 'ESPECIALISTA', 'ESTAGIARIO', 'USER')")
    public ResponseEntity<Double> getTotalHorasPorUser(
            @RequestParam(defaultValue = "all") String username,
            @RequestParam(defaultValue = "all") String projectName) {
        double total = registroHorasService.getTotalHorasPorUsuarioProjrct(username, projectName);
        return ResponseEntity.ok(total);
    }

    @Operation(summary = "Busca o sumário mensal do usuário logado (usado pelo Dashboard)")
    @GetMapping("/monthly-summary")
    @PreAuthorize("hasAnyRole('ADMIN', 'ESPECIALISTA', 'ESTAGIARIO', 'USER')")
    public ResponseEntity<List<MonthlySummary>> findMonthlySummary(
            @RequestHeader(X_USER_ID) String username) {
        List<MonthlySummary> total = registroHorasService.findMonthlySummary(username);
        return ResponseEntity.ok(total);
    }

    @Operation(summary = "Busca dados de perfil e horas requeridas")
    @GetMapping("/perfil")
    @PreAuthorize("hasAnyRole('ADMIN', 'ESPECIALISTA', 'ESTAGIARIO', 'USER')")
    public ResponseEntity<List<PerfilResponse>> getPerfil(
            @RequestHeader(X_USER_ID) String username) {
        return ResponseEntity.ok(registroHorasService.findTotalHoursAndRequiredHoursByUserName(username));
    }

    @Operation(summary = "Listar registos por usuário com paginação")
    @GetMapping("/Pageable/list/{name}")
    @PreAuthorize("hasRole('ADMIN') or (hasAnyRole('ESPECIALISTA', 'ESTAGIARIO', 'USER') and #name == authentication.name)")
    public ResponseEntity<PageResponse<RegisterResponse>> getAllRegisterHorasUserPaginated(
            @PathVariable String name,
            @RequestParam(defaultValue = "all") String projectName,
            @RequestParam(value = "page", required = false) String pageStr,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "horaEntrada") String sortBy,
            @RequestParam(defaultValue = "DESC") String direction
    ) {
        int page = parsePage(pageStr);
        Sort.Direction sortDirection = direction.equalsIgnoreCase("ASC") ? Sort.Direction.ASC : Sort.Direction.DESC;
        Pageable pageable = PageRequest.of(page, size, Sort.by(sortDirection, sortBy));

        PageResponse<RegisterResponse> response = registroHorasService.findAllRegisteredHoursUserProjectName(name, projectName, pageable);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Listar todos os registos paginados (Apenas Admin)")
    @GetMapping("/Pageable/list")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<PageResponse<RegisterResponse>> getAllRegisterHorasPaginated(
            @RequestParam(defaultValue = "all") String projectName,
            @RequestParam(value = "page", required = false) String pageStr,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "horaEntrada") String sortBy,
            @RequestParam(defaultValue = "DESC") String direction
    ) {
        int page = parsePage(pageStr);
        Sort.Direction sortDirection = direction.equalsIgnoreCase("ASC") ? Sort.Direction.ASC : Sort.Direction.DESC;
        Pageable pageable = PageRequest.of(page, size, Sort.by(sortDirection, sortBy));

        PageResponse<RegisterResponse> response = registroHorasService.findAllRegisteredHoursPageProjectName(projectName, pageable);
        return ResponseEntity.ok(response);
    }

    // Helper method para evitar repetição de lógica de parsing
    private int parsePage(String pageStr) {
        if (pageStr != null && !pageStr.equalsIgnoreCase("NaN")) {
            try {
                return Integer.parseInt(pageStr);
            } catch (NumberFormatException e) {
                return 0;
            }
        }
        return 0;
    }
}