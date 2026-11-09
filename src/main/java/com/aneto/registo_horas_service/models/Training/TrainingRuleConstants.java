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

        // --- Treino Convencional ---
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
        map.put("dips / paralelas", "Dips");
        map.put("stiff", "Stiff");
        map.put("rdl", "Stiff");
        map.put("stiff / rdl", "Stiff");
        map.put("elevações", "Pull-ups");
        map.put("pull-ups", "Pull-ups");
        map.put("elevações / pull-ups", "Pull-ups");
        map.put("lunge", "Lunge");
        map.put("afundo", "Lunge");
        map.put("lunge / afundo", "Lunge");

        // --- Mapeamentos e Aliases para Normalização ---
        map.put("push-up com pés elevados", "Flexões com Mãos Elevadas");
        map.put("push up com pés elevados", "Flexões com Mãos Elevadas");
        map.put("puxada à frente na polia", "Puxada à Frente");
        map.put("puxada com barra na polia", "Puxada à Frente");
        map.put("crucifixo invertido com halteres", "Crucifixo Invertido com Halteres no Banco");
        map.put("step up com halteres", "Step Up");
        map.put("cadeira flexora", "Cadeira Flexora");
        map.put("stiff com halteres", "Stiff com Halteres");
        map.put("lunge com halteres", "Lunge");
        map.put("elevação pélvica unipodal", "Ponte Unipodal");
        map.put("elevação pélvica com halteres em decúbito superior", "Elevação Pélvica com Halteres");
        map.put("abdução de anca em pé", "Abdução de Anca em Pé na Polia");
        map.put("hip thrust com halteres", "Elevação Pélvica com Halteres");
        map.put("hip thrust", "Elevação Pélvica");
        map.put("deadlift unilateral", "Stiff Unilateral");

        // --- Exercícios Clínicos & Reabilitação (IDs 311 - 330) ---
        map.put("descompressão lombo-pélvica", "Descompressão Lombo-Pélvica em Supino");
        map.put("descompressão lombar", "Descompressão Lombo-Pélvica em Supino");
        map.put("deslizamento neural femoral", "Deslizamento Neural do Nervo Femoral");
        map.put("neurodinâmica femoral", "Deslizamento Neural do Nervo Femoral");
        map.put("deslizamento neural ciático", "Deslizamento Neural do Nervo Ciático");
        map.put("neurodinâmica ciática", "Deslizamento Neural do Nervo Ciático");
        map.put("ativação escapular", "Libertação e Ativação Escapular com Banda");
        map.put("retração escapular", "Retração Escapular em Decúbito Ventral");
        map.put("ponte pélvica isométrica", "Ponte Pélvica com Isometria");
        map.put("basculação pélvica", "BASCULAÇÃO PÉLVICA EM SUPINO");
        map.put("extensão torácica rolo", "Extensão Torácica no Rolo (Foam Roller)");
        map.put("mobilidade 90/90", "Mobilidade de Anca em 90/90 com Rotação");
        map.put("ativação vasto medial", "Ativação de Vasto Medial Unilateral");
        map.put("prancha joelhos", "Prancha Abdominal com Apoio de Joelhos");
        map.put("dorsiflexão passiva", "Dorsiflexão Passiva de Tornozelo");
        map.put("alongamento flexores anca", "Alongamento Dinâmico de Flexores de Anca");
        map.put("isquiotibiais em supino", "Alongamento Ativo de Isquiotibiais em Supino");
        map.put("clamshell 90", "Abdução de Anca em Decúbito Lateral (Clamshell 90º)");
        map.put("rotação interna ombro", "Rotação Interna de Ombro com Elástico");
        map.put("press pallof isométrico", "Press Pallof Isométrico");
        map.put("quadrado lombar", "Alongamento do Quadrado Lombar em Cadeira");
        map.put("passar a agulha", "Thread the Needle (Passar a Agulha)");
        map.put("thread the needle", "Thread the Needle (Passar a Agulha)");
        map.put("estabilização lombar", "Estabilização Segmentar Lombar");

        EXERCISE_MAP = Collections.unmodifiableMap(map);
    }

    public static final Map<String, java.util.Set<String>> GRUPO_MUSCULAR_KEYWORDS = Map.of(
            "PEITO", java.util.Set.of("peito", "supino", "crossover", "peck", "flex", "push-up", "push up", "dips", "chest"),
            "COSTAS", java.util.Set.of("costas", "dorsal", "remada", "puxada", "pull", "pulldown", "pullover"),
            "PERNAS", java.util.Set.of("pernas", "perna", "quadríceps", "quadriceps", "isquio", "gluteo", "glúteo", "agachamento", "leg", "coxa", "anca", "tornozelo", "gémeo", "gemeo", "thrust", "deadlift", "lunge"),
            "OMBROS", java.util.Set.of("ombro", "deltoide", "lateral", "desenvolvimento", "arnold", "press"),
            "BRAÇOS", java.util.Set.of("braço", "braco", "tríceps", "triceps", "bíceps", "biceps", "rosca", "curl"),
            "REABILITAÇÃO", java.util.Set.of("reabilitação", "reabilitacao", "descompressão", "descompressao", "neurodinamica", "escapular", "isometria", "dorsiflexão", "estabilização"),
            "MOBILIDADE", java.util.Set.of("mobilidade", "rotação", "90/90", "foam roller", "needle", "stretch", "cat cow"),
            "ALONGAMENTO", java.util.Set.of("alongamento", "flexores", "isquiotibiais", "pombo", "borboleta", "quadrado lombar")
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
            Map.entry("Frankenstein Walk", java.util.Set.of("PERNAS")),
            Map.entry("Thread the Needle (Passar a Agulha)", java.util.Set.of("COSTAS", "OMBROS")),
            Map.entry("Mobilidade de Anca em 90/90 com Rotação", java.util.Set.of("PERNAS")),
            Map.entry("Extensão Torácica no Rolo (Foam Roller)", java.util.Set.of("COSTAS"))
    );

    public static final Map<String, String> MOBILIDADE_ESPECIFICA_PARA_PATOLOGIA = Map.of(
            "Rotação Externa", "ombro",
            "Clamshell", "joelho",
            "Libertação e Ativação Escapular com Banda", "ombro",
            "Descompressão Lombo-Pélvica em Supino", "lombar",
            "Deslizamento Neural do Nervo Femoral", "lombar"
    );

    public static final Map<String, List<String>> FALLBACK_DICTIONARY;
    static {
        Map<String, List<String>> map = new LinkedHashMap<>();

        map.put("PEITO", List.of(
                "Bench Press Halters", "Supino Inclinado Barra", "Peck Deck", "Flexões",
                "Dumbbell Flyes", "Chest Press Bar", "Cruxifixo na polia baixa", "PullOver chest",
                "Flexões Diamond", "Supino Inclinado com Halteres em Banco 30º", "Crossover Low to High Pulley",
                "Chest Press Machine", "Svend Press com Discos", "Top-Down Crossover", "Flexões com Mãos Elevadas",
                "Chest Press Smith", "Decline Bench Bar", "Decline dumbbell press", "Supino declinado Maquina","Bench Press Smith"
        ));

        map.put("COSTAS", List.of(
                "Puxada à Frente", "Remada Curvada", "Remada Unilateral", "Pulldown Corda",
                "Remada Baixa", "Pull-ups", "Puxada Neutra", "Remada Cavalinho", "Face Pull Pronado",
                "Remada Serrote", "PullOver back", "Puxada Pega Aberta", "Puxada Unilateral na Polia",
                "Remada Curvada com Halteres", "Remada T-Bar", "Remada Máquina", "Remada Invertida",
                "Encolhimentos com Halteres", "Extensão Lombar na Máquina", "Hiperextensão Lombar",
                "Puxada ao Peito com Pegada Invertida", "Puxada Unilateral articulada", "Remada Meadows com Halter",
                "Remada no Banco Inclinado com Halteres", "Remada Pendlay", "Good Morning com Barra Z",
                "Extensão de Anca em Banco GHD", "Puxada Assistida (Pull-up Assistido)", "Remada Chest Supported", "Superman"
        ));

        map.put("PERNAS", List.of(
                "Agachamento Livre", "Leg Press 45", "Cadeira Extensora", "Mesa Flexora",
                "Stiff / RDL", "Gémeos em Pé", "Lunge / Afundo", "Elevação Pélvica", "Agachamento Goblet",
                "Agachamento Búlgaro", "Glute Bridge", "Stiff Unilateral", "Lunge Reverso", "Lunge Lateral",
                "Step Up", "Cadeira Flexora em Pé", "Gémeos Sentado", "Good Morning", "Elevação Pélvica na Máquina",
                "Coice de Glúteo na Polia", "Coice de Glúteo na Máquina", "Abdução de Anca em Pé na Polia",
                "Adução de Anca em Pé na Polia", "Cadeira Adutora", "Cadeira Abdutora", "Agachamento Sumô", "Hack Squat", "Gémeos na maquina"
        ));

        map.put("OMBROS", List.of(
                "Desenvolvimento com Halteres", "Elevação Lateral", "Face Pull", "Elevação Frontal Halteres",
                "Arnold Press", "Elevação Lateral Unilateral Polia", "Elevação Lateral sentado", "Desenvolvimento Máquina",
                "Remada Alta com Barra", "Crucifixo Invertido na Máquina", "Encolhimentos com Barra",
                "Desenvolvimento Militar com Barra", "Landmine Press Unilateral", "Elevação Lateral no Banco Inclinado",
                "Elevação Lateral Y-Raise na Polia", "Crucifixo Invertido com Halteres no Banco", "Face Pull com Pegada Supinada",
                "Lateral Raise on the Pulley", "Elevação Lateral Unilateral Polia Inclinado", "Push Press"
        ));

        map.put("BRAÇOS", List.of(
                "CURL / Bicep com Barra Directa", "Tríceps na Polia com Corda", "CURL Martelo com Halteres",
                "Tríceps Testa Deitado com Halteres", "CURL Concentrado", "Tríceps na Polia com Barra",
                "CURL / Bicep com Halteres", "CURL / Bicep na Máquina", "Tríceps Francês com Halter",
                "Tríceps Francês na Polia", "Tríceps Testa no Banco Inclinado", "Curl Inclinado com Halteres",
                "Curl Spider com Barra Z", "Curl Preacher Unilateral na Polia", "Curl Inverso com Barra Z",
                "Tríceps Coice Unilateral na Polia", "Tríceps JM Press com Barra", "Dips no Banco com Carga",
                "Curl 21 com Barra", "Rosca Scott com Halteres", "Zottman Curl", "Skull Crusher na Polia", "Rosca de Punho"
        ));

        map.put("CORE", List.of(
                "Crunch Abdominal", "Crunch na Polia", "Bicycle Crunch", "Reverse Crunch", "V-Up",
                "Flutter Kicks", "Elevação de Joelhos Suspenso", "Windshield Wipers", "Woodchopper na Polia",
                "Landmine Rotation", "Copenhagen Plank", "Suitcase Carry", "Body Saw", "Prancha com Elevação de Perna",
                "Dead Bug", "Prancha Abdominal", "Bird Dog", "Prancha Lateral", "Dead Bug com Carga",
                "Prancha com Toque no Ombro", "Russian Twist", "Elevação de Pernas Suspenso", "Thruster", "Pallof Press",
                "Abdominal Wheel Rollout", "Prancha RKC (Tensão Máxima)", "Hollow Body Hold", "Dragon Flag", "Prancha Abdominal com Apoio de Joelhos", "Press Pallof Isométrico"
        ));

        map.put("CARDIO", List.of(
                "High Knees", "Skater Jumps", "Tuck Jump", "Shuttle Run", "Corrida em Intervalos (HIIT)",
                "Bear Crawl", "Escadas", "Eliptica", "Passadeira", "bicicleta", "Remo", "Ski Erg",
                "Air Bike", "Saltar Corda", "Jumping Jacks", "Double Unders (Corda)", "Box Jump", "Assault Bike Sprints"
        ));

        map.put("REABILITAÇÃO", List.of(
                "Clamshell", "Rotação Externa", "Equilíbrio Unipodal", "Bird Dog com Rotação", "Ponte Unipodal",
                "Wall Slide (Ombro)", "Mobilização do Tornozelo em Carga", "Ativação de Glúteo Médio em Decúbito Lateral",
                "Descompressão Lombo-Pélvica em Supino", "Deslizamento Neural do Nervo Femoral", "Deslizamento Neural do Nervo Ciático",
                "Libertação e Ativação Escapular com Banda", "Retração Escapular em Decúbito Ventral", "Ponte Pélvica com Isometria",
                "BASCULAÇÃO PÉLVICA EM SUPINO", "Ativação de Vasto Medial Unilateral", "Dorsiflexão Passiva de Tornozelo",
                "Abdução de Anca em Decúbito Lateral (Clamshell 90º)", "Rotação Interna de Ombro com Elástico", "Estabilização Segmentar Lombar"
        ));

        map.put("MOBILIDADE", List.of(
                "Cat Cow", "Y-W-T", "Knee-to-Wall", "Open Books", "Mobilidade Torácica em Quatro Apoios",
                "Rotação de Tronco Deitado", "90/90 Hip Switch", "World's Greatest Stretch", "Cossack Squat",
                "Extensão Torácica no Rolo (Foam Roller)", "Mobilidade de Anca em 90/90 com Rotação", "Alongamento Dinâmico de Flexores de Anca",
                "Thread the Needle (Passar a Agulha)", "Mobilidade de Anca em Frog Pose", "Jefferson Curl", "Deep Squat Hold", "Rotação Torácica com Halter"
        ));

        map.put("ALONGAMENTO", List.of(
                "Flexores da Anca", "Alongamento borboleta", "Alongamento do músculo grande dorsal",
                "Walking Lunges with Reach", "Frankenstein Walk", "Alongamento de Isquiotibiais em Pé",
                "Alongamento Peitoral", "Alongamento de Flexores do Punho", "Alongamento de Trapézio",
                "Alongamento de Adutores", "Alongamento do Glúteo (Pombo)", "Alongamento do Quadrado Lombar em Cadeira",
                "Alongamento Ativo de Isquiotibiais em Supino", "Alongamento de Quadríceps Ajoelhado (Couch Stretch)"
        ));

        map.put("FINALIZADOR", List.of(
                "Medicine Ball Slam", "Kettlebell Snatch", "Kettlebell Clean and Press", "Sled Pull / Puxar Trenó",
                "Sandbag Carry", "Man Makers", "Tabata de Agachamento com Salto", "Burpees", "Mountain Climbers",
                "Kettlebell Swing", "Corda de Saltar", "Battle Rope", "Jump Squat", "Remo Curto Intervalado",
                "Circuito Metabólico AMRAP", "Sprint na Bike Estática", "Sled Push / Empurrar Trenó", "Farmer's Walk com Halteres",
                "Wall Balls", "Devil Press"
        ));

        FALLBACK_DICTIONARY = Collections.unmodifiableMap(map);
    }
}