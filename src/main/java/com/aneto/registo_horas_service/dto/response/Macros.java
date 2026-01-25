package com.aneto.registo_horas_service.dto.response;

import java.util.List;

public record Macros(
        int dailyCalories,
        int protein,
        int carbs,
        int fats,
        String formula,
        List<MealSuggestion> mealSuggestions,
        double imc,            // NOVO
        String imcCategory,    // NOVO
        String statusSummary)  // NOVO (O texto para a IA)) {
{
}