package com.aneto.registo_horas_service.controller;

import com.aneto.registo_horas_service.dto.response.ListJogosResponse;
import com.aneto.registo_horas_service.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/jogos")
public class DashboardController {

    private final DashboardService dashboardService;
    private static final String X_USER_ID = "X-User-Id";


    @GetMapping("/all")
    @PreAuthorize("hasRole('ADMIN') or (hasRole('ESTAGIARIO') and #username == authentication.name)")
    public ResponseEntity<ListJogosResponse> generateJogos(
            @RequestHeader(X_USER_ID) String username) {

        // Agora o request pode chegar como null sem dar erro 400
        ListJogosResponse response = dashboardService.getListJogo(username);
        return ResponseEntity.ok(response);
    }
}
