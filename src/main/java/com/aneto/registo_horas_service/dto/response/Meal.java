package com.aneto.registo_horas_service.dto.response;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record Meal(
        @JsonAlias({"time", "hour"}) String time,
        @JsonAlias({"description", "mealName"}) String description,
        List<String> ingredients,
        Integer calories,
        Integer protein,
        Integer carbs,
        Integer fats
) {}