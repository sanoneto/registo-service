package com.aneto.registo_horas_service.models;

import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
public class MacroCalculator {

    public record Macros(int calories, int protein, int carbs, int fats) {}

    public static Macros calculate(double weight, double height, int age, String gender, String bodyType) {
        // 1. Calcular Taxa Metabólica Basal (Mifflin-St Jeor)
        double tmb;
        if ("Masculino".equalsIgnoreCase(gender)) {
            tmb = (10 * weight) + (6.25 * height) - (5 * age) + 5;
        } else {
            tmb = (10 * weight) + (6.25 * height) - (5 * age) - 161;
        }

        // 2. Nível de Atividade (Considerando 3x por semana + Recuperação de lesão)
        // Usamos um fator moderado de 1.45 para Ectomorfos
        double tdee = tmb * 1.45;

        // 3. Superávit para Hipertrofia (Ectomorfos precisam de +10% a +15%)
        int dailyCalories = (int) (tdee + 400);

        // 4. Distribuição de Macros
        // Proteína: 2g por kg
        int proteinGrams = (int) (weight * 2);
        // Gordura: 1g por kg (essencial para hormonas e recuperação)
        int fatGrams = (int) (weight * 1);
        // Carbohidratos: O restante das calorias
        int proteinCal = proteinGrams * 4;
        int fatCal = fatGrams * 9;
        int carbGrams = (dailyCalories - proteinCal - fatCal) / 4;

        return new Macros(dailyCalories, proteinGrams, carbGrams, fatGrams);
    }
}