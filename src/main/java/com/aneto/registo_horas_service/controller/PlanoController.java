package com.aneto.registo_horas_service.controller;


import com.aneto.registo_horas_service.dto.request.PlanoRequestDTO;
import com.aneto.registo_horas_service.dto.response.PlanoResponseDTO;
import com.aneto.registo_horas_service.service.PlanoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/planos")
@RequiredArgsConstructor
public class PlanoController {

    private final PlanoService planoService;

    @PreAuthorize("hasRole('ADMIN') or hasRole('ESPECIALISTA') or hasRole('USER') or (hasRole('ESTAGIARIO') and #username == authentication.name)")
    @PostMapping
    public ResponseEntity<?> create(@RequestBody @Valid PlanoRequestDTO request) {
        planoService.createPlano(request);

        // Teste: Retornar um mapa de Strings simples
        // Se o erro continuar aqui, o problema está no seu Spring Security/Filter
        Map<String, String> ok = new HashMap<>();
        ok.put("status", "success");
        ok.put("message", "Plano criado");

        return ResponseEntity.status(HttpStatus.CREATED).body(ok);
    }

    @PreAuthorize("hasRole('ADMIN') or hasRole('ESPECIALISTA') or hasRole('USER') or (hasRole('ESTAGIARIO') and #username == authentication.name)")
    @GetMapping("/{id}")
    public ResponseEntity<PlanoResponseDTO> getPlanId(@PathVariable UUID id) {
        // Nome ajustado para bater com o Service
        PlanoResponseDTO response = planoService.getByPlanoById(id);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletePlan(@PathVariable UUID id) {
        planoService.deletePlano(id);
        return ResponseEntity.noContent().build();
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'ESPECIALISTA', 'ESTAGIARIO','USER')")
    @GetMapping
    public ResponseEntity<Page<PlanoResponseDTO>> listarPlanos(
            @RequestParam(required = false) String nomeAluno,
            @PageableDefault(size = 8, sort = "nomeAluno") Pageable pageable,
            Authentication authentication) {

        // O uso de Collectors.toCollection(ArrayList::new) que você fez é excelente
        // para evitar listas imutáveis do Spring Security.
        List<String> roles = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toCollection(ArrayList::new));

        String username = authentication.getName();

        // Certifique-se que o service retorna um Page<PlanoResponseDTO>
        // e NÃO um Page<Plano> (a entidade)
        Page<PlanoResponseDTO> lista = planoService.listAllOrName(nomeAluno, pageable, roles, username);

        return ResponseEntity.ok(lista);
    }


    @PatchMapping("/{id}/atualizar-status")
    public ResponseEntity<?> updateStatusParaProcessando(
            @RequestHeader(value = "Authorization", required = false) String token,
            @RequestBody Map<String, String> body,        // Recebe o JSON como um Mapa
            @PathVariable String id,
            Authentication authentication) {
        try {
            String estadoPedido = body.get("estadoPedido");

            List<String> roles = authentication.getAuthorities().stream()
                    .map(GrantedAuthority::getAuthority)
                    .toList();
            String username = authentication.getName();


            planoService.changeOfProgress(id, username, estadoPedido);

            return ResponseEntity.ok(Map.of(
                    "message", "Plano atualizado para processamento",
                    "status", estadoPedido
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}