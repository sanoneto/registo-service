package com.aneto.registo_horas_service.models.Training;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class TrainingRuleConstants {

    private TrainingRuleConstants() {}

    public static final String CATEGORIA_CARDIO = "CARDIO";
    public static final int CARDIO_OBRIGATORIO_POR_DIA_CORE = 2;
    public static final int CARDIO_OBRIGATORIO_POR_DIA_MANUTENCAO = 1;

    public static final Map<String, Double> MET_POR_CATEGORIA = Map.ofEntries(
            Map.entry("PEITO", 5.0),
            Map.entry("COSTAS", 5.0),
            Map.entry("PERNAS", 6.0),
            Map.entry("OMBROS", 4.5),
            Map.entry("BRAÇOS", 4.0),
            Map.entry("CORE", 4.0),
            Map.entry("REAB/MOBILIDADE", 2.5),
            Map.entry("MOBILIDADE", 2.5),
            Map.entry("REABILITAÇÃO", 2.5),
            Map.entry("ALONGAMENTO", 2.0),
            Map.entry("FINALIZADOR", 8.0),
            Map.entry("CARDIO", 7.0)
    );
    public static final double MET_DEFAULT = 4.5;

    public static final Map<String, String> EXERCISE_MAP;
    static {
        Map<String, String> map = new java.util.HashMap<>();
        map.put("agachamento cálice", "Agachamento Goblet");
        map.put("agachamento goblet", "Agachamento Goblet");
        map.put("puxada corda", "Pulldown Corda");
        map.put("pulldown corda", "Pulldown Corda");
        map.put("ywt", "Y-W-T");
        map.put("y-w-t", "Y-W-T");
        map.put("mobilidade tornozelo", "Knee-to-Wall");
        map.put("tornozelo", "Knee-to-Wall");
        map.put("equilíbrio unipodal", "Equilíbrio Unipodal");
        map.put("prancha", "Prancha Abdominal");
        map.put("prancha abdominal", "Prancha Abdominal");
        map.put("cat cow", "Cat Cow");
        map.put("cat-cow", "Cat Cow");
        map.put("bird dog", "Bird Dog");
        map.put("bird-dog", "Bird Dog");
        map.put("dead bug", "Dead Bug");
        map.put("deadbug", "Dead Bug");
        map.put("dips", "Dips");
        map.put("paralelas", "Dips");
        map.put("Dips / Paralelas", "Dips");
        map.put("stiff", "Stiff");
        map.put("rdl", "Stiff");
        map.put("Stiff / RDL", "Stiff");
        map.put("elevações", "Elevações");
        map.put("pull-ups", "Elevações");
        map.put("Elevações / Pull-ups", "Elevações");
        map.put("lunge", "Lunge");
        map.put("afundo", "Lunge");
        map.put("Lunge / Afundo", "Lunge");
        EXERCISE_MAP = Collections.unmodifiableMap(map);
    }

    public static final Map<String, java.util.Set<String>> GRUPO_MUSCULAR_KEYWORDS = Map.of(
            "PEITO", java.util.Set.of("peito", "supino", "crossover", "peck", "flex"),
            "COSTAS", java.util.Set.of("costas", "dorsal", "remada", "puxada", "pull"),
            "PERNAS", java.util.Set.of("pernas", "perna", "quadríceps", "quadriceps", "isquio", "gluteo", "glúteo", "agachamento", "leg", "coxa", "anca", "tornozelo", "gémeo", "gemeo"),
            "OMBROS", java.util.Set.of("ombro", "deltoide", "lateral", "desenvolvimento", "arnold"),
            "BRAÇOS", java.util.Set.of("braço", "braco", "tríceps", "triceps", "bíceps", "biceps", "rosca")
    );

    public static final Map<String, java.util.Set<String>> GRUPO_ALVO_EXERCICIO_MOBILIDADE = Map.ofEntries(
            Map.entry("Knee-to-Wall", java.util.Set.of("PERNAS")),
            Map.entry("Cossack Squat", java.util.Set.of("PERNAS")),
            Map.entry("Y-W-T", java.util.Set.of("COSTAS", "OMBROS")),
            Map.entry("Open Books", java.util.Set.of("COSTAS", "OMBROS")),
            Map.entry("Flexores da Anca", java.util.Set.of("PERNAS")),
            Map.entry("Alongamento borboleta", java.util.Set.of("PERNAS")),
            Map.entry("Alongamento do músculo grande dorsal", java.util.Set.of("COSTAS")),
            Map.entry("Walking Lunges with Reach", java.util.Set.of("PERNAS")),
            Map.entry("Frankenstein Walk", java.util.Set.of("PERNAS"))
    );

    public static final Map<String, String> MOBILIDADE_ESPECIFICA_PARA_PATOLOGIA = Map.of(
            "Rotação Externa", "ombro",
            "Clamshell", "joelho"
    );

    public static final Map<String, List<String>> FALLBACK_DICTIONARY;
    static {
        Map<String, List<String>> map = new LinkedHashMap<>();
        map.put("PEITO", List.of("Supino Plano", "Supino Inclinado", "Supino Declinado", "Supino Plano com Halteres", "Supino Inclinado com Halteres", "Supino Declinado com Halteres", "Peck Deck", "Crossover", "Crossover Baixo para Cima", "Flexões", "Flexões Diamond", "Flexões com Pés Elevados", "Dips", "Aberturas com Halteres", "Aberturas Inclinadas com Halteres", "Supino Máquina", "Pullover com Halter", "Chest Press Máquina"));
        map.put("COSTAS", List.of("Puxada à Frente", "Puxada Pega Estreita", "Puxada Pega Neutra", "Remada Curvada", "Remada Curvada com Halteres", "Remada Unilateral", "Pulldown Corda", "Remada Baixa", "Remada Cavalinho", "Elevações", "Remada Máquina", "Remada T-Bar", "Face Pull", "Encolhimentos com Halteres", "Extensão Lombar", "Puxada com Corda Neutra", "Remada Invertida"));
        map.put("PERNAS", List.of("Agachamento Livre", "Agachamento Goblet", "Agachamento Búlgaro", "Agachamento Sumô", "Leg Press 45", "Hack Squat", "Cadeira Extensora", "Mesa Flexora", "Cadeira Flexora em Pé", "Stiff", "Stiff Unilateral", "Elevação Pélvica", "Elevação Pélvica Unilateral", "Lunge", "Lunge Reverso", "Gémeos em Pé", "Gémeos Sentado", "Abdução de Anca na Máquina", "Adução de Anca na Máquina", "Step Up"));
        map.put("OMBROS", List.of("Desenvolvimento", "Desenvolvimento com Halteres", "Arnold Press", "Elevação Lateral", "Elevação Lateral Polia", "Elevação Lateral Máquina", "Elevação Frontal", "Elevação Frontal com Barra", "Face Pull", "Remada Alta", "Crucifixo Invertido", "Crucifixo Invertido na Máquina", "Desenvolvimento Máquina", "Encolhimentos com Barra"));
        map.put("BRAÇOS", List.of("Rosca Direta", "Rosca Direta com Barra EZ", "Rosca Martelo", "Rosca Concentrada", "Rosca Scott", "Rosca Alternada com Halteres", "Tríceps Corda", "Tríceps Pulley", "Tríceps Testa", "Tríceps Testa com Halter", "Tríceps Francês", "Mergulho no Banco", "Tríceps Coice com Halter", "Rosca no Cabo"));
        map.put("CORE", List.of("Dead Bug", "Dead Bug com Carga", "Prancha Abdominal", "Prancha Lateral", "Bird Dog", "Abdominal na Polia", "Elevação de Pernas Suspenso", "Abdominal na Bola Suíça", "Russian Twist", "Prancha com Toque no Ombro"));
        map.put("CARDIO", List.of("Saltar Corda", "Air Bike", "Ski Erg", "Remo", "Jumping Jacks", "bicicleta", "Passadeira", "Eliptica", "Escadas"));
        map.put("REAB/MOBILIDADE", List.of("Cat Cow", "Clamshell", "Y-W-T", "Rotação Externa", "Knee-to-Wall", "Cossack Squat", "Equilíbrio Unipodal", "Open Books", "Bird Dog Isométrico", "Mobilidade Torácica"));
        FALLBACK_DICTIONARY = Collections.unmodifiableMap(map);
    }
}