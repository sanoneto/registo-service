package com.aneto.registo_horas_service.models.Training;

public enum EnumExerciseCategory {
    // PEITORAL
    SUPINO_PLANO("Supino Plano", "bench+press+3d+anatomy"),
    SUPINO_INCLINADO("Supino Inclinado", "incline+dumbbell+press+3d+anatomy"),
    PECK_DECK("Peck Deck", "peck+deck+3d+anatomy"),
    CROSSOVER("Crossover", "cable+crossover+3d+anatomy"),
    PULLOVER_PEITO("Pullover (FST-7)", "dumbbell+pullover+3d+anatomy"),

    // COSTAS
    PUXADA_ABERTA("Puxada à Frente", "lat+pulldown+3d+anatomy"),
    REMADA_CURVADA("Remada Curvada", "bent+over+row+3d+anatomy"),
    REMADA_SERROTE("Remada Unilateral", "dumbbell+row+3d+anatomy"),
    PULLDOWN_CORDA("Pulldown Corda", "straight+arm+pulldown+3d+anatomy"),

    // PERNAS
    AGACHAMENTO_LIVRE("Agachamento Livre", "back+squat+3d+anatomy"),
    LEG_PRESS("Leg Press 45", "leg+press+3d+anatomy"),
    CADEIRA_EXTENSORA("Cadeira Extensora", "leg+extension+3d+anatomy"),
    MESA_FLEXORA("Mesa Flexora", "lying+leg+curl+3d+anatomy"),
    STIFF("Stiff / RDL", "romanian+deadlift+3d+anatomy"),
    GÊMEOS_EM_PÉ("Gémeos em Pé", "standing+calf+raise+3d+anatomy"),

    // OMBROS
    DESENVOLVIMENTO_MILITAR("Desenvolvimento", "overhead+press+3d+anatomy"),
    ELEVAÇÃO_LATERAL("Elevação Lateral", "lateral+raise+3d+anatomy"),
    FACE_PULL("Face Pull", "face+pull+3d+anatomy"),

    // BRAÇOS
    ROSCA_DIRETA("Rosca Direta", "biceps+curl+3d+anatomy"),
    ROSCA_MARTELO("Rosca Martelo", "hammer+curl+3d+anatomy"),
    TRICEPS_CORDA("Tríceps Corda", "triceps+pushdown+3d+anatomy"),
    TRICEPS_TESTA("Tríceps Testa", "skull+crusher+3d+anatomy"),

    //MOBILIDADE E REABILITAÇÃO
    // --- MOBILIDADE E REABILITAÇÃO ---
    YWT_RETRACCAO("Y-W-T", "scapular+ywt+exercise+3d+anatomy"),
    ROTACAO_EXTERNA_OMBRO("Rotação Externa", "shoulder+external+rotation+3d+anatomy"),
    DEAD_BUG("Dead Bug", "dead+bug+exercise+3d+anatomy"),
    CAT_COW("Cat Cow", "cat+cow+stretch+3d+anatomy"),
    CLAMSHELL("Clamshell", "clamshell+exercise+3d+anatomy"),
    MOBILIDADE_TORNOZELO("Mobilidade Tornozelo", "ankle+mobility+3d+anatomy");

    private final String displayName;
    private final String searchTerms;
    private static final String BASE_URL = "https://www.youtube.com/results?search_query=";

    EnumExerciseCategory(String displayName, String searchTerms) {
        this.displayName = displayName;
        this.searchTerms = searchTerms;
    }

    public String getAnatomyLink() {
        return BASE_URL + this.searchTerms;
    }

    // Método estático para encontrar o link pelo nome que a IA gerar
    public static String findLinkByExerciseName(String exerciseName) {
        if (exerciseName == null) return "";
        String normalizedInput = exerciseName.toLowerCase().replace(" ", "").replace("-", "");

        for (EnumExerciseCategory exercise : values()) {
            String enumName = exercise.displayName.toLowerCase().replace(" ", "");
            String enumConst = exercise.name().toLowerCase().replace("_", "");

            if (normalizedInput.contains(enumName) || normalizedInput.contains(enumConst)) {
                return exercise.getAnatomyLink();
            }
        }
        // Fallback: Se não estiver no dicionário, cria uma busca genérica
        return BASE_URL + exerciseName.replace(" ", "+") + "+technique";
    }
}