package com.aneto.registo_horas_service.dto.response;

public record MealSuggestion(
        String name,
        String time,
        double pctCalories, // Percentagem das calorias totais
        double pctProtein,  // Percentagem da proteína total
        double pctCarbs,    // Percentagem dos hidratos total
        double pctFats      // Percentagem da gordura total
) {}