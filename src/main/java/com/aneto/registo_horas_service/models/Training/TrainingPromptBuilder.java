package com.aneto.registo_horas_service.models.Training;

import com.aneto.registo_horas_service.dto.request.UserProfileRequest;
import com.aneto.registo_horas_service.dto.response.Macros;
import com.aneto.registo_horas_service.dto.response.MealSuggestion;
import com.aneto.registo_horas_service.models.Enum;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
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
                                  List<String> exerciciosDoS3,
                                  boolean aplicaFocoGluteoExtremo) {

        int dias = userRequest.getFrequencyPerWeek();
        String objectiveText = defaultIfEmpty(userRequest.getObjective(), "Manutenção de saúde e bem-estar");
        String locationText = defaultIfEmpty(userRequest.getLocation(), "Não especificada");
        String bodyTypeText = (userRequest.getBodyType() != null) ? userRequest.getBodyType().name() : "ECTOMORPH";
        String genderText = (userRequest.getGender() != null) ? userRequest.getGender().name() : "MALE";
        String weightKg = defaultIfEmpty(String.valueOf(userRequest.getWeightKg()), "70");
        String durationText = defaultIfEmpty(userRequest.getDurationPerSession(), "60 minutos");

        String protocolId = (userRequest.getProtocol() == null) ? "nasm_estabilizacao" : userRequest.getProtocol();
        Enum.TrainingProtocol protocol = Enum.TrainingProtocol.fromId(protocolId);

        boolean isGluteFocus = objectiveText.toLowerCase().contains("glúteo") || objectiveText.toLowerCase().contains("gluteo");
        boolean isSedentary = "sedentary".equalsIgnoreCase(userRequest.getExerciseHistory());

        String tempoNASM = protocolId.contains("estabilização") ? "4-2-1" : "2-0-2";
        String listaParaEvitar = (exerciciosDoS3 == null || exerciciosDoS3.isEmpty()) ? "Nenhum (Primeiro plano do aluno)" : String.join(", ", exerciciosDoS3);

        String protocoloEfetivo = isSedentary ? "Adaptação Anatómica (Baixa Intensidade)" : (isDeloadWeek ? "Deload (Redução Programada de Carga - Semana " + weekNumber + ")" : protocol.getLabel());
        String repsEfetivas = isSedentary ? "12 a 15 (longe da falha)" : protocol.getReps();
        String setsEfetivas = (isSedentary || isDeloadWeek) ? "2" : protocol.getSets();
        String descansoEfetivo = isDeloadWeek ? calcularDescansoDeload(protocol, objectiveText, weightKg) : calcularDescansoCientifico(objectiveText, weightKg);

        // Declaradas todas as variáveis necessárias antes do Stream
        String diretrizPrioridade = buildDiretrizPrioridadeRelatorios(userRequest.getMedicalReportText(), userRequest.getReportVisbobyText());
        String diretrizPrescricaoVisbody = buildDiretrizPrescricaoVisbody(userRequest.getReportVisbobyText());
        String diretrizAjusteMetabolico = buildDiretrizAjusteMetabolicoVisbody(userRequest.getReportVisbobyText());
        String diretrizPistasMentais = buildDiretrizPistasMentais(userRequest.getReportVisbobyText());

        String diretrizFocoEspecial = buildDiretrizFocoEspecial(aplicaFocoGluteoExtremo, isGluteFocus, protocol, exerciseDictionaryComSub);
        String diretrizVariedade = """
                [SISTEMA DE VARIAÇÃO ANTI-PLATÔ]
                - EXERCÍCIOS JÁ REALIZADOS (PROIBIDO REPETIR): %s.
                - REGRA DE OURO: É estritamente proibido repetir o mesmo exercício em dias diferentes deste plano.
                - TEMA: Focar em variações biomecânicas.
                - RITMO OBRIGATÓRIO: O campo 'tempo' no JSON deve ser rigorosamente '%s'.
                """.formatted(listaParaEvitar, tempoNASM);

        String diretrizProtocolo = "DIRETRIZES TÉCNICAS E FISIOLÓGICAS (%s):\n- Séries: %s | Repetições: %s\n- DESCANSO CIENTÍFICO: %s segundos fixos.\n- RITMO (Tempo): %s\n".formatted(protocoloEfetivo, setsEfetivas, repsEfetivas, descansoEfetivo, protocol.getTempo());
        String diretrizSegurancaIniciante = isSedentary ? "[ALERTA DE SEGURANÇA: ALUNO SEDENTÁRIO]\n- O aluno nunca treinou. É TERMINANTEMENTE PROIBIDO levar à falha concêntrica.\n- Prioridade: Estabilidade hemodinâmica. Não usar superséries.\n" : "";
        String diretrizPeriodizacao = isDeloadWeek ? "[SEMANA DE DELOAD - RECUPERAÇÃO PROGRAMADA (Semana %d do ciclo)]\n- VOLUME: gera EXATAMENTE %d exercícios por dia.\n- INTENSIDADE: RPE máximo 5-6 em todas as séries.\n".formatted(weekNumber, volumeIdeal) : "";

        Set<String> nomesJaRealizados = (exerciciosDoS3 == null) ? Set.of() : new HashSet<>(exerciciosDoS3);
        String repertorioFiltrado = filtrarRepertorioSugerido(protocol.getSuggestedExercises(), nomesJaRealizados);
        String diretrizBiomecanica = "DIRETRIZES DE SELEÇÃO BIOMECÂNICA ESTREITAS:\n1. REPERTÓRIO: %s.\n2. PROIBIDOS: %s.\n3. ADAPTAÇÃO: Patologia \"%s\".\n".formatted(repertorioFiltrado, protocol.getForbiddenExercises(), pathologyText);

        String diretrizAquecimento = buildDiretrizAquecimento(pathologyEspecifica, pathologyText);

        List<String> sequenciaDias = gerarSequenciaDivisao(userRequest.getFrequencyPerWeek(), isGluteFocus, aplicaFocoGluteoExtremo);
        boolean temDiaDeCore = sequenciaDias.stream()
                .map(dia -> dia.split(":", 2)[0].trim())
                .anyMatch(categoria -> categoria.equalsIgnoreCase("CORE"));

        String diretrizTreino = buildDiretrizTreino(sequenciaDias, durationText, volumeIdeal);
        String diretrizAlimentar = buildDiretrizAlimentar(macros);

        boolean permiteFinalizador = !isSedentary && !pathologyEspecifica && !isDeloadWeek && isObjetivoCompativel(objectiveText) && totalMinutos >= 40;
        String diretrizFinalizador = permiteFinalizador ? "[FINALIZADOR METABÓLICO/ANAERÓBIO]\n- Adiciona UM exercício FINAL de condicionamento metabólico em cada dia, na categoria FINALIZADOR.\n" : "";

        String diretrizNomenclaturaDias = buildDiretrizNomenclaturaDias(userRequest, sequenciaDias);
        String diretrizCardioCore = temDiaDeCore ? buildDiretrizCardioCore(volumeIdeal) : "";
        String diretrizDicionario = buildDiretrizDicionario(exerciseDictionary);

        // 1. DECLARAR A NOVA DIRETRIZ DO RÁCIO POSTURAL
        String diretrizRacioVolumePostural = buildDiretrizRacioVolumePostural(userRequest.getReportVisbobyText());

        String diretrizRelatorioMedico = buildDiretrizRelatorioMedico(userRequest.getMedicalReportText(), pathologyText);
        String diretrizRelatorioVisbody = buildDiretrizRelatorioVisbody(userRequest.getReportVisbobyText());

        String blocoDiretrizesCompletas = Stream.of(
                        diretrizPrioridade,
                        diretrizPrescricaoVisbody,
                        diretrizAjusteMetabolico,
                        diretrizPistasMentais,
                        diretrizRacioVolumePostural,
                        diretrizFocoEspecial, diretrizVariedade, diretrizAquecimento,
                        diretrizSegurancaIniciante, diretrizProtocolo, diretrizBiomecanica,
                        diretrizTreino, diretrizFinalizador, diretrizPeriodizacao,
                        diretrizNomenclaturaDias, diretrizCardioCore, diretrizDicionario,
                        diretrizAlimentar, diretrizRelatorioMedico, diretrizRelatorioVisbody
                )
                .filter(s -> s != null && !s.isBlank())
                .collect(Collectors.joining("\n"));

        String regrasFinais = "REGRAS CRÍTICAS DE FECHAMENTO:\n1. FREQUÊNCIA COMPLETA OBRIGATÓRIA: Gera exatamente %d objetos dentro do array 'plan' (Dia 1 até Dia %d).\n2. DURAÇÃO: O treino deve durar %d minutos. Gera EXATAMENTE %d exercícios por dia.\n3. RITMO E DESCANSO: Ritmo %s e Descanso %s segundos.\n4. TOTAIS DIETA: %d kcal, %dg Prot, %dg Carbs, %dg Fats.\n"
                .formatted(dias, dias, totalMinutos, volumeIdeal, protocol.getTempo(), descansoEfetivo, macros.dailyCalories(), macros.protein(), macros.carbs(), macros.fats());

        StringBuilder jsonDaysExample = getStringBuilder(dias, protocol, descansoEfetivo);

        String template = """
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
                """;
        return String.format(Locale.US, template,
                userRequest.getAge(), bodyTypeText, genderText, userRequest.getWeightKg(), objectiveText, pathologyText,
                blocoDiretrizesCompletas, regrasFinais, dias, protocol.getLabel(), jsonDaysExample.toString(),
                macros.dailyCalories(), macros.imc(), macros.imcCategory(), macros.protein(), macros.carbs(), macros.fats()
        );
    }

    @NotNull
    private static StringBuilder getStringBuilder(int dias, Enum.TrainingProtocol protocol, String descansoEfetivo) {
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
                        "details": "Instrução técnica biomecânica ajustada à postura do VisBody.", "notas": "Pista mental e proteção lombar/articular.", "date": "Data"
                        }
                      ]
                    }%s""", i, protocol.getTempo(), descansoEfetivo, (i < dias ? ",\n    " : "")));
        }
        return jsonDaysExample;
    }
    private String buildDiretrizRacioVolumePostural(String reportVisbobyText) {
        if (reportVisbobyText == null || reportVisbobyText.isBlank()) return "";

        return """
        [REGRA DE PROPORÇÃO DE VOLUME POSTURAL (RÁCIO 2:1)]
        - Devido à protração de ombros e cabeça anteriorizada detetadas no VisBody:
          * No dia UPPER, é OBRIGATÓRIO prescrever EXATAMENTE o dobro de exercícios para a CADEIA POSTIOR (Costas, Trapézio Médio/Inferior e Deltóide Posterior) em relação aos exercícios de Peito.
          * Exemplo para 6 exercícios no dia UPPER: 3 de Costas/Escápula, 1 de Peito, 1 de Ombro Posterior/Lateral, 1 de Braços.
        """;
    }

    private String buildDiretrizPrioridadeRelatorios(String medicalReportText, String reportVisbobyText) {
        boolean temMedico = medicalReportText != null && !medicalReportText.isBlank();
        boolean temVisbody = reportVisbobyText != null && !reportVisbobyText.isBlank();

        if (!temMedico && !temVisbody) return "";

        StringBuilder sb = new StringBuilder();
        sb.append("\n=========================================================\n");
        sb.append("⚠️ HIERARQUIA CRÍTICA DE INTERVENÇÃO (MÉDICA & VISBODY) ⚠️\n");
        sb.append("OS RELATÓRIOS ANEXADOS PREVALECEM SOBRE QUAISQUER OUTROS OBJETIVOS ESTÉTICOS.\n");

        if (temMedico) {
            sb.append("1. SEGURANÇA LOMBAR: Lesão crónica confirmada no atestado médico. Proibido compressão axial e flexão/extensão lombar sob carga.\n");
        }
        if (temVisbody) {
            sb.append("2. COMPOSIÇÃO CORPORAL & REABILITAÇÃO POSTURAL:\n");
            sb.append("   - Perfil sarcopénico / 'falso magro' (alta gordura, baixa MME): Priorizar hipertrofia metabólica e recomposição sem levar à falha.\n");
            sb.append("   - Atacar ativamente a anteversão pélvica e a protração dos ombros identificadas no scanner.\n");
        }
        sb.append("=========================================================\n");

        return sb.toString();
    }

    private String buildDiretrizPrescricaoVisbody(String reportVisbobyText) {
        if (reportVisbobyText == null || reportVisbobyText.isBlank()) return "";

        return """
            [DIRETRIZES TÉCNICAS DERIVADAS DO SCANNER VISBODY]
            1. CORREÇÃO LOMBO-PÉLVICA (ANTEVERSÃO PÉLVICA DETETADA):
               - Proibido qualquer carga axial direta na coluna (ex: Agachamento Livre/Barra nas costas).
               - OBRIGATÓRIO incluir exercícios de ativação do Glúteo Máximo e Transverso do Abdómen no dia LOWER.
            2. CORREÇÃO DA CINTURA ESCAPULAR E CERVICAL (PROTRAÇÃO DE OMBROS E CABEÇA PROJETADA):
               - No dia UPPER, priorizar a proporção 2:1 de Puxadas/Remadas (Cadeia Posterior) em relação aos exercícios de Peito (Cadeia Anterior).
               - Foco obrigatório em retratores da escápula (Rombóides e Trapézio Inferior) e rotadores externos (ex: Wall Slide, Face Pull, Rotação Externa).
            3. ADAPTAÇÃO JOELHO ESQUERDO (HIPEREXTENSÃO DETETADA):
               - Instrução obrigatória nas 'notas/details': "Manter um ligeiro flexo funcional (evitar o bloqueio/semiflexão do joelho no final do movimento)".
            """;
    }

    private String buildDiretrizAjusteMetabolicoVisbody(String reportVisbobyText) {
        if (reportVisbobyText == null || reportVisbobyText.isBlank()) return "";

        return """
            [SISTEMA DE PRESCRIÇÃO NUTRICIONAL DO VISBODY]
            - A aluna apresenta BMR reduzido e perfil de baixa Massa Muscular Esquelética.
            - A distribuição de macronutrientes do JSON de resposta DEVE priorizar o aporte proteico (mínimo 2.0g/kg a 2.2g/kg) para estimular a síntese proteica sem ultrapassar o gasto calórico total.
            - Não prescrever défices calóricos agressivos; focar em RECOMPOSIÇÃO CORPORAL.
            """;
    }

    private String buildDiretrizPistasMentais(String reportVisbobyText) {
        if (reportVisbobyText == null || reportVisbobyText.isBlank()) return "";

        return """
            [REQUISITO CRÍTICO NOS CAMPOS 'details' E 'notas' DO JSON]
            - Para exercícios do dia LOWER, o campo 'notas' DEVE conter explicitamente o aviso: "Manter retroversão pélvica consciente / sem hiperextensão lombar (VisBody: anteversão)".
            - Para exercícios com membros inferiores, incluir a nota: "Evitar o bloqueio articular completo do joelho esquerdo (VisBody: hiperextensão)".
            - Para exercícios do dia UPPER, o campo 'details' DEVE incluir: "Foco na depressão e retração escapular (VisBody: ombros enrolados)".
            """;
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
                "[REGRA OBRIGATÓRIA DE AQUECIMENTO] O aluno TEM patologia (\"%s\"). Order 1 deve ser um exercício da categoria REABILITAÇÃO ou MOBILIDADE do dicionário oficial abaixo, focado nessa área.\n".formatted(pathologyText) :
                "[REGRA OBRIGATÓRIA DE AQUECIMENTO] Order 1 deve ser um exercício da categoria MOBILIDADE do dicionário oficial abaixo (nunca um nome fora do dicionário).\n";
    }

    private String buildDiretrizTreino(List<String> sequencia, String durationText, int volumeIdeal) {
        return "REGRAS DE DIVISÃO: " + String.join(" -> ", sequencia) + " | Duração: " + durationText + " | Volume: " + volumeIdeal + " ex/dia.";
    }

    private String buildDiretrizNomenclaturaDias(UserProfileRequest userRequest, List<String> sequencia) {
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

    private String buildDiretrizRelatorioVisbody(String reportVisbobyText) {
        if (reportVisbobyText == null || reportVisbobyText.isBlank()) return "";

        // Remove marcadores de código markdown (```json e ```) para enviar texto limpo à LLM
        String limpo = reportVisbobyText
                .replaceAll("(?s)```json\\s*", "")
                .replaceAll("(?s)```\\s*", "")
                .trim();

        return "\n[RELATÓRIO VISBODY / AVALIAÇÃO POSTURAL E BIOIMPEDÂNCIA ANEXADO]\nConteúdo:\n" + limpo + "\n";
    }
    private String buildDiretrizAlimentar(Macros macros) {
        StringBuilder dietTable = new StringBuilder("DIRETRIZES ALIMENTARES:\n");
        for (MealSuggestion m : macros.mealSuggestions()) {
            dietTable.append("- %s (%s): %d kcal\n".formatted(m.name(), m.time(), (int) (macros.dailyCalories() * m.pctCalories())));
        }
        return dietTable.toString();
    }

    private List<String> gerarSequenciaDivisao(int frequencia, boolean isGluteFocus, boolean focoExtremo) {
        if (focoExtremo) {
            return gerarSequenciaFocoGluteoExtremo(frequencia);
        }

        if (isGluteFocus) {
            return switch (frequencia) {
                case 1 -> List.of("FULL BODY: Glúteos, Pernas e Tronco");
                case 2 -> List.of("LOWER: Foco Glúteos e Posterior", "UPPER: Peito, Costas, Ombros e Braços");
                case 3 -> List.of("LEGS: Glúteos e Posterior", "UPPER: Peito, Costas e Ombros", "LEGS: Quadríceps e Glúteos");
                case 4 -> List.of("LEGS: Glúteos e Posterior", "PUSH: Peito, Ombros e Tríceps", "LEGS: Quadríceps e Glúteos", "PULL: Costas e Bíceps");
                case 5 -> List.of("LEGS: Glúteos e Posterior", "PUSH: Peito, Ombros e Tríceps", "LEGS: Quadríceps e Glúteos", "PULL: Costas e Bíceps", "FULL BODY: Foco Glúteos e Core");
                default -> List.of("LEGS: Glúteos e Posterior", "PUSH: Peito, Ombros e Tríceps", "LEGS: Quadríceps e Glúteos", "PULL: Costas e Bíceps", "FULL BODY: Foco Glúteos e Core", "LEGS: Glúteos e Isquiotibiais");
            };
        }

        return switch (frequencia) {
            case 1 -> List.of("FULL BODY: Corpo Inteiro");
            case 2 -> List.of("UPPER: Peito, Costas, Ombros e Braços", "LOWER: Quadríceps, Posterior e Glúteos");
            case 3 -> List.of("PUSH: Peito, Ombros e Tríceps", "PULL: Costas e Bíceps", "LEGS: Quadríceps, Posterior e Glúteos");
            case 4 -> List.of("UPPER: Peito e Costas", "LOWER: Quadríceps e Glúteos", "UPPER: Ombros e Braços", "LOWER: Posterior e Panturrilhas");
            case 5 -> List.of("PUSH: Peito, Ombros e Tríceps", "PULL: Costas e Bíceps", "LEGS: Quadríceps e Glúteos", "UPPER: Peito, Costas e Ombros", "LOWER: Posterior, Glúteos e Core");
            default -> List.of("PUSH: Peito e Tríceps", "PULL: Costas e Bíceps", "LEGS: Quadríceps e Panturrilhas", "SHOULDERS: Ombros e Core", "LEGS: Posterior e Glúteos", "FULL BODY: Condicionamento e Mobilidade");
        };
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