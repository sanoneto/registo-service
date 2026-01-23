package com.aneto.registo_horas_service.models.Training;

import lombok.NoArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@NoArgsConstructor
public class MacroCalculator {

        public record Macros(int calories, int protein, int carbs, int fats, String formula) {}

        public static Macros calculate(double weight, double height, int age, String gender, String bodyType, Double bodyFat) {
            // 1. Definição da Fórmula e Cálculo da TMB
            boolean usedKatch = (bodyFat != null && bodyFat > 0);
            String formulaName = usedKatch ? "Katch-McArdle (baseada em massa magra)" : "Mifflin-St Jeor (baseada em peso total)";

            double tmb;
            if (usedKatch) {
                double leanMass = weight * (1 - (bodyFat / 100));
                tmb = 370 + (21.6 * leanMass);
            } else {
                tmb = (10 * weight) + (6.25 * height) - (5 * age) +
                        ("Masculino".equalsIgnoreCase(gender) ? 5 : -161);
            }

            // 2. Ajuste de Fator de Atividade e Excedente Calórico por Biótipo
            double activityFactor;
            int calorieAdjustment;

            switch (bodyType.toUpperCase()) {
                case "ECTOMORFO" -> {
                    activityFactor = 1.55;  // Metabolismo rápido
                    calorieAdjustment = 400; // Superávit maior para ganhar peso
                }
                case "ENDOMORFO" -> {
                    activityFactor = 1.30;  // Metabolismo lento/cauteloso
                    calorieAdjustment = 200; // Superávit baixo para evitar gordura
                }
                default -> { // MESOMORFO
                    activityFactor = 1.45;
                    calorieAdjustment = 300;
                }
            }

            int dailyCalories = (int) (tmb * activityFactor) + calorieAdjustment;

            // 3. Distribuição de Macros baseada no Biótipo
            int proteinGrams, fatGrams, carbGrams;

            if ("ENDOMORFO".equalsIgnoreCase(bodyType)) {
                // Endomorfos beneficiam de menos carbs e mais proteína/gordura (controlo de insulina)
                proteinGrams = (int) (weight * 2.3);
                fatGrams = (int) (weight * 1.0);
            } else if ("ECTOMORFO".equalsIgnoreCase(bodyType)) {
                // Ectomorfos precisam de muitos carbs para combustível
                proteinGrams = (int) (weight * 2.0);
                fatGrams = (int) (weight * 0.8);
            } else { // MESOMORFO
                proteinGrams = (int) (weight * 2.2);
                fatGrams = (int) (weight * 0.9);
            }

            // O restante das calorias é preenchido por Carbohidratos
            // Fórmula: (Calorias Totais - (Proteína * 4) - (Gordura * 9)) / 4
            int caloriesFromProteinAndFat = (proteinGrams * 4) + (fatGrams * 9);
            carbGrams = Math.max(0, (dailyCalories - caloriesFromProteinAndFat) / 4);

            return new Macros(dailyCalories, proteinGrams, carbGrams, fatGrams, formulaName);
        }
    }