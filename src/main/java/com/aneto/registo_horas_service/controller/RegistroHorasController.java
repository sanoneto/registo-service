// language: java
package com.aneto.registo_horas_service.controller;

import com.aneto.registo_horas_service.dto.request.RegisterRequest;
import com.aneto.registo_horas_service.dto.response.MonthlySummary;
import com.aneto.registo_horas_service.dto.response.PageResponse;
import com.aneto.registo_horas_service.dto.response.PerfilResponse;
import com.aneto.registo_horas_service.dto.response.RegisterResponse;
import com.aneto.registo_horas_service.service.RegistosHorasService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
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

    @GetMapping("/{uuid}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('ESTAGIARIO')")
    public ResponseEntity<RegisterResponse> getRegisterById(@PathVariable UUID uuid) {
        // Usa o serviço existente que retorna todos os registros e filtra pelo publicId.
        // Evita a necessidade de adicionar novos métodos ao service/repository.
        return registroHorasService.findAllRegisteredHours().stream()
                .filter(r -> r.publicId().equals(uuid))
                .findFirst()
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @Operation(summary = "Busca todos os registros de horas")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Lista de registros retornada"),
            @ApiResponse(responseCode = "403", description = "Acesso negado")
    })

    @PreAuthorize("hasRole('ADMIN')") // Apenas ADMIN pode ver todos
    @GetMapping("/all")
    public ResponseEntity<List<RegisterResponse>> getAllRegisterHoras() {
        List<RegisterResponse> registros = registroHorasService.findAllRegisteredHours();
        return ResponseEntity.ok(registros);
    }

    @PreAuthorize("hasRole('ADMIN')") // Apenas ADMIN pode ver todos
    @GetMapping("monthly-summary/all")
    public ResponseEntity<List<MonthlySummary>> findMonthlySummary() {
        List<MonthlySummary> registros = registroHorasService.findMonthlySummary();
        return ResponseEntity.ok(registros);
    }

    @Operation(summary = "Atualiza um registro existente")
    @PreAuthorize("hasRole('ADMIN') or (hasRole('ESTAGIARIO') and #username == authentication.name)")
    @PutMapping("/{publicId}")
    public ResponseEntity<RegisterResponse> updateRegister(
            @PathVariable UUID publicId,
            @Valid @RequestBody RegisterRequest request,
            @RequestHeader(X_USER_ID) String username) throws Exception {
        // Implementar validação de propriedade dentro do Service para garantir que o usuário só edita os seus.
        RegisterResponse response = registroHorasService.atualizarRegistro(publicId, request, username);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Deleta um registro")
    @PreAuthorize("hasRole('ADMIN')") // Apenas ADMIN pode deletar (ou estagiário no seu service)
    @DeleteMapping("/{publicId}")
    public ResponseEntity<Void> deleteRegistry(@PathVariable UUID publicId) {
        registroHorasService.deleteRegistry(publicId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Retorna o total de horas de um usuário")
    @GetMapping("/total_user")
    @PreAuthorize("hasRole('ADMIN') or (hasRole('ESTAGIARIO') and #username == authentication.name)")
    public ResponseEntity<Map<String, Object>> getTotalHoras(
            @RequestHeader(X_USER_ID) String username,
            Authentication authentication) {
        double total = registroHorasService.getTotalHorasPorUsuario(username);

        Map<String, Object> body = Map.of(
                "user", username,
                "totalHoursDecimal", total
        );
        return ResponseEntity.ok(body);
    }

    @Operation(summary = "Retorna o total de horas de um usuário")
    @GetMapping("/total-user-project/{username}{projectName}")
    @PreAuthorize("hasRole('ADMIN') or (hasRole('ESTAGIARIO') and #username == authentication.name)")
    public ResponseEntity<Double> getTotalHorasPorUser(
            @RequestParam String username,
            @RequestParam String projectName,
            Authentication authentication) {
        double total = registroHorasService.getTotalHorasPorUsuarioProjrct(username, projectName);
        return ResponseEntity.ok(total);
    }

    @Operation(summary = "Retorna o total de horas de um usuário")
    @GetMapping("/monthly-summary")
    @PreAuthorize("hasRole('ADMIN') or (hasRole('ESTAGIARIO') and #username == authentication.name)")
    public ResponseEntity<List<MonthlySummary>> findMonthlySummary(
            @RequestHeader(X_USER_ID) String username,
            Authentication authentication) {
        List<MonthlySummary> total = registroHorasService.findMonthlySummary(username);
        return ResponseEntity.ok(total);
    }

    @Operation(summary = "Retorna o total de horas de um usuário")
    @GetMapping("/perfil")
    @PreAuthorize("hasRole('ADMIN') or (hasRole('ESTAGIARIO') and #username == authentication.name)")
    public ResponseEntity<List<PerfilResponse>> getPerfil(
            @RequestHeader(X_USER_ID) String username,
            Authentication authentication) {
        return ResponseEntity.ok(registroHorasService.findTotalHoursAndRequiredHoursByUserName(username));
    }

    @Operation(
            summary = "Listar registos por usuário com paginação",
            description = "Retorna os registros paginados de um usuário específico"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Página retornada com sucesso"),
            @ApiResponse(responseCode = "403", description = "Acesso negado"),
            @ApiResponse(responseCode = "404", description = "Nenhum registro encontrado")
    })
    @GetMapping("/Pageable/list/{name}")
    @PreAuthorize("hasRole('ADMIN') or (hasRole('ESTAGIARIO') and #name == authentication.name)")
    public ResponseEntity<PageResponse<RegisterResponse>> getAllRegisterHorasUserPaginated(
            @PathVariable String name,

            @Parameter(description = "Número da página (0-based)", example = "0")
            @RequestParam(value = "page", required = false) String pageStr,

            @Parameter(description = "Tamanho da página", example = "10")
            @RequestParam(defaultValue = "10") int size,

            @Parameter(description = "Campo para ordenação", example = "dataInicio")
            @RequestParam(defaultValue = "horaEntrada") String sortBy,

            @Parameter(description = "Direção da ordenação (ASC ou DESC)", example = "DESC")
            @RequestParam(defaultValue = "DESC") String direction
    ) {
        // 1. Defina um valor padrão
        int page = 0;

        // 2. Tente fazer o parsing da string
        if (pageStr != null && !pageStr.equalsIgnoreCase("NaN")) {
            try {
                page = Integer.parseInt(pageStr);
            } catch (NumberFormatException e) {
                // Opcional: Lançar uma exceção de BadRequest
                // throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "O parâmetro 'page' deve ser um número inteiro.");
                System.out.println("Aviso: Valor inválido para página ('" + pageStr + "'). Usando o valor padrão: 0.");
                page = 0; // Reseta para o valor padrão
            }
        }
        Sort.Direction sortDirection = direction.equalsIgnoreCase("ASC")
                ? Sort.Direction.ASC
                : Sort.Direction.DESC;

        Pageable pageable = PageRequest.of(page, size, Sort.by(sortDirection, sortBy));
        PageResponse<RegisterResponse> response = registroHorasService.findAllRegisteredHoursUser(name, pageable);

        return ResponseEntity.ok(response);
    }

    @GetMapping("/Pageable/list")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<PageResponse<RegisterResponse>> getAllRegisterHorasPaginated(
            @Parameter(description = "Número da página (0-based)", example = "0")
            @RequestParam(value = "page", required = false) String pageStr, // <-- MUDANÇA PARA String

            @Parameter(description = "Tamanho da página", example = "10")
            @RequestParam(defaultValue = "10") int size,

            @Parameter(description = "Campo para ordenação", example = "dataInicio")
            @RequestParam(defaultValue = "horaEntrada") String sortBy,

            @Parameter(description = "Direção da ordenação (ASC ou DESC)", example = "DESC")
            @RequestParam(defaultValue = "DESC") String direction
    ) {
        // 1. Defina um valor padrão
        int page = 0;

        // 2. Tente fazer o parsing da string
        if (pageStr != null && !pageStr.equalsIgnoreCase("NaN")) {
            try {
                page = Integer.parseInt(pageStr);
            } catch (NumberFormatException e) {
                // Opcional: Lançar uma exceção de BadRequest
                // throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "O parâmetro 'page' deve ser um número inteiro.");
                System.out.println("Aviso: Valor inválido para página ('" + pageStr + "'). Usando o valor padrão: 0.");
                page = 0; // Reseta para o valor padrão
            }
        }

        Sort.Direction sortDirection = direction.equalsIgnoreCase("ASC")
                ? Sort.Direction.ASC
                : Sort.Direction.DESC;

        Pageable pageable = PageRequest.of(page, size, Sort.by(sortDirection, sortBy));
        PageResponse<RegisterResponse> response = registroHorasService.findAllRegisteredHoursPage(pageable);

        return ResponseEntity.ok(response);
    }

}