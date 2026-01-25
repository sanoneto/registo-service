package com.aneto.registo_horas_service.models.Training;

import com.aneto.registo_horas_service.dto.response.Macros;
import com.aneto.registo_horas_service.dto.response.MealSuggestion;
import lombok.NoArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@NoArgsConstructor
public class MacroCalculator {

    public static Macros calculate(double weight, double height, int age, String gender, String bodyType, Double bodyFat, int mealsPerDay) {
        // 1. Sanitização
        weight = (weight <= 0) ? 70.0 : weight;
        height = (height <= 0) ? 170.0 : height;
        String typeKey = (bodyType == null) ? "MESOMORFO" : bodyType.toUpperCase();

        // 2. TMB e Fórmulas
        boolean usedKatch = (bodyFat != null && bodyFat > 0);
        double tmb = calculateTMB(weight, height, age, gender, bodyFat, usedKatch);
        String formulaDescription = usedKatch
                ? "Katch-McArdle (Alta Precisão: Baseado em Massa Magra)"
                : "Mifflin-St Jeor (Padrão Biométrico)";

        // 3. Calorias e Macros Totais
        int dailyCalories = getDailyCalories(typeKey, tmb);
        int proteinGrams = calculateProtein(typeKey, weight);
        int fatGrams = calculateFats(typeKey, weight);

        int caloriesFromPAndF = (proteinGrams * 4) + (fatGrams * 9);
        int carbGrams = Math.max(20, (dailyCalories - caloriesFromPAndF) / 4);

        // 4. Análise Biométrica (O "Cérebro" da IA)
        double imc = calculateIMC(weight, height);
        String imcCat = getIMCCategory(imc);

        String bfAnalysis = (bodyFat != null)
                ? (bodyFat < 12 ? "BF Baixo: Prioridade em ganho de volume bruto." : "BF Controlado: Foco em ganho de massa limpa.")
                : "BF não informado: Estimar visualmente a sensibilidade insulínica.";

        String aiAnalysisSummary = String.format(
                "Perfil %s. IMC: %.1f (%s). %s Objetivo: Maximizar anabolismo.",
                typeKey, imc, imcCat, bfAnalysis
        );

        // 5. Distribuição via Enum
        List<MealSuggestion> meals = MealDistribution
                .fromBodyType(typeKey)
                .getMeals(mealsPerDay);

        validateMealIntegrity(meals);

        // 6. Retorno para o DTO Macros
        return new Macros(
                dailyCalories,
                proteinGrams,
                carbGrams,
                fatGrams,
                formulaDescription,
                meals,
                imc,
                imcCat,
                aiAnalysisSummary
        );
    }
    private static int calculateProtein(String bodyType, double weight) {
        return switch (bodyType) {
            case "ENDOMORFO" -> (int) (weight * 2.3);
            case "ECTOMORFO" -> (int) (weight * 2.0);
            default -> (int) (weight * 2.2); // Mesomorfo ou outros
        };
    }

    private static int calculateFats(String bodyType, double weight) {
        return switch (bodyType) {
            case "ECTOMORFO" -> (int) (weight * 0.8);
            default -> (int) (weight * 0.9); // Endomorfo e Mesomorfo precisam de um pouco mais de gordura para saciedade/hormonas
        };
    }

    // O teu getDailyCalories que já tinhas, mas agora como switch limpo:
    private static int getDailyCalories(String bodyType, double tmb) {
        double activityFactor = switch (bodyType) {
            case "ECTOMORFO" -> 1.55;
            case "ENDOMORFO" -> 1.30;
            default          -> 1.45;
        };

        int calorieAdjustment = switch (bodyType) {
            case "ECTOMORFO" -> 400;
            case "ENDOMORFO" -> 200;
            default          -> 300;
        };

        return (int) (tmb * activityFactor) + calorieAdjustment;
    }

    private static double calculateTMB(double w, double h, int a, String g, Double bf, boolean usedKatch) {
        if (usedKatch) {
            double leanMass = w * (1 - (bf / 100));
            return 370 + (21.6 * leanMass);
        }
        return (10 * w) + (6.25 * h) - (5 * a) + ("Masculino".equalsIgnoreCase(g) ? 5 : -161);
    }

    private static void validateMealIntegrity(List<MealSuggestion> meals) {
        double totalPct = meals.stream().mapToDouble(MealSuggestion::pctCalories).sum();

        // Usamos uma pequena margem de erro (epsilon) para lidar com imprecisões de double
        if (Math.abs(totalPct - 1.0) > 0.001) {
            throw new IllegalStateException(
                    String.format("ERRO CRÍTICO: A soma das calorias das refeições é %.2f%% e deve ser 100%%!", totalPct * 100)
            );
        }
    }

    // Adiciona estes métodos ao MacroCalculator.java
    public static double calculateIMC(double weight, double heightCm) {
        double heightM = heightCm / 100;
        return weight / (heightM * heightM);
    }

    public static String getIMCCategory(double imc) {
        if (imc < 18.5) return "Abaixo do Peso (Risco de Catabolismo)";
        if (imc < 25.0) return "Peso Ideal (Foco em Recomposição)";
        if (imc < 30.0) return "Sobrepeso (Foco em Ganho Limpo)";
        return "Obesidade (Necessidade de Défice Estratégico)";
    }
}