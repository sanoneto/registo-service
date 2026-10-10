package com.aneto.registo_horas_service.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DietPlanResponse {
    private int dailyCalories;
    private double imc;
    private String imcCategory;
    private MacroDistribution macroDistribution;
    private List<MealResponse> meals;
}