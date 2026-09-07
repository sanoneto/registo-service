package com.aneto.registo_horas_service.models.Training;

import com.aneto.registo_horas_service.dto.response.Macros;
import com.aneto.registo_horas_service.dto.response.MealSuggestion;
import lombok.NoArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@NoArgsConstructor
public class MacroCalculator {

    // Limites realistas para deteção de erros de unidade / valores absurdos
    private static final double ALTURA_MIN_CM = 100.0;   // abaixo disto é fisicamente improvável num adulto
    private static final double ALTURA_MAX_CM = 250.0;   // acima disto também é improvável
    private static final double PESO_MIN_KG = 20.0;
    private static final double PESO_MAX_KG = 400.0;

    public static Macros calculate(double weight, double height, int age, String gender, String bodyType, Double bodyFat, int mealsPerDay) {
        // 1. Sanitização e normalização de unidades
        weight = sanitizeWeight(weight);
        height = sanitizeHeightCm(height);

        String typeKey = (bodyType == null) ? "MESOMORFO" : bodyType.toUpperCase();

        // 2. TMB e Fórmulas
        boolean usedKatch = (bodyFat != null && bodyFat > 0);
        double tmb = calculateTMB(weight, height, age, gender, bodyFat, usedKatch);
        String formulaDescription = usedKatch
                ? "Katch-McArdle (Alta Precisão: Baseado em Massa Magra)"
                : "Mifflin-St Jeor (Padrão Biométrico)";

        // 3. Calorias Totais
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

    /**
     * Normaliza o peso recebido, corrigindo valores fora de gama razoável.
     * Não faz conversão de unidades (kg é a única unidade suportada) — apenas
     * aplica um valor de fallback caso o dado seja inválido ou absurdo.
     */
    private static double sanitizeWeight(double weight) {
        if (weight <= 0 || weight < PESO_MIN_KG || weight > PESO_MAX_KG) {
            return 70.0;
        }
        return weight;
    }

    /**
     * Normaliza a altura recebida. Deteta o erro comum de a altura vir em METROS
     * (ex: 1.75) em vez de CENTÍMETROS (ex: 175), e corrige automaticamente
     * multiplicando por 100 nesse caso. Se, mesmo assim, o valor ficar fora
     * de uma gama humana plausível, usa um fallback seguro.
     */
    private static double sanitizeHeightCm(double heightCm) {
        if (heightCm <= 0) {
            return 170.0;
        }

        // Erro típico: altura enviada em metros (ex: 1.75) em vez de cm (175)
        if (heightCm < 3.0) {
            heightCm = heightCm * 100;
        }

        if (heightCm < ALTURA_MIN_CM || heightCm > ALTURA_MAX_CM) {
            return 170.0;
        }

        return heightCm;
    }

    private static int calculateProtein(String bodyType, double weight) {
        return switch (bodyType) {
            case "ENDOMORFO" -> (int) (weight * 2.4);
            case "ECTOMORFO" -> (int) (weight * 2.0);
            default          -> (int) (weight * 2.2);
        };
    }

    private static int calculateFats(String bodyType, double weight) {
        return switch (bodyType) {
            case "ENDOMORFO" -> (int) (weight * 0.8);
            case "ECTOMORFO" -> (int) (weight * 1.1);
            default          -> (int) (weight * 1.0);
        };
    }

    private static int getDailyCalories(String bodyType, double tmb) {
        double activityFactor = switch (bodyType) {
            case "ECTOMORFO" -> 1.6;
            case "ENDOMORFO" -> 1.4;
            default          -> 1.5;
        };

        int surplus = switch (bodyType) {
            case "ECTOMORFO" -> 450;
            case "ENDOMORFO" -> 150;
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
        if (heightCm <= 0) return 0;
        double heightM = heightCm / 100;
        double imc = weight / (heightM * heightM);
        // Arredonda a 1 casa decimal para evitar ruído de precisão double
        return Math.round(imc * 10.0) / 10.0;
    }

    public static String getIMCCategory(double imc) {
        if (imc <= 0) return "Indisponível";
        if (imc < 16.0) return "Magreza Grave";
        if (imc < 17.0) return "Magreza Moderada";
        if (imc < 18.5) return "Abaixo do Peso";
        if (imc < 25.0) return "Peso Ideal";
        if (imc < 30.0) return "Sobrepeso";
        if (imc < 35.0) return "Obesidade Grau I";
        if (imc < 40.0) return "Obesidade Grau II";
        return "Obesidade Grau III (Mórbida)";
    }
}