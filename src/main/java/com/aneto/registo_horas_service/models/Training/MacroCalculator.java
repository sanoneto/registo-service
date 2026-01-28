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

        // 3. Calorias Totais (Usando o método que você criou abaixo)
        int dailyCalories = getDailyCalories(typeKey, tmb);

        // 4. Macros Totais
        int proteinGrams = calculateProtein(typeKey, weight);
        int fatGrams = calculateFats(typeKey, weight);

        // Cálculo de Carboidratos Restantes
        int caloriesFromPAndF = (proteinGrams * 4) + (fatGrams * 9);
        int carbGrams = Math.max(50, (dailyCalories - caloriesFromPAndF) / 4);

        // 5. Análise Biométrica
        double imc = calculateIMC(weight, height);
        String imcCat = getIMCCategory(imc);

        String bfAnalysis = (bodyFat != null)
                ? (bodyFat < 12 ? "BF Baixo: Prioridade em ganho de volume bruto." : "BF Controlado: Foco em ganho de massa limpa.")
                : "BF não informado: Estimar visualmente a sensibilidade insulínica.";

        String aiAnalysisSummary = String.format(
                "Perfil %s. IMC: %.1f (%s). %s Objetivo: Maximizar anabolismo.",
                typeKey, imc, imcCat, bfAnalysis
        );

        // 6. Distribuição via Enum
        List<MealSuggestion> meals = MealDistribution
                .fromBodyType(typeKey)
                .getMeals(mealsPerDay);

        validateMealIntegrity(meals);

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

    // --- MÉTODOS AUXILIARES ---

    private static int calculateProtein(String bodyType, double weight) {
        return switch (bodyType) {
            case "ENDOMORFO" -> (int) (weight * 2.4);
            case "ECTOMORFO" -> (int) (weight * 2.0);
            default          -> (int) (weight * 2.2);
        };
    }

    private static int calculateFats(String bodyType, double weight) {
        return switch (bodyType) {
            case "ENDOMORFO" -> (int) (weight * 0.8); // Menos gordura, mais proteína
            case "ECTOMORFO" -> (int) (weight * 1.1); // Mais gordura para bater calorias
            default          -> (int) (weight * 1.0);
        };
    }

    private static int getDailyCalories(String bodyType, double tmb) {
        double activityFactor = switch (bodyType) {
            case "ECTOMORFO" -> 1.6; // Metabolismo rápido + Treino Intenso
            case "ENDOMORFO" -> 1.4;
            default          -> 1.5;
        };

        int surplus = switch (bodyType) {
            case "ECTOMORFO" -> 450; // Superavit agressivo
            case "ENDOMORFO" -> 150; // Superavit controlado
            default          -> 300;
        };

        return (int) (tmb * activityFactor) + surplus;
    }

    private static double calculateTMB(double w, double h, int a, String g, Double bf, boolean usedKatch) {
        if (usedKatch) {
            double leanMass = w * (1 - (bf / 100));
            return 370 + (21.6 * leanMass);
        }
        return (10 * w) + (6.25 * h) - (5 * a) + ("Masculino".equalsIgnoreCase(g) ? 5 : -161);
    }

    private static void validateMealIntegrity(List<MealSuggestion> meals) {
        if (meals == null || meals.isEmpty()) return;
        double totalPct = meals.stream().mapToDouble(MealSuggestion::pctCalories).sum();
        if (Math.abs(totalPct - 1.0) > 0.001) {
            throw new IllegalStateException(String.format("Erro na soma das refeições: %.2f%%", totalPct * 100));
        }
    }

    public static double calculateIMC(double weight, double heightCm) {
        if (heightCm <= 0) return 0; // Evita divisão por zero
        double heightM = heightCm / 100;
        return weight / (heightM * heightM);
    }

    public static String getIMCCategory(double imc) {
        if (imc < 18.5) return "Abaixo do Peso";
        if (imc < 25.0) return "Peso Ideal";
        if (imc < 30.0) return "Sobrepeso";
        return "Obesidade";
    }
}