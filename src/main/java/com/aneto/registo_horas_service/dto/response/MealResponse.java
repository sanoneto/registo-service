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
public class MealResponse {
    private String time;
    private String description;
    private List<String> ingredients;
    private int calories;
    private int protein;
    private int carbs;
    private int fats;
}