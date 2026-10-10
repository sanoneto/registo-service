package com.aneto.registo_horas_service.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClinicalBlock implements Serializable {
    private String blockName;     // Ex: "BLOCO 1: LIBERTAÇÃO, MOBILIDADE & ATIVAÇÃO"
    private String durationText;   // Ex: "10 Minutos"

    @Builder.Default
    private List<ClinicalExercise> exercises = new ArrayList<>();
}