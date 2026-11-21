package com.aneto.registo_horas_service.controller;

import com.aneto.registo_horas_service.service.impl.LogServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/monitoring")
@RequiredArgsConstructor
public class MonitoringController {

    private final LogServiceImpl logService;

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/inventory-errors")
    public ResponseEntity<List<String>> getErrors() throws IOException {
        return ResponseEntity.ok(logService.getInventoryAlerts());
    }

    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/inventory-errors")
    public ResponseEntity<Void> deleteErrors() throws IOException {
        logService.clearLogs();
        return ResponseEntity.noContent().build();
    }
}