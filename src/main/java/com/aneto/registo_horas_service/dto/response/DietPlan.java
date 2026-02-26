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
public class DietPlan {
    private String methodology;

    @Builder.Default
    private List<Meal> meals = new ArrayList<>();

    private Double imc;
    private String imcCategory;
    private String statusSummary;
    private Integer dailyCalories;
    private MacroDistribution macroDistribution;
    private String localTips;
}