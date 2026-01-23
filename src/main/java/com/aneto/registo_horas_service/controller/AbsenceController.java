package com.aneto.registo_horas_service.controller;

import com.aneto.registo_horas_service.dto.request.AbsenceRequest;
import com.aneto.registo_horas_service.models.Absence;
import com.aneto.registo_horas_service.models.Enum;
import com.aneto.registo_horas_service.service.AbsenceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

@RestController
@RequestMapping("/api/ausencias")
@RequiredArgsConstructor
public class AbsenceController {


    private final AbsenceService absenceService;
    private static final String X_USER_ID = "X-User-Id";

    // --- 1. POST (Registo) ---
    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Absence> registerAbsence(@Valid @RequestBody AbsenceRequest absenceRequest) {
        try {
            // Nota: Se usar Spring Security, valide se dto.getUserName() corresponde ao usuário autenticado.
            Absence registeredAbsence = absenceService.registerAbsence(absenceRequest);
            return new ResponseEntity<>(registeredAbsence, HttpStatus.CREATED);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    // ----------------------------------------------------------------------
    // --- 2. GET (Listagem Paginada - Para ADMIN) ---
    // ----------------------------------------------------------------------
    @GetMapping("/Pageable/list")
    @PreAuthorize("hasRole('ADMIN')")
    public Page<Absence> listAllAbsencesPage(
            @RequestParam(required = false) Enum.AbsenceStatus status, // Filtro opcional por Status
            @PageableDefault(sort = "registrationDate", direction = Sort.Direction.DESC, size = 10) Pageable pageable
    ) {
        // Se for ADMIN, o userName é null, e o serviço retorna todos
        return absenceService.getAbsencesPage(null, status, pageable);
    }

    // ----------------------------------------------------------------------
    // --- 3. GET (Listagem Paginada - Para Usuário Comum) ---
    // ----------------------------------------------------------------------
    @GetMapping("/Pageable/list/{username}")
    @PreAuthorize("hasAnyRole('ADMIN') || #username == authentication.name") // Proteção
    public Page<Absence> listUserAbsencesPage(
            @PathVariable String username,
            @RequestParam(required = false) Enum.AbsenceStatus status, // Filtro opcional por Status
            @PageableDefault(sort = "registrationDate", direction = Sort.Direction.DESC, size = 10) Pageable pageable
    ) {
        // O serviço filtra automaticamente pelo userName passado na URL
        return absenceService.getAbsencesPage(username, status, pageable);
    }

    // ----------------------------------------------------------------------
    // --- 4. DELETE (Exclusão) ---
    // ----------------------------------------------------------------------
    @DeleteMapping("/{publicId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteAbsence(@PathVariable String publicId) {
        try {
            absenceService.deleteByPublicId(publicId);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Registo de ausência não encontrado ou não pode ser eliminado.");
        }
    }

    // ----------------------------------------------------------------------
    // --- 5. PATCH (Atualização de Status por ADMIN) ---
    // ----------------------------------------------------------------------
    @PatchMapping("/status/{publicId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Absence> updateAbsenceStatus(
            @PathVariable String publicId,
            @RequestParam Enum.AbsenceStatus newStatus) {

        Optional<Absence> updatedAbsence = absenceService.updateAbsenceStatus(publicId, newStatus);

        return updatedAbsence.map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }
}