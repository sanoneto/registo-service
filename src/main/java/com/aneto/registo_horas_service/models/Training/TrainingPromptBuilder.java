package com.aneto.registo_horas_service.models.Training;

import com.aneto.registo_horas_service.dto.request.UserProfileRequest;
import com.aneto.registo_horas_service.dto.response.Macros;
import com.aneto.registo_horas_service.dto.response.MealSuggestion;
import com.aneto.registo_horas_service.models.Enum;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.aneto.registo_horas_service.models.Training.TrainingRuleConstants.CARDIO_OBRIGATORIO_POR_DIA_CORE;
import static com.aneto.registo_horas_service.models.Training.TrainingUtils.*;

public class TrainingPromptBuilder {

    public String buildUserPrompt(UserProfileRequest userRequest,
                                  int weekNumber,
                                  boolean isDeloadWeek,
                                  int volumeIdeal,
                                  int totalMinutos,
                                  String pathologyText,
                                  boolean pathologyEspecifica,
                                  Macros macros,
                                  Map<String, List<String>> exerciseDictionary,
                                  Map<String, Map<String, List<String>>> exerciseDictionaryComSub,
                                  List<String> exerciciosDoS3) {

        int dias = userRequest.getFrequencyPerWeek();
        String exerciseHistoryText = sanitizarExerciseHistory(userRequest.getExerciseHistory());
        String objectiveText = defaultIfEmpty(userRequest.getObjective(), "Manutenção de saúde e bem-estar");
        String locationText = defaultIfEmpty(userRequest.getLocation(), "Não especificada");
        String bodyTypeText = (userRequest.getBodyType() != null) ? userRequest.getBodyType().name() : "ECTOMORPH";
        String genderText = (userRequest.getGender() != null) ? userRequest.getGender().name() : "MALE";
        String weightKg = defaultIfEmpty(String.valueOf(userRequest.getWeightKg()), "70");
        String durationText = defaultIfEmpty(userRequest.getDurationPerSession(), "60 minutos");

        String protocolId = (userRequest.getProtocol() == null) ? "nasm_estabilizacao" : userRequest.getProtocol();
        Enum.TrainingProtocol protocol = Enum.TrainingProtocol.fromId(protocolId);

        boolean isGluteFocus = objectiveText.toLowerCase().contains("glúteo") || objectiveText.toLowerCase().contains("gluteo");
        boolean aplicaFocoGluteoExtremo = isGluteFocus && protocoloTemMetodologiaPropria(String.valueOf(protocol));
        boolean isSedentary = "sedentary".equalsIgnoreCase(userRequest.getExerciseHistory());

        String tempoNASM = protocolId.contains("estabilização") ? "4-2-1" : "2-0-2";
        String listaParaEvitar = (exerciciosDoS3 == null || exerciciosDoS3.isEmpty()) ? "Nenhum (Primeiro plano do aluno)" : String.join(", ", exerciciosDoS3);

        String diretrizFocoEspecial = buildDiretrizFocoEspecial(aplicaFocoGluteoExtremo, isGluteFocus, protocol, exerciseDictionaryComSub);
        String diretrizVariedade = """
                [SISTEMA DE VARIAÇÃO ANTI-PLATÔ]
                - EXERCÍCIOS JÁ REALIZADOS (PROIBIDO REPETIR): %s.
                - REGRA DE OURO: É estritamente proibido repetir o mesmo exercício em dias diferentes deste plano.
                - TEMA: Focar em variações biomecânicas.
                - RITMO OBRIGATÓRIO: O campo 'tempo' no JSON deve ser rigorosamente '%s'.
                """.formatted(listaParaEvitar, tempoNASM);

        String protocoloEfetivo = isSedentary ? "Adaptação Anatómica (Baixa Intensidade)" : (isDeloadWeek ? "Deload (Redução Programada de Carga - Semana " + weekNumber + ")" : protocol.getLabel());
        String repsEfetivas = isSedentary ? "12 a 15 (longe da falha)" : protocol.getReps();
        String setsEfetivas = (isSedentary || isDeloadWeek) ? "2" : protocol.getSets();
        String descansoEfetivo = isDeloadWeek ? calcularDescansoDeload(protocol, objectiveText, weightKg) : calcularDescansoCientifico(objectiveText, weightKg);

        String diretrizSegurancaIniciante = isSedentary ? "[ALERTA DE SEGURANÇA: ALUNO SEDENTÁRIO]\n- O aluno nunca treinou. É TERMINANTEMENTE PROIBIDO levar à falha concêntrica.\n- Prioridade: Estabilidade hemodinâmica. Não usar superséries.\n" : "";
        String diretrizPeriodizacao = isDeloadWeek ? "[SEMANA DE DELOAD - RECUPERAÇÃO PROGRAMADA (Semana %d do ciclo)]\n- VOLUME: gera EXATAMENTE %d exercícios por dia.\n- INTENSIDADE: RPE máximo 5-6 em todas as séries.\n".formatted(weekNumber, volumeIdeal) : "";

        String detalhesEquipamento = locationText.equalsIgnoreCase("Casa") ? "UTILIZA APENAS: 'Peso Corporal', 'Halteres' ou 'Bandas Elásticas'." : "UTILIZA: 'Máquinas', 'Barras', 'Polias' ou 'Halteres'.";
        String filtroEquipamento = isSedentary ? "PREFERÊNCIA OBRIGATÓRIA: Máquinas guiadas para maior controlo motor e segurança." : detalhesEquipamento;

        String diretrizProtocolo = "DIRETRIZES TÉCNICAS E FISIOLÓGICAS (%s):\n- Séries: %s | Repetições: %s\n- DESCANSO CIENTÍFICO: %s segundos fixos.\n- RITMO (Tempo): %s\n".formatted(protocoloEfetivo, setsEfetivas, repsEfetivas, descansoEfetivo, protocol.getTempo());
        String diretrizBiomecanica = "DIRETRIZES DE SELEÇÃO BIOMECÂNICA ESTREITAS:\n1. REPERTÓRIO: %s.\n2. PROIBIDOS: %s.\n3. ADAPTAÇÃO: Patologia \"%s\".\n".formatted(protocol.getSuggestedExercises(), protocol.getForbiddenExercises(), pathologyText);

        String diretrizAquecimento = buildDiretrizAquecimento(pathologyEspecifica, pathologyText);
        String diretrizTreino = buildDiretrizTreino(userRequest, durationText, volumeIdeal, aplicaFocoGluteoExtremo, isGluteFocus);
        String diretrizAlimentar = buildDiretrizAlimentar(macros);

        boolean permiteFinalizador = !isSedentary && !pathologyEspecifica && !isDeloadWeek && isObjetivoCompativel(objectiveText) && totalMinutos >= 40;
        String diretrizFinalizador = permiteFinalizador ? "[FINALIZADOR METABÓLICO/ANAERÓBIO]\n- Adiciona UM exercício FINAL de condicionamento metabólico em cada dia, na categoria FINALIZADOR.\n" : "";

        String diretrizNomenclaturaDias = buildDiretrizNomenclaturaDias(userRequest, isGluteFocus, aplicaFocoGluteoExtremo, pathologyText);
        String diretrizCardioCore = buildDiretrizCardioCore(volumeIdeal);
        String diretrizDicionario = buildDiretrizDicionario(exerciseDictionary);
        String diretrizRelatorioMedico = buildDiretrizRelatorioMedico(userRequest.getMedicalReportText(), pathologyText);

        String blocoDiretrizesCompletas = Stream.of(
                        diretrizFocoEspecial, diretrizVariedade, diretrizAquecimento,
                        diretrizSegurancaIniciante, diretrizProtocolo, diretrizBiomecanica,
                        diretrizTreino, diretrizFinalizador, diretrizPeriodizacao,
                        diretrizNomenclaturaDias, diretrizCardioCore, diretrizDicionario,
                        diretrizAlimentar, diretrizRelatorioMedico
                )
                .filter(s -> s != null && !s.isBlank())
                .collect(Collectors.joining("\n"));

        // 1. Regras Críticas de Fechamento com o número de dias explícito
        String regrasFinais = "REGRAS CRÍTICAS DE FECHAMENTO:\n1. FREQUÊNCIA COMPLETA OBRIGATÓRIA: Gera exatamente %d objetos dentro do array 'plan' (Dia 1 até Dia %d).\n2. DURAÇÃO: O treino deve durar %d minutos. Gera EXATAMENTE %d exercícios por dia.\n3. RITMO E DESCANSO: Ritmo %s e Descanso %s segundos.\n4. TOTAIS DIETA: %d kcal, %dg Prot, %dg Carbs, %dg Fats.\n"
                .formatted(dias, dias, totalMinutos, volumeIdeal, protocol.getTempo(), descansoEfetivo, macros.dailyCalories(), macros.protein(), macros.carbs(), macros.fats());

        // 2. Construção dinâmica da lista de dias para o JSON de exemplo
        StringBuilder jsonDaysExample = new StringBuilder();
        for (int i = 1; i <= dias; i++) {
            jsonDaysExample.append(String.format("""
                    {
                    "day": "Dia %d - [CATEGORIA]: [FOCO]",
                    "exercises": [
                        {
                        "order": 1, "name": "...",
                        "muscleGroup": "...", "movementPlane": "...", "equipment": "...",
                        "tempo": "%s", "sets": "3", "reps": "15", "rest": "%s",
                        "weight": "0kg", "cargaAtual": "...", "videoUrl": "",
                        "details": "Instrução técnica biomecânica.", "notas": "Pista Mental.", "date": "Data"
                        }
                      ]
                    }%s""", i, protocol.getTempo(), descansoEfetivo, (i < dias ? ",\n    " : "")));
        }

        return """
                ATUAÇÃO: Personal Trainer e Nutricionista Profissional (Portugal).
                PERFIL: %d anos, %s, %s, %.2fkg. Objetivo: %s. Patologias: %s.
                
                SISTEMA DE REGRAS TÉCNICAS:
                %s
                
                %s
                
                FORMATO JSON OBRIGATÓRIO (DEVE CONTER EXACTAMENTE %d DIAS NO ARRAY 'plan'):
                {
                "summary": "Explicação técnica da estratégia %s.",
                "plan": [
                    %s
                ],
                "dietPlan": {
                    "dailyCalories": %d, "imc": %.2f, "imcCategory": "%s",
                    "macroDistribution": { "protein": "%dg", "carbs": "%dg", "fats": "%dg" },
                    "meals": [{"time": "HH:mm", "description": "...", "ingredients": ["..."], "calories": 0, "protein": 0, "carbs": 0, "fats": 0}]
                  }
                }
                """.formatted(
                userRequest.getAge(), bodyTypeText, genderText, userRequest.getWeightKg(), objectiveText, pathologyText,
                blocoDiretrizesCompletas, regrasFinais, dias, protocol.getLabel(), jsonDaysExample.toString(),
                macros.dailyCalories(), macros.imc(), macros.imcCategory(), macros.protein(), macros.carbs(), macros.fats()
        );
    }

    private String buildDiretrizFocoEspecial(boolean aplicaFocoGluteoExtremo, boolean isGluteFocus, Enum.TrainingProtocol protocol, Map<String, Map<String, List<String>>> dictComSub) {
        if (aplicaFocoGluteoExtremo) {
            return """
                    [FOCO PRIORITÁRIO EXTREMO: GLÚTEOS]
                    - O aluno pediu foco quase exclusivo em Glúteos.
                    - EXERCÍCIOS DISPONÍVEIS PARA ESTE FOCO: %s.
                    """.formatted(listarExerciciosGluteo(dictComSub));
        } else if (isGluteFocus) {
            return "[FOCO EM GLÚTEOS — dentro da metodologia do protocolo %s]\n".formatted(protocol.getLabel());
        }
        return "";
    }

    private String buildDiretrizAquecimento(boolean pathologyEspecifica, String pathologyText) {
        return pathologyEspecifica ?
                "[REGRA OBRIGATÓRIA DE AQUECIMENTO] O aluno TEM patologia (\"%s\"). Order 1 deve ser Reabilitação/Mobilidade focado nessa área.\n".formatted(pathologyText) :
                "[REGRA OBRIGATÓRIA DE AQUECIMENTO] Order 1 deve ser Mobilidade Geral do dicionário.\n";
    }

    private String buildDiretrizTreino(UserProfileRequest userRequest, String durationText, int volumeIdeal, boolean aplicaFocoGluteoExtremo, boolean isGluteFocus) {
        List<String> sequencia = gerarSequenciaDivisao(userRequest.getFrequencyPerWeek(), isGluteFocus, aplicaFocoGluteoExtremo);
        return "REGRAS DE DIVISÃO: " + String.join(" -> ", sequencia) + " | Duração: " + durationText + " | Volume: " + volumeIdeal + " ex/dia.";
    }

    private String buildDiretrizNomenclaturaDias(UserProfileRequest userRequest, boolean isGluteFocus, boolean aplicaFocoGluteoExtremo, String pathologyText) {
        List<String> sequencia = gerarSequenciaDivisao(userRequest.getFrequencyPerWeek(), isGluteFocus, aplicaFocoGluteoExtremo);
        StringBuilder listaDias = new StringBuilder();
        for (int i = 0; i < sequencia.size(); i++) {
            listaDias.append("   - Dia ").append(i + 1).append(" - ").append(sequencia.get(i)).append("\n");
        }
        return "REGRAS ESTRITAS DE DIVISÃO (FREQUÊNCIA %d DIAS):\n%s".formatted(userRequest.getFrequencyPerWeek(), listaDias.toString());
    }

    private String buildDiretrizCardioCore(int volumeIdeal) {
        return """
                [REGRA OBRIGATÓRIA: CARDIO EM DIAS COM BLOCO DE CORE]
                - Dias de CORE exigem OBRIGATORIAMENTE %d exercícios de CARDIO do dicionário.
                """.formatted(CARDIO_OBRIGATORIO_POR_DIA_CORE);
    }

    private String buildDiretrizDicionario(Map<String, List<String>> dictionary) {
        StringBuilder sb = new StringBuilder("DICIONÁRIO OFICIAL DE EXERCÍCIOS:\n");
        for (Map.Entry<String, List<String>> entry : dictionary.entrySet()) {
            sb.append("- ").append(entry.getKey()).append(": ").append(String.join(", ", entry.getValue())).append("\n");
        }
        return sb.toString();
    }

    private String buildDiretrizRelatorioMedico(String medicalReportText, String pathologyText) {
        if (medicalReportText == null || medicalReportText.isBlank()) return "";
        return "[RELATÓRIO MÉDICO ANEXADO]\nPatologia declarada: %s\nConteúdo: %s\n".formatted(pathologyText, medicalReportText);
    }

    private String buildDiretrizAlimentar(Macros macros) {
        StringBuilder dietTable = new StringBuilder("DIRETRIZES ALIMENTARES:\n");
        for (MealSuggestion m : macros.mealSuggestions()) {
            dietTable.append("- %s (%s): %d kcal\n".formatted(m.name(), m.time(), (int) (macros.dailyCalories() * m.pctCalories())));
        }
        return dietTable.toString();
    }

    private List<String> gerarSequenciaDivisao(int frequencia, boolean isGluteFocus, boolean focoExtremo) {
        if (frequencia <= 1) return List.of("FULL BODY: Corpo Inteiro");
        if (focoExtremo) return gerarSequenciaFocoGluteoExtremo(frequencia);

        List<String> ciclo = isGluteFocus ?
                List.of("LEGS: Glúteos e Posterior", "PUSH: Peito, Ombros e Tríceps", "LEGS: Quadríceps e Glúteos", "PULL: Costas e Bíceps") :
                List.of("PUSH: Peito, Ombros e Tríceps", "PULL: Costas e Bíceps", "LEGS: Quadríceps, Posterior e Glúteos");

        List<String> sequencia = new ArrayList<>();
        for (int i = 0; i < frequencia; i++) {
            sequencia.add(ciclo.get(i % ciclo.size()));
        }
        return sequencia;
    }

    private List<String> gerarSequenciaFocoGluteoExtremo(int frequencia) {
        String legs = "LEGS: Foco Glúteos (Grande, Médio, Mínimo)";
        String core = "CORE: Core e Condicionamento (Cardio Obrigatório)";
        String superior = "SUPERIOR: Manutenção de Peito, Costas e Ombros";
        String combinado = "MANUTENÇÃO: Superiores e Core";

        switch (frequencia) {
            case 2:
                return List.of(legs, combinado);
            case 3:
                return List.of(legs, combinado, legs);
            case 4:
                return List.of(legs, core, legs, superior);
            case 5:
                return List.of(legs, core, legs, superior, legs);
            default:
                List<String> seq = new ArrayList<>();
                for (int i = 0; i < frequencia; i++) seq.add(i == 2 ? core : (i == frequencia - 2 ? superior : legs));
                return seq;
        }
    }

    public String buildFeedbackDeErro(String erro, Set<String> nomesUsadosNoPlano, boolean permiteCustom,
                                      Map<String, List<String>> exerciseDictionary, Set<String> nomesAnteriores) {
        return "\nERRO NA TENTATIVA ANTERIOR: " + erro + "\nEXERCÍCIOS JÁ USADOS: " + String.join(", ", nomesUsadosNoPlano) + "\nCORRIGE E GERA NOVAMENTE.";
    }
}