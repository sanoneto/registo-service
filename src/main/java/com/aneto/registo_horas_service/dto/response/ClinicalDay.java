package com.aneto.registo_horas_service.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClinicalDay {
    private String day; // Ex: "Dia 1 - Foco Lombar e Core"

    @Builder.Default
    private List<ClinicalBlock> blocks = new ArrayList<>(); // 4 blocos específicos para este dia
}