package com.aneto.registo_horas_service.mapper;

import java.util.HashMap;
import java.util.Map;

public class ExerciseVideoMapper {

    private static final Map<String, String> VIDEO_MAP = new HashMap<>();

    static {
        // PEITO
        VIDEO_MAP.put("Supino Plano", "https://www.youtube.com/watch?v=tuwHzzStWsc");
        VIDEO_MAP.put("Supino Inclinado", "https://www.youtube.com/watch?v=8iP6v_AseD0");
        VIDEO_MAP.put("Peck Deck", "https://www.youtube.com/watch?v=O-59BvVvS8E");
        VIDEO_MAP.put("Crossover", "https://www.youtube.com/watch?v=H75t3r_A48E");

        // COSTAS
        VIDEO_MAP.put("Puxada à Frente", "https://www.youtube.com/watch?v=CAwf7n6Luuc");
        VIDEO_MAP.put("Remada Curvada", "https://www.youtube.com/watch?v=6FZHJGzMFEc");
        VIDEO_MAP.put("Remada Unilateral", "https://www.youtube.com/watch?v=dFzUjzfih74");
        VIDEO_MAP.put("Pulldown Corda", "https://www.youtube.com/watch?v=vV77G_tqYpE");

        // PERNAS
        VIDEO_MAP.put("Agachamento Livre", "https://www.youtube.com/watch?v=U3HlEF_E9fo");
        VIDEO_MAP.put("Leg Press 45", "https://www.youtube.com/watch?v=iz86pW-K0w0");
        VIDEO_MAP.put("Cadeira Extensora", "https://www.youtube.com/watch?v=m0FOpMEgero");
        VIDEO_MAP.put("Mesa Flexora", "https://www.youtube.com/watch?v=uKEnA902C0k");
        VIDEO_MAP.put("Stiff / RDL", "https://www.youtube.com/watch?v=hCDzSR6bW10");
        VIDEO_MAP.put("Gémeos em Pé", "https://www.youtube.com/watch?v=N_69o_A7t2o");
        VIDEO_MAP.put("Mobilidade Tornozelo", "https://www.youtube.com/watch?v=I72XInT1Wp0");

        // OMBROS E BRAÇOS
        VIDEO_MAP.put("Desenvolvimento", "https://www.youtube.com/watch?v=HzIi6mH8BCY");
        VIDEO_MAP.put("Elevação Lateral", "https://www.youtube.com/watch?v=3VcKaXpzqRo");
        VIDEO_MAP.put("Face Pull", "https://www.youtube.com/watch?v=rep-qVOkqgk");
        VIDEO_MAP.put("Rosca Direta", "https://www.youtube.com/watch?v=yTWO2th-RIY");
        VIDEO_MAP.put("Tríceps Corda", "https://www.youtube.com/watch?v=vB5OHsJ3EME");

        // REABILITAÇÃO E CORE
        VIDEO_MAP.put("Dead Bug", "https://www.youtube.com/watch?v=g_BYB0R-4Ws");
        VIDEO_MAP.put("Cat Cow", "https://www.youtube.com/watch?v=kqnua4rHVSk");
        VIDEO_MAP.put("Clamshell", "https://www.youtube.com/watch?v=2vY6WlE_rP8");
        VIDEO_MAP.put("Y-W-T", "https://www.youtube.com/watch?v=pAnu71Yn-m0");
        VIDEO_MAP.put("Rotação Externa", "https://www.youtube.com/watch?v=P6mmD8X_G_U");
    }

    /**
     * Retorna a URL do vídeo com base no nome exato do exercício.
     * Se não encontrar, retorna uma pesquisa genérica no YouTube.
     */
    public static String getVideoUrl(String exerciseName) {
        if (exerciseName == null || exerciseName.isEmpty()) return "";

        return VIDEO_MAP.getOrDefault(
                exerciseName.trim(),
                "https://www.youtube.com/results?search_query=" + exerciseName.replace(" ", "+")
        );
    }
}