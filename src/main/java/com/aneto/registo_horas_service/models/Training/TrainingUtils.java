package com.aneto.registo_horas_service.models.Training;

import com.aneto.registo_horas_service.dto.request.UserProfileRequest;
import com.aneto.registo_horas_service.dto.response.TrainingExercise;
import com.aneto.registo_horas_service.models.Enum;
import org.apache.commons.lang3.StringUtils;

import java.util.*;

public final class TrainingUtils {

    private TrainingUtils() {
    }

    public static String sanitizarExerciseHistory(String input) {
        return (input == null || input.isBlank()) ? "Nenhum histórico registado" : input.trim();
    }

    public static String defaultIfEmpty(String value, String defaultValue) {
        return (value == null || value.isBlank()) ? defaultValue : value;
    }

    public static int extrairMinutosTotais(String durationText) {
        if (durationText == null || durationText.isBlank()) return 60;
        try {
            String clean = durationText.toLowerCase().trim();
            if (clean.contains("h") || clean.contains(":")) {
                String[] parts = clean.split("[h:]");
                int horas = Integer.parseInt(parts[0].replaceAll("[^0-9]", ""));
                int minutos = (parts.length > 1 && !parts[1].isEmpty()) ? Integer.parseInt(parts[1].replaceAll("[^0-9]", "")) : 0;
                return (horas * 60) + minutos;
            }
            return Integer.parseInt(clean.replaceAll("[^0-9]", ""));
        } catch (Exception e) {
            return 60;
        }
    }

    public static String calcularDescansoCientifico(String objective, String cargaAtual) {
        double peso = 0;
        try {
            peso = Double.parseDouble(cargaAtual.replaceAll("[^0-9.]", ""));
        } catch (Exception ignored) {
        }

        if (objective.equalsIgnoreCase("Força")) return (peso > 80) ? "180" : "120";
        if (objective.equalsIgnoreCase("Hipertrofia")) return (peso > 50) ? "90" : "60";
        return "60";
    }

    public static String calcularDescansoDeload(Enum.TrainingProtocol protocol, String objective, String cargaAtual) {
        String base = calcularDescansoCientifico(objective, cargaAtual);
        try {
            int segundosBase = Integer.parseInt(base.replaceAll("[^0-9]", ""));
            return String.valueOf(segundosBase + 30);
        } catch (Exception e) {
            return "90";
        }
    }

    public static boolean isObjetivoCompativel(String objectiveText) {
        String objectiveLower = objectiveText.toLowerCase();
        return objectiveLower.contains("emagrec")
                || objectiveLower.contains("perda de gordura")
                || objectiveLower.contains("definição")
                || objectiveLower.contains("definicao")
                || objectiveLower.contains("condicionamento")
                || objectiveLower.contains("resistência")
                || objectiveLower.contains("resistencia")
                || objectiveLower.contains("hipertrofia");
    }

    public static boolean protocoloTemMetodologiaPropria(String protocolName) {
        return protocolName == null || (!protocolName.contains("NASM") && !protocolName.contains("FST7") && !protocolName.contains("GVT"));
    }

    public static Set<String> flattenDictionary(Map<String, List<String>> dictionary) {
        return dictionary.values().stream()
                .flatMap(List::stream)
                .collect(java.util.stream.Collectors.toCollection(HashSet::new));
    }

    public static String normalizeExerciseName(String aiSuggestion, Set<String> validNames) {
        String cleanSuggestion = aiSuggestion.trim().replaceAll("[.,!?]$", "");
        if (validNames.contains(cleanSuggestion)) return cleanSuggestion;

        String mapped = TrainingRuleConstants.EXERCISE_MAP.get(cleanSuggestion.toLowerCase());
        if (mapped != null && validNames.contains(mapped)) return mapped;

        String lower = cleanSuggestion.toLowerCase();
        String melhorMatch = null;
        int menorDistancia = Integer.MAX_VALUE;
        for (String valido : validNames) {
            int distancia = StringUtils.getLevenshteinDistance(lower, valido.toLowerCase());
            if (distancia < menorDistancia && distancia <= Math.max(3, valido.length() / 4)) {
                menorDistancia = distancia;
                melhorMatch = valido;
            }
        }
        return (melhorMatch != null) ? melhorMatch : cleanSuggestion;
    }

    public static String encontrarCategoria(String exerciseName, Map<String, List<String>> dictionary) {
        if (dictionary == null || exerciseName == null) return null;
        for (Map.Entry<String, List<String>> entry : dictionary.entrySet()) {
            if (entry.getValue().stream().anyMatch(e -> e.equalsIgnoreCase(exerciseName))) {
                return entry.getKey();
            }
        }
        return null;
    }

    public static String encontrarSubcategoria(String exerciseName, Map<String, Map<String, List<String>>> dictionaryComSub) {
        if (dictionaryComSub == null || exerciseName == null) return null;
        for (Map<String, List<String>> subs : dictionaryComSub.values()) {
            for (Map.Entry<String, List<String>> subEntry : subs.entrySet()) {
                if (subEntry.getValue().stream().anyMatch(e -> e.equalsIgnoreCase(exerciseName))) {
                    return subEntry.getKey();
                }
            }
        }
        return null;
    }

    public static String obterSubstitutoValido(String categoria, String subcategoria,
                                               Map<String, Map<String, List<String>>> dictComSub,
                                               Set<String> usadosNestaTentativa, Set<String> nomesAnteriores) {
        if (categoria == null || dictComSub == null) return null;
        Map<String, List<String>> subs = dictComSub.get(categoria);
        if (subs == null) return null;

        if (subcategoria != null && subs.containsKey(subcategoria)) {
            for (String opcao : subs.get(subcategoria)) {
                if (!usadosNestaTentativa.contains(opcao) && !nomesAnteriores.contains(opcao)) {
                    return opcao;
                }
            }
        }
        for (List<String> lista : subs.values()) {
            for (String opcao : lista) {
                if (!usadosNestaTentativa.contains(opcao) && !nomesAnteriores.contains(opcao)) {
                    return opcao;
                }
            }
        }
        return null;
    }

    public static Map<String, Map<String, List<String>>> envolverSemSubcategoria(Map<String, List<String>> simpleDict) {
        Map<String, Map<String, List<String>>> result = new HashMap<>();
        if (simpleDict == null) return result;
        for (Map.Entry<String, List<String>> entry : simpleDict.entrySet()) {
            Map<String, List<String>> subMap = new HashMap<>();
            subMap.put("GERAL", entry.getValue());
            result.put(entry.getKey(), subMap);
        }
        return result;
    }

    public static String listarExerciciosGluteo(Map<String, Map<String, List<String>>> dictComSub) {
        if (dictComSub == null) return "Elevação Pélvica, Agachamento Búlgaro, Coice de Glúteo, Abdução de Anca";
        Map<String, List<String>> pernasSub = dictComSub.get("PERNAS");
        if (pernasSub == null) return "Elevação Pélvica, Agachamento Búlgaro, Coice de Glúteo, Abdução de Anca";

        List<String> gluteos = new ArrayList<>();
        for (Map.Entry<String, List<String>> entry : pernasSub.entrySet()) {
            if (entry.getKey().toLowerCase().contains("glúteo") || entry.getKey().toLowerCase().contains("gluteo")) {
                gluteos.addAll(entry.getValue());
            }
        }
        return gluteos.isEmpty() ? "Elevação Pélvica, Agachamento Búlgaro, Coice de Glúteo, Abdução de Anca" : String.join(", ", gluteos);
    }

    public static void validarMobilidadeCondicional(String exerciseName, String pathologyText) {
        String palavraChave = TrainingRuleConstants.MOBILIDADE_ESPECIFICA_PARA_PATOLOGIA.get(exerciseName);
        if (palavraChave != null) {
            boolean temPatologiaAdequada = pathologyText != null && pathologyText.toLowerCase().contains(palavraChave);
            if (!temPatologiaAdequada) {
                throw new RuntimeException("O exercício \"" + exerciseName + "\" só pode ser prescrito para patologia na zona de " + palavraChave + ".");
            }
        }
    }

    public static String cleanMarkdown(String text) {
        if (text == null || text.isBlank()) return "{}";
        String cleaned = text.replaceAll("(?s)```json\\s*(.*?)\\s*```", "$1").trim();
        int firstBrace = cleaned.indexOf("{");
        int lastBrace = cleaned.lastIndexOf("}");
        cleaned = (firstBrace != -1 && lastBrace != -1) ? cleaned.substring(firstBrace, lastBrace + 1) : cleaned;
        return cleaned.replaceAll("(?<=:\\s?)(\\d+),(\\d+)(?=[,}\\s])", "$1.$2");
    }

    public static List<TrainingExercise> anexarEstimativaCalorica(List<TrainingExercise> enrichedExercises,
                                                                  Map<String, List<String>> dictionary,
                                                                  Double weightKg, int totalMinutosSessao) {
        if (enrichedExercises == null || enrichedExercises.isEmpty() || weightKg == null || weightKg <= 0) {
            return enrichedExercises;
        }

        double somaMet = 0;
        int contados = 0;
        for (TrainingExercise ex : enrichedExercises) {
            String categoria = encontrarCategoria(ex.getName(), dictionary);
            double met = (categoria != null && TrainingRuleConstants.MET_POR_CATEGORIA.containsKey(categoria.toUpperCase()))
                    ? TrainingRuleConstants.MET_POR_CATEGORIA.get(categoria.toUpperCase())
                    : TrainingRuleConstants.MET_DEFAULT;
            somaMet += met;
            contados++;
        }
        if (contados == 0) return enrichedExercises;

        double metMedio = somaMet / contados;
        int kcalEstimadas = (int) Math.round(metMedio * 3.5 * weightKg / 200.0 * totalMinutosSessao);

        int ultimoIndex = enrichedExercises.size() - 1;
        TrainingExercise ultimo = enrichedExercises.get(ultimoIndex);
        String notaOriginal = (ultimo.getNotas() == null || ultimo.getNotas().isBlank()) ? "" : ultimo.getNotas().trim() + " ";
        String notaComEstimativa = notaOriginal + "Estimativa de gasto calórico desta sessão: ~" + kcalEstimadas + " kcal.";

        TrainingExercise atualizado = ultimo.toBuilder().notas(notaComEstimativa).build();
        List<TrainingExercise> resultado = new ArrayList<>(enrichedExercises);
        resultado.set(ultimoIndex, atualizado);
        return resultado;
    }

    public static String garantirFechoMotivacional(String summary, UserProfileRequest request, boolean isDeloadWeek, int weekNumber) {
        String base = (summary == null || summary.isBlank()) ? "" : summary.trim();
        String fecho = construirFechoMotivacional(request, isDeloadWeek, weekNumber);
        return base.isEmpty() ? fecho : base + " " + fecho;
    }

    private static String construirFechoMotivacional(UserProfileRequest request, boolean isDeloadWeek, int weekNumber) {
        String nome = (request.getStudentName() != null && !request.getStudentName().isBlank()) ? request.getStudentName() : null;
        String vocativo = nome != null ? nome + ", " : "";
        String objectiveLower = defaultIfEmpty(request.getObjective(), "").toLowerCase();

        if (isDeloadWeek) {
            return vocativo + "esta semana de recuperação é o que vai permitir que o teu corpo absorva todo o trabalho das últimas " + Math.max(1, weekNumber - 1) + " semanas — não saltes os dias de descanso.";
        }
        if (objectiveLower.contains("glúteo") || objectiveLower.contains("gluteo")) {
            return vocativo + "cada agachamento e elevação pélvica bem executados hoje são investimento direto no resultado que procuras nos glúteos.";
        }
        if (objectiveLower.contains("hipertrofia")) {
            return vocativo + "cada série perto da falha controlada é um estímulo direto para o crescimento muscular que procuras — mantém a técnica e o foco.";
        }
        if (objectiveLower.contains("emagrec") || objectiveLower.contains("perda de gordura") || objectiveLower.contains("definição") || objectiveLower.contains("definicao")) {
            return vocativo + "a consistência nestes treinos, semana após semana, é o que realmente separa quem atinge o objetivo de emagrecimento de quem desiste a meio.";
        }
        return vocativo + "mantém o foco no objetivo de " + defaultIfEmpty(request.getObjective(), "saúde e bem-estar") + " — a consistência, sessão após sessão, é o que faz a diferença.";
    }
}