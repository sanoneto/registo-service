package com.aneto.registo_horas_service.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.Column;

import java.time.LocalDate;
import java.time.LocalTime;

@JsonIgnoreProperties(ignoreUnknown = true)
public record EventRequest(
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
        boolean sendAlert,
        boolean isMobile,
        String endDate,
        PushSubscriptionDTO notificationSubscription // Adicionado para receber os dados do React
) {
}
