package com.aneto.registo_horas_service.dto.response;

import java.util.List;

public record DietPlan(
        String methodology,
        List<Meal> meals,
        Double imc,            // NOVO
        String imcCategory,    // NOVO
        String statusSummary,
        Integer dailyCalories,
        MacroDistribution macroDistribution,
        // Alterado para List por conveniência
        String localTips
) {
}