package com.aneto.registo_horas_service.dto.response;

import java.util.List;

public record DietPlan(
        Integer dailyCalories,
        MacroDistribution macroDistribution,
        List<Meal> meals, // Alterado para List por conveniência
        String localTips
) {
}