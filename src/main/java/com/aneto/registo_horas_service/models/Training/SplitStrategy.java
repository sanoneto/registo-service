package com.aneto.registo_horas_service.models.Training;

import lombok.Getter;

import java.util.List;
import java.util.Map;

@Getter
public enum SplitStrategy {

    STANDARD(Map.of(
            1, List.of("FULL BODY: Corpo Inteiro"),
            2, List.of("UPPER: Peito, Costas, Ombros e Braços", "LOWER: Quadríceps, Posterior e Glúteos"),
            3, List.of("PUSH: Peito, Ombros e Tríceps", "PULL: Costas e Bíceps", "LEGS: Quadríceps, Posterior e Glúteos"),
            4, List.of("UPPER: Peito e Costas", "LOWER: Quadríceps e Glúteos", "UPPER: Ombros e Braços", "LOWER: Posterior e Panturrilhas"),
            5, List.of("PUSH: Peito, Ombros e Tríceps", "PULL: Costas e Bíceps", "LEGS: Quadríceps e Glúteos", "UPPER: Peito, Costas e Ombros", "LOWER: Posterior, Glúteos e Core"),
            6, List.of("PUSH: Peito e Tríceps", "PULL: Costas e Bíceps", "LEGS: Quadríceps e Panturrilhas", "SHOULDERS: Ombros e Core", "LEGS: Posterior e Glúteos", "FULL BODY: Condicionamento e Mobilidade")
    )),

    GLUTE_FOCUS(Map.of(
            1, List.of("FULL BODY: Glúteos, Pernas e Tronco"),
            2, List.of("LOWER: Foco Glúteos e Posterior", "UPPER: Peito, Costas, Ombros e Braços"),
            3, List.of("LEGS: Glúteos e Posterior", "UPPER: Peito, Costas e Ombros", "LEGS: Quadríceps e Glúteos"),
            4, List.of("LEGS: Glúteos e Posterior", "PUSH: Peito, Ombros e Tríceps", "LEGS: Quadríceps e Glúteos", "PULL: Costas e Bíceps"),
            5, List.of("LEGS: Glúteos e Posterior", "PUSH: Peito, Ombros e Tríceps", "LEGS: Quadríceps e Glúteos", "PULL: Costas e Bíceps", "FULL BODY: Foco Glúteos e Core"),
            6, List.of("LEGS: Glúteos e Posterior", "PUSH: Peito, Ombros e Tríceps", "LEGS: Quadríceps e Glúteos", "PULL: Costas e Bíceps", "FULL BODY: Foco Glúteos e Core", "LEGS: Glúteos e Isquiotibiais")
    )),

    GLUTE_EXTREME(Map.of(
            1, List.of("LEGS: Foco Glúteos (Grande, Médio, Mínimo)"),
            2, List.of("LEGS: Foco Glúteos (Grande, Médio, Mínimo)", "MANUTENÇÃO: Superiores e Core"),
            3, List.of("LEGS: Foco Glúteos (Grande, Médio, Mínimo)", "MANUTENÇÃO: Superiores e Core", "LEGS: Foco Glúteos (Grande, Médio, Mínimo)"),
            4, List.of("LEGS: Foco Glúteos (Grande, Médio, Mínimo)", "CORE: Core e Condicionamento (Cardio Obrigatório)", "LEGS: Foco Glúteos (Grande, Médio, Mínimo)", "SUPERIOR: Manutenção de Peito, Costas e Ombros"),
            5, List.of("LEGS: Foco Glúteos (Grande, Médio, Mínimo)", "CORE: Core e Condicionamento (Cardio Obrigatório)", "LEGS: Foco Glúteos (Grande, Médio, Mínimo)", "SUPERIOR: Manutenção de Peito, Costas e Ombros", "LEGS: Foco Glúteos (Grande, Médio, Mínimo)"),
            6, List.of("LEGS: Foco Glúteos (Grande, Médio, Mínimo)", "CORE: Core e Condicionamento (Cardio Obrigatório)", "LEGS: Foco Glúteos (Grande, Médio, Mínimo)", "SUPERIOR: Manutenção de Peito, Costas e Ombros", "LEGS: Foco Glúteos (Grande, Médio, Mínimo)", "MANUTENÇÃO: Superiores e Core")
    ));

    private final Map<Integer, List<String>> splitsByFrequency;

    SplitStrategy(Map<Integer, List<String>> splitsByFrequency) {
        this.splitsByFrequency = splitsByFrequency;
    }

    public List<String> getSplit(int frequency) {
        int key = Math.min(Math.max(frequency, 1), 6);
        return splitsByFrequency.getOrDefault(key, splitsByFrequency.get(6));
    }

    public static SplitStrategy selectStrategy(boolean focoExtremo, boolean isGluteFocus) {
        if (focoExtremo) return GLUTE_EXTREME;
        if (isGluteFocus) return GLUTE_FOCUS;
        return STANDARD;
    }
}