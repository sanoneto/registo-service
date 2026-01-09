package com.aneto.registo_horas_service.dto.response;

import jakarta.persistence.Column;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

public record EventsResponse(
        UUID id,
        String username,

        @Column(nullable = false)
        String title,

        String project,

        @Column(name = "reference_date", nullable = false)
        LocalDate referenceDate,

        @Column(name = "start_time", nullable = false)
        LocalTime startTime,

        LocalTime endTime,

        @Column(columnDefinition = "TEXT")
        String notes,
        boolean sendAlert
) {
}
