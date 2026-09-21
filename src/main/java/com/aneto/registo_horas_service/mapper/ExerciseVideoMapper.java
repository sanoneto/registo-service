package com.aneto.registo_horas_service.mapper;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class ExerciseVideoMapper {

    private static final Map<String, String> VIDEO_MAP;

    static {
        Map<String, String> map = new HashMap<>();

        // --- PEITO ---
        map.put("Supino Plano", "https://www.youtube.com/watch?v=tuwHzzStWsc");
        map.put("Supino Inclinado", "https://www.youtube.com/watch?v=8iP6v_AseD0");
        map.put("Peck Deck", "https://www.youtube.com/watch?v=O-59BvVvS8E");
        map.put("Crossover", "https://www.youtube.com/watch?v=H75t3r_A48E");
        map.put("Flexões", "https://www.youtube.com/watch?v=IODxDxX7oi4");
        map.put("Dips / Paralelas", "https://www.youtube.com/watch?v=2z8JmcrW-As");

        // --- COSTAS ---
        map.put("Puxada à Frente", "https://www.youtube.com/watch?v=CAwf7n6Luuc");
        map.put("Remada Curvada", "https://www.youtube.com/watch?v=6FZHJGzMFEc");
        map.put("Remada Unilateral", "https://www.youtube.com/watch?v=dFzUjzfih74");
        map.put("Pulldown Corda", "https://www.youtube.com/watch?v=vV77G_tqYpE");
        map.put("Remada Baixa", "https://www.youtube.com/watch?v=GZbfZ033f74");
        map.put("Elevações / Pull-ups", "https://www.youtube.com/watch?v=eGo4IYlbE5g");

        // --- PERNAS ---
        map.put("Agachamento Livre", "https://www.youtube.com/watch?v=U3HlEF_E9fo");
        map.put("Leg Press 45", "https://www.youtube.com/watch?v=iz86pW-K0w0");
        map.put("Cadeira Extensora", "https://www.youtube.com/watch?v=m0FOpMEgero");
        map.put("Mesa Flexora", "https://www.youtube.com/watch?v=uKEnA902C0k");
        map.put("Stiff / RDL", "https://www.youtube.com/watch?v=hCDzSR6bW10");
        map.put("Gémeos em Pé", "https://www.youtube.com/watch?v=N_69o_A7t2o");
        map.put("Lunge / Afundo", "https://www.youtube.com/watch?v=D7KaRcUTQeE");
        map.put("Elevação Pélvica", "https://www.youtube.com/watch?v=SEdqd1n0ad0");

        // --- OMBROS ---
        map.put("Desenvolvimento", "https://www.youtube.com/watch?v=HzIi6mH8BCY");
        map.put("Elevação Lateral", "https://www.youtube.com/watch?v=3VcKaXpzqRo");
        map.put("Face Pull", "https://www.youtube.com/watch?v=rep-qVOkqgk");
        map.put("Elevação Frontal", "https://www.youtube.com/watch?v=-t7fuZ0KhDA");

        // --- BRAÇOS ---
        map.put("Rosca Direta", "https://www.youtube.com/watch?v=yTWO2th-RIY");
        map.put("Tríceps Corda", "https://www.youtube.com/watch?v=vB5OHsJ3EME");
        map.put("Rosca Martelo", "https://www.youtube.com/watch?v=zC3nLlEvin4");
        map.put("Tríceps Testa", "https://www.youtube.com/watch?v=X-iV-zZ0U8A");

        // --- MOBILIDADE, REABILITAÇÃO E CORE ---
        map.put("Dead Bug", "https://www.youtube.com/watch?v=g_BYB0R-4Ws");
        map.put("Cat Cow", "https://www.youtube.com/watch?v=kqnua4rHVSk");
        map.put("Clamshell", "https://www.youtube.com/watch?v=2vY6WlE_rP8");
        map.put("Y-W-T", "https://www.youtube.com/watch?v=pAnu71Yn-m0");
        map.put("Rotação Externa", "https://www.youtube.com/watch?v=P6mmD8X_G_U");
        map.put("Prancha Abdominal", "https://www.youtube.com/watch?v=ASdvN_XEl_c");
        map.put("Bird Dog", "https://www.youtube.com/watch?v=wiF97vdtZno");
        map.put("Mobilidade Tornozelo", "https://www.youtube.com/watch?v=I72XInT1Wp0");

        VIDEO_MAP = Collections.unmodifiableMap(map);
    }

    public static String getVideoUrl(String exerciseName) {
        return Optional.ofNullable(exerciseName)
                .map(String::trim)
                .map(VIDEO_MAP::get)
                .orElseGet(() -> {
                    String query = exerciseName != null ? exerciseName.trim().replace(" ", "+") : "";
                    return "https://www.youtube.com/results?search_query=" + query;
                });
    }
}