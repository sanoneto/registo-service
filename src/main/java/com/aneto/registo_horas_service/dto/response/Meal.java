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
public class Meal {
    private String time;
    private String description;

    @Builder.Default
    private List<String> ingredients = new ArrayList<>();

    private Integer calories;
    private Integer protein;
    private Integer carbs;
    private Integer fats;
}