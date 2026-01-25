package com.aneto.registo_horas_service.models.Training;

import com.aneto.registo_horas_service.dto.response.MealSuggestion;

import java.util.List;

public enum MealDistribution {
    ECTOMORFO {
        @Override
        public List<MealSuggestion> getMeals(int count) {
            return switch (count) {
                case 4 -> List.of(
                        new MealSuggestion("Pequeno-almoço", "08:00", 0.25, 0.20, 0.30, 0.20),
                        new MealSuggestion("Almoço", "13:00", 0.35, 0.30, 0.40, 0.30),
                        new MealSuggestion("Jantar", "20:30", 0.30, 0.30, 0.25, 0.40),
                        new MealSuggestion("Ceia", "23:00", 0.10, 0.20, 0.05, 0.10)
                );
                case 5 -> List.of(
                        new MealSuggestion("Pequeno-almoço", "08:00", 0.20, 0.20, 0.25, 0.15),
                        new MealSuggestion("Almoço", "13:00", 0.30, 0.25, 0.35, 0.25),
                        new MealSuggestion("Lanche", "17:00", 0.15, 0.15, 0.20, 0.10),
                        new MealSuggestion("Jantar", "20:30", 0.25, 0.20, 0.15, 0.40),
                        new MealSuggestion("Ceia", "23:00", 0.10, 0.20, 0.05, 0.10)
                );
                default -> List.of(
                        new MealSuggestion("Pequeno-almoço", "08:00", 0.15, 0.15, 0.20, 0.10),
                        new MealSuggestion("Lanche da Manhã", "10:30", 0.10, 0.10, 0.10, 0.10),
                        new MealSuggestion("Almoço", "13:00", 0.25, 0.20, 0.30, 0.25),
                        new MealSuggestion("Lanche da Tarde", "17:00", 0.15, 0.15, 0.20, 0.10),
                        new MealSuggestion("Jantar", "20:30", 0.25, 0.20, 0.15, 0.35),
                        new MealSuggestion("Ceia", "23:00", 0.10, 0.20, 0.05, 0.10)
                );
            };
        }
    },
    DEFAULT {
        @Override
        public List<MealSuggestion> getMeals(int count) {
            return switch (count) {
                case 4 -> List.of(
                        new MealSuggestion("Pequeno-almoço", "08:00", 0.25, 0.25, 0.30, 0.20),
                        new MealSuggestion("Almoço",          "13:00", 0.35, 0.30, 0.40, 0.30),
                        new MealSuggestion("Jantar",          "20:30", 0.30, 0.30, 0.25, 0.40),
                        new MealSuggestion("Ceia",            "23:00", 0.10, 0.15, 0.05, 0.10)
                );
                case 5 -> List.of(
                        new MealSuggestion("Pequeno-almoço", "08:00", 0.20, 0.20, 0.25, 0.20),
                        new MealSuggestion("Almoço",          "13:00", 0.35, 0.25, 0.40, 0.30),
                        new MealSuggestion("Lanche",          "17:00", 0.10, 0.10, 0.15, 0.10),
                        new MealSuggestion("Jantar",          "20:30", 0.25, 0.25, 0.20, 0.35),
                        new MealSuggestion("Ceia",            "23:00", 0.10, 0.20, 0.00, 0.05)
                );
                default -> List.of(
                        new MealSuggestion("Pequeno-almoço", "08:00", 0.15, 0.20, 0.20, 0.15),
                        new MealSuggestion("Lanche Manhã",    "10:30", 0.10, 0.10, 0.10, 0.10),
                        new MealSuggestion("Almoço",          "13:00", 0.25, 0.25, 0.35, 0.20),
                        new MealSuggestion("Lanche Tarde",    "17:00", 0.15, 0.15, 0.20, 0.15),
                        new MealSuggestion("Jantar",          "20:30", 0.25, 0.20, 0.15, 0.35),
                        new MealSuggestion("Ceia",            "23:00", 0.10, 0.10, 0.00, 0.05)
                );
            };
        }
    };

    public abstract List<MealSuggestion> getMeals(int count);

    // --- AQUI ESTÁ O QUE PERGUNTASTE ---
    public static MealDistribution fromBodyType(String bodyType) {
        if (bodyType == null) return DEFAULT;

        return switch (bodyType.toUpperCase()) {
            case "ECTOMORFO" -> ECTOMORFO;
            default -> DEFAULT; // Mesomorfos e Endomorfos usam o padrão
        };
    }
}