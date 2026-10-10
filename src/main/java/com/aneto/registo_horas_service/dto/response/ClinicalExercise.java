package com.aneto.registo_horas_service.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClinicalExercise {
    private int order;
    private String name;
    private String targetFocus;             // Ex: "Chave de Reabilitação", "Alívio Neural"
    private String dosageRight;             // Ex: "Dir: 4x 10-12 reps"
    private String dosageLeft;              // Ex: "Esq: 2x 10 reps"
    private String tempoRpe;                // Ex: "Cadência 3-2-1 / RPE 6-7"
    private String executionInstructions;   // Instruções técnicas de execução
    private String safetyNotes;              // Sinais de alarme e proteção articular
    private String videoUrl;
}