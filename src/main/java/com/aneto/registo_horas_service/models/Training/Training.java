package com.aneto.registo_horas_service.models.Training;

import com.aneto.registo_horas_service.dto.request.UserProfileRequest;
import com.aneto.registo_horas_service.dto.response.*;
import com.aneto.registo_horas_service.models.Enum;
import com.aneto.registo_horas_service.service.ExerciseVideoService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.ResponseFormat;
import org.springframework.stereotype.Component;
import org.apache.commons.lang3.StringUtils;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Component
@Slf4j
@RequiredArgsConstructor
public class Training {

    private final ChatModel chatModel;
    private final ObjectMapper objectMapper;
    private final ExerciseVideoService exerciseVideoService;

    public TrainingPlanResponse generateTrainingPlan(UserProfileRequest userRequest, List<String> exerciciosDoS3) {
        // Sem contagem de semana disponível: assume semana 1 (sem deload).
        // Para ativar a periodização automática (deload a cada 4 semanas), o chamador
        // deve invocar generateTrainingPlan(userRequest, exerciciosDoS3, semanaAtual).
        return generateTrainingPlan(userRequest, exerciciosDoS3, 1);
    }

    /**
     * @param weekNumber Número da semana do ciclo de treino do aluno (1, 2, 3, 4...).
     *                    A cada 4ª semana (4, 8, 12...) o sistema aplica automaticamente
     *                    uma semana de DELOAD (volume e intensidade reduzidos) para
     *                    prevenir overreaching e platôs. Se o caller não tiver esta
     *                    informação, usar generateTrainingPlan(userRequest, exerciciosDoS3).
     */
    public TrainingPlanResponse generateTrainingPlan(UserProfileRequest userRequest, List<String> exerciciosDoS3, int weekNumber) {

        log.info("Iniciando generateTrainingPlan para o utilizador. Tem relatório médico? {}",
                (userRequest.getMedicalReportText() != null && !userRequest.getMedicalReportText().isBlank()));

        // 1. Sanitização de entradas
        String exerciseHistoryText = sanitizarExerciseHistory(userRequest.getExerciseHistory());
        String objectiveText = defaultIfEmpty(userRequest.getObjective(), "Manutenção de saúde e bem-estar");
        String locationText = defaultIfEmpty(userRequest.getLocation(), "Não especificada");
        String bodyTypeText = (userRequest.getBodyType() != null) ? userRequest.getBodyType().name() : "ECTOMORPH";
        String genderText = (userRequest.getGender() != null) ? userRequest.getGender().name() : "MALE";
        String weightKg = defaultIfEmpty(String.valueOf(userRequest.getWeightKg()), "70");

        String durationText = (userRequest.getDurationPerSession() != null && !userRequest.getDurationPerSession().isBlank())
                ? userRequest.getDurationPerSession() : "60 minutos";

        String protocolId = (userRequest.getProtocol() == null) ? "nasm_estabilizacao" : userRequest.getProtocol();
        Enum.TrainingProtocol protocol = Enum.TrainingProtocol.fromId(protocolId);

        int totalMinutos = extrairMinutosTotais(durationText);
        int volumeIdealBase = Math.max(6, totalMinutos / 7);

        // --- PERIODIZAÇÃO: deload automático a cada 4ª semana ---
        boolean isDeloadWeek = weekNumber > 0 && weekNumber % 4 == 0;
        int volumeIdeal = isDeloadWeek ? Math.max(4, (int) Math.round(volumeIdealBase * 0.7)) : volumeIdealBase;

        log.info("Semana {} do ciclo. Semana de deload? {} (volume base: {}, volume aplicado: {})",
                weekNumber, isDeloadWeek, volumeIdealBase, volumeIdeal);

        String pathologyDeclarada = (userRequest.getPathology() == null || userRequest.getPathology().isBlank())
                ? "Nenhuma limitação relatada" : userRequest.getPathology();

        PatologiaInferida patologiaInferida = inferirPatologiaDoRelatorio(userRequest.getMedicalReportText());
        String pathologyInferidaRelatorio = (patologiaInferida == null) ? null : patologiaInferida.categorias();

        String pathologyText = (pathologyInferidaRelatorio == null)
                ? pathologyDeclarada
                : pathologyDeclarada.contains("Nenhuma")
                ? pathologyInferidaRelatorio
                : pathologyDeclarada + ", " + pathologyInferidaRelatorio;

        log.info("Patologia efetiva usada no prompt: '{}' (declarada: '{}', inferida do relatório: '{}', termos literais encontrados: '{}')",
                pathologyText, pathologyDeclarada, pathologyInferidaRelatorio,
                patologiaInferida == null ? "nenhum" : String.join(", ", patologiaInferida.termosEncontrados()));

        boolean pathologyEspecifica = !pathologyText.contains("Nenhuma") || userRequest.getMedicalReportText() != null
                && !userRequest.getMedicalReportText().isBlank();

        // 2. Cálculos Nutricionais e Macros
        Macros macros = MacroCalculator.calculate(
                userRequest.getWeightKg(),
                userRequest.getHeightCm(),
                userRequest.getAge(),
                genderText,
                bodyTypeText,
                userRequest.getBodyFat() != null ? userRequest.getBodyFat() : 15.0,
                userRequest.getMealsPerDay() != null ? userRequest.getMealsPerDay() : 6
        );

        // 3. Lógica de Variação (Anti-Platô) e NASM
        String tempoNASM = protocolId.contains("estabilizacao") ? "4-2-1" : "2-0-2";
        String listaParaEvitar = (exerciciosDoS3 == null || exerciciosDoS3.isEmpty())
                ? "Nenhum (Primeiro plano do aluno)"
                : String.join(", ", exerciciosDoS3);

        // --- DETEÇÃO DE FOCO ESPECÍFICO (EX: GLÚTEO) ---
        boolean isGluteFocus = objectiveText.toLowerCase().contains("glúteo") || objectiveText.toLowerCase().contains("gluteo");
        String diretrizFocoEspecial = isGluteFocus ? """
                [FOCO PRIORITÁRIO: GLÚTEOS]
                - O aluno deseja foco total em Glúteos.
                - 70% dos exercícios de membros inferiores devem ser específicos para Glúteos (Grande, Médio e Mínimo).
                - Incluir obrigatoriamente variações de Elevação Pélvica, Agachamento Búlgaro e Abduções.
                """ : "";

        String diretrizVariedade = """
                [SISTEMA DE VARIAÇÃO ANTI-PLATÔ]
                - EXERCÍCIOS JÁ REALIZADOS (PROIBIDO REPETIR): %s.
                - REGRA DE OURO: É estritamente proibido repetir o mesmo exercício em dias diferentes deste plano.
                - TEMA: Focar em variações biomecânicas.
                - RITMO OBRIGATÓRIO: O campo 'tempo' no JSON deve ser rigorosamente '%s'.
                """.formatted(listaParaEvitar, tempoNASM);

        // 4. Adaptação para Sedentários (Prevenção de mal-estar) e Deload (Periodização)
        boolean isSedentary = "sedentary".equalsIgnoreCase(userRequest.getExerciseHistory());

        String protocoloEfetivo;
        String repsEfetivas;
        String setsEfetivas;
        String descansoEfetivo;

        if (isSedentary) {
            protocoloEfetivo = "Adaptação Anatómica (Baixa Intensidade)";
            repsEfetivas = "12 a 15 (longe da falha)";
            setsEfetivas = "2";
            descansoEfetivo = "120";
        } else if (isDeloadWeek) {
            protocoloEfetivo = "Deload (Redução Programada de Carga - Semana " + weekNumber + ")";
            repsEfetivas = protocol.getReps();
            setsEfetivas = "2";
            descansoEfetivo = calcularDescansoDeload(protocol, objectiveText, weightKg);
        } else {
            protocoloEfetivo = protocol.getLabel();
            repsEfetivas = protocol.getReps();
            setsEfetivas = protocol.getSets();
            descansoEfetivo = calcularDescansoCientifico(protocol, objectiveText, weightKg);
        }

        String diretrizSegurancaIniciante = isSedentary ? """
                [ALERTA DE SEGURANÇA: ALUNO SEDENTÁRIO]
                - O aluno nunca treinou. É TERMINANTEMENTE PROIBIDO levar à falha concêntrica.
                - Prioridade: Estabilidade hemodinâmica. Não usar superséries.
                - Evitar exercícios com a cabeça abaixo do nível do coração.
                - Foco em máquinas (maior estabilidade).
                """ : "";

        String diretrizPeriodizacao = isDeloadWeek ? """
                [SEMANA DE DELOAD - RECUPERAÇÃO PROGRAMADA (Semana %d do ciclo)]
                - Esta é uma semana de DELOAD programado, não um treino normal.
                - OBJETIVO FISIOLÓGICO: permitir recuperação neuromuscular e do sistema nervoso
                  central, prevenindo overreaching e platôs de longo prazo.
                - VOLUME: gera EXATAMENTE %d exercícios por dia (reduzido face ao habitual).
                - INTENSIDADE: RPE máximo 5-6 em todas as séries (longe da falha). Séries: 2.
                - PROIBIDO usar superséries, dropsets ou qualquer técnica de intensificação.
                - NÃO incluir finalizador metabólico/anaeróbio esta semana, mesmo que o
                  objetivo do aluno normalmente o permita.
                - Mantém a técnica, a variedade e a estrutura normal dos dias, mas o foco
                  desta semana é qualidade de movimento e recuperação, não sobrecarga.
                - No campo 'notas' de pelo menos um exercício por dia, refere brevemente que
                  esta é uma semana de deload/recuperação, para o aluno perceber a mudança de
                  intensidade e não estranhar.
                """.formatted(weekNumber, volumeIdeal) : "";

        String detalhesEquipamento = locationText.equalsIgnoreCase("Casa") ?
                "UTILIZA APENAS: 'Peso Corporal', 'Halteres' ou 'Bandas Elásticas'. PROIBIDO o uso de máquinas de ginásio." :
                "UTILIZA: 'Máquinas', 'Barras', 'Polias' ou 'Halteres'.";

        String filtroEquipamento = isSedentary ?
                "PREFERÊNCIA OBRIGATÓRIA: Máquinas guiadas para maior controlo motor e segurança." :
                detalhesEquipamento;

        // --- BLOCOS DE DIRETRIZES TÉCNICAS ---

        String diretrizProtocolo = """
                DIRETRIZES TÉCNICAS E FISIOLÓGICAS (%s):
                - Séries: %s | Repetições: %s
                - DESCANSO CIENTÍFICO: %s segundos fixos.
                - RITMO (Tempo): %s
                - JUSTIFICATIVA FISIOLÓGICA: O descanso de %s garante a resíntese de Fosfocreatina.
                """.formatted(protocoloEfetivo, setsEfetivas, repsEfetivas, descansoEfetivo, protocol.getTempo(), descansoEfetivo);

        String diretrizBiomecanica = """
                DIRETRIZES DE SELEÇÃO BIOMECÂNICA ESTREITAS:
                1. REPERTÓRIO: %s.
                2. PROIBIDOS: %s.
                3. ADAPTAÇÃO: Patologia "%s", substitui impactos por baixo torque.
                4. SEPARAÇÃO TOTAL: PROIBIDO incluir PERNAS em dias de SUPERIORES.
                5. COERÊNCIA: PUSH (Peito/Ombros/Tríceps), LEGS (Quadríceps/Isquios/Glúteos).
                """.formatted(protocol.getSuggestedExercises(), protocol.getForbiddenExercises(), pathologyText);

        String diretrizAquecimento = pathologyEspecifica ? """
                [REGRA OBRIGATÓRIA DE AQUECIMENTO E MOBILIDADE]
                - O aluno TEM patologia/limitação relatada ("%s"). O exercício de "order": 1
                  DEVE ser de Reabilitação/Mobilidade focado nessa área (categoria REABILITAÇÃO
                  do dicionário).
                - RELAÇÃO COM O DIA: sempre que possível, escolhe o aquecimento relacionado com
                  o grupo muscular treinado nesse dia.
                - VARIEDADE OBRIGATÓRIA: NÃO uses sempre o mesmo exercício de mobilidade em
                  todos os dias.
                - É PROIBIDO usar o mesmo exercício de aquecimento em dois dias CONSECUTIVOS.
                """.formatted(pathologyText)
                : """
                [REGRA OBRIGATÓRIA DE AQUECIMENTO E MOBILIDADE]
                - O aluno NÃO tem patologia relatada. O "order": 1 deve ser Mobilidade Geral,
                  escolhida EXCLUSIVAMENTE da categoria MOBILIDADE do dicionário.
                - RELAÇÃO COM O DIA: sempre que possível, escolhe o aquecimento relacionado com
                  o grupo muscular treinado nesse dia. Ex: dia de COSTAS -> "Y-W-T" ou
                  "Open Books"; dia de PERNAS -> "Knee-to-Wall" ou "Cossack Squat"; dia de
                  PEITO/OMBROS sem opção específica -> usa mobilidade geral como "Cat Cow".
                - VARIEDADE OBRIGATÓRIA: NÃO uses sempre o mesmo exercício de mobilidade em
                  todos os dias.
                - É PROIBIDO usar o mesmo exercício de aquecimento em dois dias CONSECUTIVOS.
                """;

        String diretrizTreino = buildDiretrizTreino(userRequest, durationText, volumeIdeal);
        String diretrizAlimentar = buildDiretrizAlimentar(macros);

        String diretrizRepertorio = """
                EXPANSÃO DE REPERTÓRIO:
                - Escolha exercícios que respeitem o protocolo %s.
                - Objetivo: %s. Frequência: %d dias.
                """.formatted(protocol.getLabel(), objectiveText, userRequest.getFrequencyPerWeek());

        String diretrizEquipamento = """
                LOGÍSTICA E EQUIPAMENTO:
                - Localização: %s.
                - REGRA ABSOLUTA: O campo 'equipment' no JSON deve indicar o material.
                - %s
                - VÍDEOS: Usa obrigatoriamente nomes do DICIONÁRIO.
                """.formatted(locationText, filtroEquipamento);

        String diretrizArrefecimento = """
                REGRA DE ARREFECIMENTO:
                - O ÚLTIMO exercício de cada dia deve ser um exercício da categoria ALONGAMENTO do DICIONÁRIO OFICIAL, focado em %s.
                - RELAÇÃO COM O DIA: escolhe o alongamento relacionado com o grupo muscular treinado nesse dia. Ex: dia de COSTAS -> "Alongamento do músculo grande dorsal"; dia de PERNAS -> "Flexores da Anca", "Alongamento borboleta", "Walking Lunges with Reach" ou "Frankenstein Walk".
                - VARIEDADE OBRIGATÓRIA: NÃO uses sempre o mesmo exercício de arrefecimento em todos os dias.
                - É PROIBIDO usar o mesmo exercício de arrefecimento em dois dias CONSECUTIVOS.
                """.formatted(pathologyText);

        // --- FINALIZADOR ANAERÓBIO/METABÓLICO (condicional, nunca para sedentários ou com patologia) ---
        String objectiveLower = objectiveText.toLowerCase();
        boolean objetivoCompativel = objectiveLower.contains("emagrec")
                || objectiveLower.contains("perda de gordura")
                || objectiveLower.contains("definição")
                || objectiveLower.contains("definicao")
                || objectiveLower.contains("condicionamento")
                || objectiveLower.contains("resistência")
                || objectiveLower.contains("resistencia")
                || objectiveLower.contains("hipertrofia");

        boolean permiteFinalizador = !isSedentary && !pathologyEspecifica && !isDeloadWeek && objetivoCompativel && totalMinutos >= 40;

        String diretrizFinalizador = permiteFinalizador ? """
                [FINALIZADOR METABÓLICO/ANAERÓBIO]
                - Adiciona UM exercício FINAL de condicionamento metabólico em cada dia, na
                  categoria FINALIZADOR do DICIONÁRIO OFICIAL.
                - POSIÇÃO: é o PENÚLTIMO exercício do dia (imediatamente antes do
                  alongamento/arrefecimento final).
                - FORMATO: intervalado e curto — indica no campo 'details' um esquema tipo
                  "30-45seg trabalho / 15-20seg descanso, 3-4 rondas" ou equivalente (AMRAP,
                  EMOM), adaptado ao exercício escolhido.
                - OBJETIVO: maximizar o EPOC (consumo de oxigénio pós-exercício) e a queima
                  calórica adicional, sem comprometer a recuperação dos exercícios de força
                  anteriores.
                - VARIEDADE: não repetir o mesmo finalizador em dias consecutivos.
                - RESTRIÇÃO ABSOLUTA: NUNCA usar este bloco se o aluno for sedentário ou tiver
                  qualquer patologia/limitação relatada (não se aplica neste caso).
                """ : "";

        String diretrizMobilidadeCondicional = """
                [REGRA DE OURO: MOBILIDADE ESPECÍFICA SÓ COM QUEIXA CORRESPONDENTE]
                - Exercícios de mobilidade/reabilitação ESPECÍFICOS de uma articulação (ex: "Rotação Externa", "Clamshell") \
                só podem ser usados SE a patologia relatada mencionar explicitamente essa zona do corpo.
                - Patologia relatada pelo aluno: "%s".
                - Se a patologia for "Nenhuma limitação relatada" ou não mencionar uma articulação específica, \
                é PROIBIDO usar "Rotação Externa", "Clamshell" ou qualquer exercício de reabilitação dirigido a uma queixa que o aluno não tem.
                - Nesse caso (sem patologia), usa APENAS mobilidade GERAL: "Cat Cow", "Y-W-T", "Open Books", "Knee-to-Wall", "Cossack Squat" ou "Equilíbrio Unipodal".
                - Exemplo errado: aluno sem patologia recebe "Rotação Externa" -> ERRO CRÍTICO.
                - Exemplo correto: aluno com patologia "Ombro" recebe "Rotação Externa" como order 1 -> CORRETO.
                """.formatted(pathologyText);

        String diretrizNomenclaturaDias = buildDiretrizNomenclaturaDias(userRequest, isGluteFocus, pathologyText);

        String diretrizReabilitacao = pathologyText.contains("Nenhuma") ?
                "Foca o primeiro exercício em mobilidade geral ou ativação dinâmica." :
                buildDiretrizReabilitacao(pathologyText) + "\n- REGRA: Proibido repetir o mesmo exercício de reabilitação em dias consecutivos.";

        // --- DICIONÁRIO DE EXERCÍCIOS (BD com fallback estático) ---
        Map<String, List<String>> exerciseDictionary;
        try {
            exerciseDictionary = exerciseVideoService.getExerciseDictionary();
        } catch (Exception e) {
            log.error("Erro ao obter dicionário de exercícios da BD, a usar fallback estático: {}", e.getMessage());
            exerciseDictionary = Collections.emptyMap();
        }

        if (exerciseDictionary == null || exerciseDictionary.isEmpty()) {
            log.warn("Dicionário de exercícios da BD vazio ou indisponível — a usar fallback estático.");
            exerciseDictionary = FALLBACK_DICTIONARY;
        }

        String diretrizDicionario = buildDiretrizDicionario(exerciseDictionary);

        String diretrizAnatomiaDetalhada = """
                DETALHAMENTO ANATÓMICO:
                - No campo 'muscleGroup', lista: Motores Primários (Agonistas), Sinergistas e Estabilizadores.
                - Exemplo: "Peitoral Maior, Deltoide Anterior, Tríceps Braquial, Serrátil Anterior".
                """;

        String diretrizCargasDinamicas = """
                SISTEMA DINÂMICO DE CARGAS (RPE):
                - Histórico: "%s".
                - RPE Sugerido: Sedentário (4-5), Beginner (6-7), Intermediate (7-8), Advanced (9).
                - No campo 'cargaAtual', escreve a orientação RPE.
                """.formatted(exerciseHistoryText);

        String regrasFinais = """
                REGRAS CRÍTICAS DE FECHAMENTO:
                1. DURAÇÃO: O treino deve durar %d minutos. Gera EXATAMENTE %d exercícios por dia.
                2. RITMO E DESCANSO: Ritmo %s e Descanso %s segundos.
                3. FORMATO: Responde APENAS o JSON puro.
                4. TOTAIS DIETA: %d kcal, %dg Prot, %dg Carbs, %dg Fats.
                5. FORMATO NUMÉRICO: Todos os números no JSON usam OBRIGATORIAMENTE ponto decimal (ex: 25.5), NUNCA vírgula (25,5).
                """.formatted(totalMinutos, volumeIdeal, protocol.getTempo(), descansoEfetivo, macros.dailyCalories(), macros.protein(), macros.carbs(), macros.fats());

        String medicalReportText = userRequest.getMedicalReportText(); // pode ser null
        String diretrizRelatorioMedico = buildDiretrizRelatorioMedico(medicalReportText, pathologyText);
        log.info("Diretriz de relatório médico incluída no prompt? {} (tamanho: {} chars)",
                !diretrizRelatorioMedico.isBlank(), diretrizRelatorioMedico.length());


        // --- CONSTRUÇÃO DO PROMPT FINAL (blocos vazios são filtrados) ---
        String blocoDiretrizesCompletas = Stream.of(
                        diretrizFocoEspecial, diretrizVariedade, diretrizAquecimento, diretrizMobilidadeCondicional,
                        diretrizSegurancaIniciante, diretrizProtocolo, diretrizReabilitacao, diretrizBiomecanica,
                        diretrizAnatomiaDetalhada, diretrizCargasDinamicas, diretrizEquipamento, diretrizTreino,
                        diretrizRepertorio, diretrizFinalizador, diretrizPeriodizacao, diretrizArrefecimento, diretrizNomenclaturaDias, diretrizDicionario,
                        diretrizAlimentar, diretrizRelatorioMedico
                )
                .filter(s -> s != null && !s.isBlank())
                .collect(Collectors.joining("\n"));

        String userPrompt = """
                ATUAÇÃO: Personal Trainer e Nutricionista Profissional (Portugal).
                PERFIL: %d anos, %s, %s, %.2fkg. Objetivo: %s. Patologias: %s.
                
                SISTEMA DE REGRAS TÉCNICAS:
                %s
                
                %s
                
                FORMATO JSON OBRIGATÓRIO:
                {
                "summary": "Explicação técnica da estratégia %s.",
                "plan": [
                    {
                    "day": "Dia X - [CATEGORIA]: [FOCO]",
                    "exercises": [
                        {
                        "order": 1, "name": "...",
                        "muscleGroup": "...", "movementPlane": "...", "equipment": "...",
                        "tempo": "%s", "sets": "3", "reps": "15", "rest": "%s",
                        "weight": "0kg",
                        "cargaAtual": "...",
                         "videoUrl": "",
                         "details": "Instrução técnica biomecânica focada na execução correta e plano de movimento.",
                        "notas": "Dica de conexão mente-músculo e segurança (Pista Mental).",
                        "date": "Data do Treino"
                        }
                      ]
                    }
                ],
                "dietPlan": {
                    "dailyCalories": %d, "imc": %.2f, "imcCategory": "%s",
                    "macroDistribution": { "protein": "%dg", "carbs": "%dg", "fats": "%dg" },
                    "meals": [{"time": "HH:mm", "description": "...", "ingredients": ["..."], "calories": 0, "protein": 0, "carbs": 0, "fats": 0}]
                  }
                }
                """.formatted(
                userRequest.getAge(), bodyTypeText, genderText, userRequest.getWeightKg(), objectiveText, pathologyText,
                blocoDiretrizesCompletas, regrasFinais,
                protocol.getLabel(), protocol.getTempo(), descansoEfetivo,
                macros.dailyCalories(), macros.imc(), macros.imcCategory(),
                macros.protein(), macros.carbs(), macros.fats()
        );

        boolean temRelatorioMedico = medicalReportText != null && !medicalReportText.isBlank();
        //   log.info("prompt -enviado : {}", userPrompt);
        TrainingPlanResponse resultado = executeGeneration(userPrompt, totalMinutos, exerciseDictionary, pathologyText, exerciciosDoS3, pathologyEspecifica, permiteFinalizador, userRequest.getWeightKg());

        // Garante (em Java, não só via prompt) que o summary termina sempre com uma
        // frase de incentivo curta e ESPECÍFICA ao aluno — não genérica. Isto não
        // depende da IA cumprir a instrução; é sempre aplicado aqui.
        String summaryComFecho = garantirFechoMotivacional(resultado.getSummary(), userRequest, isDeloadWeek, weekNumber);
        resultado.setSummary(summaryComFecho);

        return resultado;
    }

    @NotNull
    private String buildDiretrizNomenclaturaDias(UserProfileRequest userRequest, boolean isGluteFocus, String pathologyText) {
        List<String> sequencia = gerarSequenciaDivisao(userRequest.getFrequencyPerWeek(), isGluteFocus);

        StringBuilder listaDias = new StringBuilder();
        for (int i = 0; i < sequencia.size(); i++) {
            listaDias.append("   - Dia ").append(i + 1).append(" - ").append(sequencia.get(i)).append("\n");
        }

        return """
                REGRAS ESTRITAS DE DIVISÃO (FREQUÊNCIA %d DIAS):
                1. NOMENCLATURA OBRIGATÓRIA: Usa EXATAMENTE estes nomes de dia, NESTA ORDEM, sem alterar texto, categoria ou sequência:
                %s
                2. PROIBIDO REPETIR A MESMA CATEGORIA (PUSH/PULL/LEGS/SUPERIORES/INFERIORES) EM DIAS CONSECUTIVOS. A sequência acima já garante isso — não a modifiques.
                3. REABILITAÇÃO DIÁRIA (OBRIGATÓRIO): O primeiro exercício (Order 1) de TODOS os dias deve ser obrigatoriamente para %s.
                4. VARIABILIDADE: Proibido repetir exercícios entre os dias. Cada bloco deve ter 100%% de exercícios únicos.
                5. ESTRUTURA: Gere exatamente %d blocos dentro do array "plan", correspondendo 1:1 à lista acima.
                """.formatted(userRequest.getFrequencyPerWeek(), listaDias, pathologyText, userRequest.getFrequencyPerWeek());
    }

    /**
     * Executa a geração do plano via IA, com validação estrita do inventário
     * de exercícios e retries com feedback específico do erro.
     */

    private TrainingPlanResponse executeGeneration(
            String prompt, int totalMinutos, Map<String, List<String>> exerciseDictionary,
            String pathologyText, List<String> exerciciosAnteriores, boolean pathologyEspecifica,
            boolean permiteFinalizador, Double weightKg) {

        log.info("Iniciando executeGeneration no ChatModel.");
        int maxRetries = 8;

        // Calculado uma única vez por chamada (não por exercício) para eficiência
        Set<String> validNames = flattenDictionary(
                (exerciseDictionary == null || exerciseDictionary.isEmpty())
                        ? FALLBACK_DICTIONARY : exerciseDictionary
        );

        // --- Opções disponíveis por categoria (para variedade e correspondência de grupo) ---
        Set<String> categoriasAquecimento = Set.of("MOBILIDADE", "REABILITAÇÃO");
        List<String> opcoesAquecimento = exerciseDictionary.entrySet().stream()
                .filter(e -> categoriasAquecimento.stream().anyMatch(cat -> e.getKey().equalsIgnoreCase(cat)))
                .flatMap(e -> e.getValue().stream())
                .distinct()
                .collect(Collectors.toList());
        boolean exigirVariedadeAquecimento = opcoesAquecimento.size() >= 2;

        Set<String> categoriasArrefecimento = Set.of("ALONGAMENTO");
        List<String> opcoesArrefecimento = exerciseDictionary.entrySet().stream()
                .filter(e -> categoriasArrefecimento.stream().anyMatch(cat -> e.getKey().equalsIgnoreCase(cat)))
                .flatMap(e -> e.getValue().stream())
                .distinct()
                .collect(Collectors.toList());
        boolean exigirVariedadeArrefecimento = opcoesArrefecimento.size() >= 2;

        Set<String> categoriasFinalizador = Set.of("FINALIZADOR");
        List<String> opcoesFinalizador = exerciseDictionary.entrySet().stream()
                .filter(e -> categoriasFinalizador.stream().anyMatch(cat -> e.getKey().equalsIgnoreCase(cat)))
                .flatMap(e -> e.getValue().stream())
                .distinct()
                .collect(Collectors.toList());
        boolean exigirVariedadeFinalizador = opcoesFinalizador.size() >= 2;
        // Se o aluno for elegível mas o dicionário não tiver categoria FINALIZADOR, desativa a exigência
        boolean finalizadorDisponivel = permiteFinalizador && !opcoesFinalizador.isEmpty();
        if (permiteFinalizador && opcoesFinalizador.isEmpty()) {
            log.warn("Finalizador anaeróbio elegível para este aluno mas categoria FINALIZADOR ausente/vazia no dicionário — a ignorar validação.");
        }

        log.info("Variedade disponível — Aquecimento: {} opções (exigir variedade: {}) | Arrefecimento: {} opções (exigir variedade: {}) | Finalizador: {} opções (elegível: {})",
                opcoesAquecimento.size(), exigirVariedadeAquecimento, opcoesArrefecimento.size(), exigirVariedadeArrefecimento,
                opcoesFinalizador.size(), finalizadorDisponivel);

        // Normaliza a lista de exercícios do plano anterior para comparação consistente
        Set<String> nomesAnteriores = (exerciciosAnteriores == null ? List.<String>of() : exerciciosAnteriores)
                .stream()
                .map(nome -> normalizeExerciseName(nome, validNames))   // lambda em vez de method reference
                .collect(Collectors.toCollection(HashSet::new));


        // --- STRUCTURED OUTPUT: força o OpenAI a devolver sempre JSON sintaticamente
        // válido (sem markdown fences, sem chavetas por fechar, sem vírgulas a mais).
        // Isto elimina a categoria de retries causados por erro de FORMATO, deixando
        // as tentativas restantes só para erros de REGRA DE NEGÓCIO (que continuam a
        // ser validados abaixo, exercício a exercício).
        // Nota: response_format=json_object exige que a palavra "JSON" apareça no
        // prompt — o nosso já a tem ("FORMATO JSON OBRIGATÓRIO", "Responde APENAS o
        // JSON puro"), por isso não precisa de ajuste adicional.
        OpenAiChatOptions jsonModeOptions = OpenAiChatOptions.builder()
                .withResponseFormat(ResponseFormat.builder()
                        .type(ResponseFormat.Type.JSON_OBJECT)
                        .build())
                .build();

        StringBuilder promptBuilder = new StringBuilder(prompt);
        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            Set<String> nomesUsadosNestaTentativa = new HashSet<>(); // <<< declarado no topo do corpo do for, fora do try
            try {
                Prompt promptComJsonMode = new Prompt(
                        new UserMessage(promptBuilder.toString()), jsonModeOptions);
                ChatResponse chatResponse = chatModel.call(promptComJsonMode);
                String textResponse = chatResponse.getResult().getOutput().getContent();
                String cleanedJson = cleanMarkdown(textResponse);

                // 1. DESSERIALIZAÇÃO INICIAL
                TrainingPlanResponse response = objectMapper.readValue(cleanedJson, TrainingPlanResponse.class);

                // 2. VALIDAÇÃO DE VOLUME
                int minExAceitavel = Math.max(4, (totalMinutos / 10));
                boolean isVolumeValido = response.getPlan() != null && !response.getPlan().isEmpty() &&
                        response.getPlan().stream().allMatch(d -> d.getExercises() != null && d.getExercises().size() >= minExAceitavel);

                if (!isVolumeValido) throw new RuntimeException("Volume insuficiente.");

                // 3. VALIDAÇÃO DE SEQUÊNCIA (sem repetição de categoria em dias consecutivos)
                String categoriaAnterior = null;
                for (TrainingDay day : response.getPlan()) {
                    String categoriaAtual = extrairCategoriaDia(day.getDay());
                    if (categoriaAtual != null && categoriaAtual.equalsIgnoreCase(categoriaAnterior)) {
                        throw new RuntimeException(
                                "Repetição de categoria de treino em dias consecutivos: \"" + categoriaAtual +
                                        "\" apareceu duas vezes seguidas (dia \"" + day.getDay() + "\"). " +
                                        "Segue rigorosamente a ordem de dias fornecida nas diretrizes de nomenclatura, sem repetir a mesma categoria em dias seguidos."
                        );
                    }
                    categoriaAnterior = categoriaAtual;
                }

                // 4. MAPEAMENTO + VALIDAÇÃO DE INVENTÁRIO + UNICIDADE/VARIEDADE + CORRESPONDÊNCIA DE GRUPO
                List<TrainingDay> updatedPlan = new ArrayList<>();
                String aquecimentoAnterior = null;
                String arrefecimentoAnterior = null;
                String finalizadorAnterior = null; // >>> NOVO: controlo de variedade do finalizador anaeróbio
                int totalCustomNoPlano = 0; // >>> NOVO: contador global de exercícios custom no plano inteiro
                final int LIMITE_CUSTOM_TOTAL = 3; // subiu de 2 para 3, cobre os dois motivos

                for (TrainingDay day : response.getPlan()) {
                    List<TrainingExercise> exercisesOfDay = day.getExercises() != null
                            ? day.getExercises() : Collections.emptyList();

                    List<TrainingExercise> enrichedExercises = new ArrayList<>();
                    int totalExDoDia = exercisesOfDay.size();
                    Set<String> gruposDoDia = extrairGruposMuscularesDoDia(day.getDay());
                    int customsNoDia = 0; // >>> NOVO: contador de exercícios custom neste dia

                    for (int i = 0; i < totalExDoDia; i++) {
                        TrainingExercise ex = exercisesOfDay.get(i);
                        boolean isAquecimento = (i == 0);
                        boolean isArrefecimento = (i == totalExDoDia - 1);
                        // Finalizador é o penúltimo exercício, só é válido se o dia tiver pelo menos
                        // 3 exercícios (para não colidir com o aquecimento) e o aluno for elegível.
                        boolean isFinalizador = finalizadorDisponivel && totalExDoDia >= 3 && (i == totalExDoDia - 2);

                        // >>> NOVO BLOCO: tratamento de exercícios CUSTOM (fora do dicionário)
                        if (ex.isCustom()) {

                            if (!pathologyEspecifica) {
                                throw new RuntimeException(
                                        "Exercício \"" + ex.getName() + "\" marcado como custom, mas o aluno não tem patologia relatada. " +
                                                "'custom' só é permitido quando ligado a uma patologia real."
                                );
                            }

                            customsNoDia++;
                            totalCustomNoPlano++;

                            if (customsNoDia > 1) {
                                throw new RuntimeException(
                                        "Mais de 1 exercício custom no mesmo dia (\"" + day.getDay() + "\"). Limite é 1 por dia."
                                );
                            }
                            if (totalCustomNoPlano > LIMITE_CUSTOM_TOTAL) {
                                throw new RuntimeException(
                                        "Mais de " + LIMITE_CUSTOM_TOTAL + " exercícios custom no plano inteiro. Limite total é " + LIMITE_CUSTOM_TOTAL + "."
                                );
                            }

                            // Normaliza minimamente, SEM validar contra o dicionário e SEM procurar vídeo
                            TrainingExercise enrichedCustom = ex.toBuilder()
                                    .videoUrl("")
                                    .date(java.time.LocalDate.now().toString())
                                    .build();

                            // >>> NOVO: log separado para revisão humana / possível promoção ao dicionário oficial
                            log.warn("[EXERCÍCIO CUSTOM] Dia: '{}' | Nome: '{}' | Patologia: '{}' | Justificação: '{}'",
                                    day.getDay(), enrichedCustom.getName(), pathologyText, enrichedCustom.getNotas());


                            // Mantém as variáveis de controlo de posição atualizadas,
                            // para que a validação de dias seguintes continue coerente
                            if (isAquecimento) {
                                aquecimentoAnterior = enrichedCustom.getName();
                            } else if (isArrefecimento) {
                                arrefecimentoAnterior = enrichedCustom.getName();
                            } else {
                                // Exercício de trabalho custom: ainda entra na verificação de unicidade do plano
                                if (!nomesUsadosNestaTentativa.add(enrichedCustom.getName())) {
                                    throw new RuntimeException(
                                            "Exercício custom repetido no plano: \"" + enrichedCustom.getName() + "\"."
                                    );
                                }
                            }

                            enrichedExercises.add(enrichedCustom);
                            continue; // salta todo o fluxo de validação normal abaixo
                        }
                        // <<< FIM DO BLOCO NOVO

                        // fluxo normal (dicionário obrigatório) — inalterado
                        TrainingExercise enriched = enrichExercise(ex, validNames, pathologyText);

                        if (isAquecimento) {
                            if (!pathologyEspecifica) {
                                // 4a. Variedade: não repetir em dias consecutivos
                                if (exigirVariedadeAquecimento && enriched.getName().equalsIgnoreCase(aquecimentoAnterior)) {
                                    throw new RuntimeException(
                                            "Exercício de aquecimento repetido em dias consecutivos: \"" + enriched.getName() +
                                                    "\". Escolhe um exercício de mobilidade diferente do DICIONÁRIO para este dia (há várias opções disponíveis)."
                                    );
                                }
                                // 4b. Correspondência de grupo muscular com o foco do dia
                                if (!grupoMuscularCorresponde(enriched.getName(), gruposDoDia)
                                        && existeOpcaoMelhorParaGrupo(gruposDoDia, opcoesAquecimento, enriched.getName(), pathologyText)) {
                                    throw new RuntimeException(
                                            "Aquecimento desalinhado com o foco do dia: \"" + enriched.getName() +
                                                    "\" não é adequado ao dia \"" + day.getDay() + "\". " +
                                                    "Escolhe um exercício de mobilidade do DICIONÁRIO relacionado com o grupo muscular treinado nesse dia."
                                    );
                                }
                            }
                            aquecimentoAnterior = enriched.getName();
                        } else if (isArrefecimento) {
                            if (!pathologyEspecifica) {   // <<< ADICIONAR esta condição
                                // 4c. Variedade: não repetir em dias consecutivos
                                if (exigirVariedadeArrefecimento && enriched.getName().equalsIgnoreCase(arrefecimentoAnterior)) {
                                    throw new RuntimeException(
                                            "Exercício de arrefecimento repetido em dias consecutivos: \"" + enriched.getName() +
                                                    "\". Escolhe um exercício de alongamento diferente do DICIONÁRIO para este dia."
                                    );
                                }
                                // 4d. Correspondência de grupo muscular com o foco do dia
                                if (!grupoMuscularCorresponde(enriched.getName(), gruposDoDia)
                                        && existeOpcaoMelhorParaGrupo(gruposDoDia, opcoesArrefecimento, enriched.getName(), pathologyText)) {
                                    throw new RuntimeException(
                                            "Arrefecimento desalinhado com o foco do dia: \"" + enriched.getName() +
                                                    "\" não é adequado ao dia \"" + day.getDay() + "\". " +
                                                    "Escolhe um exercício de alongamento do DICIONÁRIO relacionado com o grupo muscular treinado nesse dia."
                                    );
                                }
                            }
                            arrefecimentoAnterior = enriched.getName();
                        } else if (isFinalizador) {
                            // 4e. Segurança: nunca aceitar finalizador se o aluno não for elegível
                            if (!finalizadorDisponivel) {
                                throw new RuntimeException(
                                        "Exercício finalizador \"" + enriched.getName() + "\" não deveria existir: " +
                                                "o aluno é sedentário, tem patologia relatada ou o objetivo não é compatível com " +
                                                "condicionamento metabólico. Remove este exercício do dia."
                                );
                            }
                            // 4f. Deve pertencer à categoria FINALIZADOR do dicionário
                            String categoriaFinalizador = encontrarCategoria(enriched.getName(), exerciseDictionary);
                            if (categoriaFinalizador == null || !categoriaFinalizador.equalsIgnoreCase("FINALIZADOR")) {
                                throw new RuntimeException(
                                        "O penúltimo exercício do dia (\"" + enriched.getName() + "\") deveria ser um " +
                                                "finalizador metabólico da categoria FINALIZADOR do dicionário, mas pertence a \"" +
                                                categoriaFinalizador + "\". Escolhe um exercício da categoria FINALIZADOR."
                                );
                            }
                            // 4g. Variedade: não repetir em dias consecutivos
                            if (exigirVariedadeFinalizador && enriched.getName().equalsIgnoreCase(finalizadorAnterior)) {
                                throw new RuntimeException(
                                        "Exercício finalizador repetido em dias consecutivos: \"" + enriched.getName() +
                                                "\". Escolhe um finalizador diferente do DICIONÁRIO para este dia."
                                );
                            }
                            finalizadorAnterior = enriched.getName();
                        } else {
                            // Exercícios de TRABALHO: unicidade total no plano + anti-platô vs plano anterior
                            boolean repetidoNoPlano = !nomesUsadosNestaTentativa.add(enriched.getName());
                            boolean repetidoNoHistorico = !repetidoNoPlano && nomesAnteriores.contains(enriched.getName());

                            if (repetidoNoPlano || repetidoNoHistorico) {
                                String categoria = encontrarCategoria(enriched.getName(), exerciseDictionary);
                                String substituto = obterSubstitutoValido(categoria, exerciseDictionary,
                                        nomesUsadosNestaTentativa, nomesAnteriores);

                                if (substituto != null) {
                                    log.warn("[AUTO-SUBSTITUIÇÃO] '{}' -> '{}' (categoria: {}, motivo: {})",
                                            enriched.getName(), substituto, categoria,
                                            repetidoNoPlano ? "duplicado no plano" : "usado no plano anterior");

                                    enriched = enrichExercise(
                                            ex.toBuilder().name(substituto).build(), validNames, pathologyText);
                                    nomesUsadosNestaTentativa.add(enriched.getName());
                                } else {
                                    // Sem alternativa disponível: aí sim, lança exceção (vai para custom ou retry)
                                    throw new RuntimeException(
                                            "Exercício repetido sem alternativa disponível na categoria \"" + categoria +
                                                    "\": \"" + enriched.getName() + "\"."
                                    );
                                }
                            }
                        }

                        enrichedExercises.add(enriched);
                    }

                    List<TrainingExercise> exercisesComEstimativa = anexarEstimativaCalorica(
                            enrichedExercises, exerciseDictionary, weightKg, totalMinutos);

                    updatedPlan.add(new TrainingDay(day.getDay(), exercisesComEstimativa));
                }

                // 5. LIMPEZA DA DIETA (garante listas mutáveis)
                DietPlan diet = response.getDietPlan();
                if (diet != null && diet.getMeals() != null) {
                    List<Meal> mutableMeals = new ArrayList<>();
                    for (Meal m : diet.getMeals()) {
                        if (m.getIngredients() != null) {
                            m.setIngredients(new ArrayList<>(m.getIngredients()));
                        }
                        mutableMeals.add(m);
                    }
                    diet.setMeals(mutableMeals);
                }

                log.info("Geração concluída com sucesso, validada contra inventário, sequência de dias, variedade e correspondência de grupo muscular.");

                // 6. RESPOSTA FINAL
                return TrainingPlanResponse.builder()
                        .isExistingPlan(false)
                        .summary(response.getSummary())
                        .plan(updatedPlan)
                        .dietPlan(diet)
                        .userProfile(null)
                        .build();

                // CÓDIGO ATUAL — ainda o append genérico antigo:
            } catch (Exception e) {
                log.warn("Falha na tentativa {}/{} - Erro: {}", attempt, maxRetries, e.getMessage());

                if (attempt >= maxRetries) {
                    log.error("Todas as tentativas falharam. Erro final: {}", e.getMessage());
                    throw new RuntimeException("Falha crítica na geração do plano.");
                }
                promptBuilder.append(buildFeedbackDeErro(
                        e.getMessage(), nomesUsadosNestaTentativa,
                        pathologyEspecifica, exerciseDictionary, nomesAnteriores));
            }
        }

        throw new RuntimeException("Falha na geração.");
    }

    // Valores MET (Metabolic Equivalent of Task) aproximados por categoria de exercício,
    // usados apenas para dar ao aluno uma ESTIMATIVA de gasto calórico da sessão.
    // Não substitui medição real (frequência cardíaca, VO2), é apenas indicativo.
    private static final Map<String, Double> MET_POR_CATEGORIA = Map.ofEntries(
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
            Map.entry("FINALIZADOR", 8.0)
    );
    private static final double MET_DEFAULT = 4.5; // fallback para categorias desconhecidas/custom

    /**
     * Estima o gasto calórico de uma sessão de treino a partir da fórmula padrão
     * kcal = MET * 3.5 * pesoKg / 200 * minutos, usando o MET médio dos exercícios
     * do dia (ponderado por número de exercícios de cada categoria).
     * É uma estimativa aproximada, não uma medição clínica.
     */
    private Integer estimarCaloriasSessao(List<TrainingExercise> exercises, Map<String, List<String>> dictionary,
                                          Double weightKg, int totalMinutosSessao) {
        if (exercises == null || exercises.isEmpty() || weightKg == null || weightKg <= 0) return null;

        double somaMet = 0;
        int contados = 0;
        for (TrainingExercise ex : exercises) {
            String categoria = encontrarCategoria(ex.getName(), dictionary);
            double met = (categoria != null && MET_POR_CATEGORIA.containsKey(categoria.toUpperCase()))
                    ? MET_POR_CATEGORIA.get(categoria.toUpperCase())
                    : MET_DEFAULT;
            somaMet += met;
            contados++;
        }
        if (contados == 0) return null;

        double metMedio = somaMet / contados;
        double kcal = metMedio * 3.5 * weightKg / 200.0 * totalMinutosSessao;
        return (int) Math.round(kcal);
    }

    /**
     * Anexa a estimativa de calorias da sessão à nota do último exercício do dia
     * (o alongamento/arrefecimento), de forma visível mas não intrusiva para o aluno.
     */
    private List<TrainingExercise> anexarEstimativaCalorica(List<TrainingExercise> enrichedExercises,
                                                            Map<String, List<String>> dictionary,
                                                            Double weightKg, int totalMinutosSessao) {
        Integer kcalEstimadas = estimarCaloriasSessao(enrichedExercises, dictionary, weightKg, totalMinutosSessao);
        if (kcalEstimadas == null || enrichedExercises.isEmpty()) return enrichedExercises;

        int ultimoIndex = enrichedExercises.size() - 1;
        TrainingExercise ultimo = enrichedExercises.get(ultimoIndex);
        String notaOriginal = (ultimo.getNotas() == null || ultimo.getNotas().isBlank()) ? "" : ultimo.getNotas().trim() + " ";
        String notaComEstimativa = notaOriginal + "Estimativa de gasto calórico desta sessão: ~" + kcalEstimadas + " kcal.";

        TrainingExercise atualizado = ultimo.toBuilder().notas(notaComEstimativa).build();
        List<TrainingExercise> resultado = new ArrayList<>(enrichedExercises);
        resultado.set(ultimoIndex, atualizado);
        return resultado;
    }

    /**
     * Deteta o(s) grupo(s) muscular(es) do dia a partir do texto completo do "day"
     * (ex: "Dia 5 - PULL: Costas e Bíceps" -> {COSTAS, BRAÇOS}).
     */
    private Set<String> extrairGruposMuscularesDoDia(String dayLabel) {
        if (dayLabel == null) return Set.of();
        String lower = dayLabel.toLowerCase();
        Set<String> grupos = new HashSet<>();
        for (Map.Entry<String, Set<String>> entry : GRUPO_MUSCULAR_KEYWORDS.entrySet()) {
            for (String keyword : entry.getValue()) {
                if (lower.contains(keyword)) {
                    grupos.add(entry.getKey());
                    break;
                }
            }
        }
        return grupos;
    }

    /**
     * Verifica se um exercício de mobilidade/alongamento é adequado ao(s) grupo(s) do dia.
     * Exercícios não mapeados (gerais) são sempre aceites. Se não foi possível determinar
     * o grupo do dia, também aceita (evita falsos positivos).
     */
    private boolean grupoMuscularCorresponde(String exerciseName, Set<String> gruposDoDia) {
        Set<String> gruposAlvo = GRUPO_ALVO_EXERCICIO_MOBILIDADE.get(exerciseName);
        if (gruposAlvo == null) return true; // exercício genérico, serve para qualquer dia
        if (gruposDoDia.isEmpty()) return true; // não foi possível determinar o grupo do dia
        return !Collections.disjoint(gruposAlvo, gruposDoDia);
    }

    /**
     * Verifica se existe, na lista de opções da categoria (aquecimento ou arrefecimento),
     * pelo menos um exercício ESPECIFICAMENTE adequado ao(s) grupo(s) do dia — usado para
     * só exigir correção quando realmente existir alternativa melhor no dicionário.
     */
    private boolean existeOpcaoMelhorParaGrupo(Set<String> gruposDoDia, List<String> opcoesDaCategoria,
                                               String exercicioEscolhido, String pathologyText) {
        if (gruposDoDia.isEmpty()) return false;
        return opcoesDaCategoria.stream()
                .filter(nome -> !nome.equalsIgnoreCase(exercicioEscolhido))
                .filter(nome -> {
                    // Exclui opções que exigem uma patologia que o aluno não tem
                    String palavraChave = MOBILIDADE_ESPECIFICA_PARA_PATOLOGIA.get(nome);
                    if (palavraChave == null) return true; // não é pathology-gated, pode ser sugerida
                    return pathologyText != null && pathologyText.toLowerCase().contains(palavraChave);
                })
                .anyMatch(nome -> {
                    Set<String> gruposAlvo = GRUPO_ALVO_EXERCICIO_MOBILIDADE.get(nome);
                    return gruposAlvo != null && !Collections.disjoint(gruposAlvo, gruposDoDia);
                });
    }

    private String extrairCategoriaDia(String dayLabel) {
        if (dayLabel == null) return null;
        int idxTraco = dayLabel.indexOf('-');
        int idxDoisPontos = dayLabel.indexOf(':');
        if (idxTraco == -1 || idxDoisPontos == -1 || idxDoisPontos <= idxTraco) return null;
        return dayLabel.substring(idxTraco + 1, idxDoisPontos).trim();
    }

    /**
     * Normaliza, valida contra o inventário e resolve o vídeo de um exercício.
     * Lança exceção com mensagem específica se o nome não existir no dicionário —
     * essa mensagem é reaproveitada no retry para guiar a IA à correção certa.
     */
    private TrainingExercise enrichExercise(TrainingExercise ex, Set<String> validNames, String pathologyText) {
        String correctedName = normalizeExerciseName(ex.getName(), validNames);

        if (!validNames.contains(correctedName)) {
            log.error("[ALERTA DE INVENTÁRIO] Nome fora do dicionário: '{}' (original da IA: '{}')",
                    correctedName, ex.getName());
            throw new RuntimeException(
                    "Exercício fora do inventário: \"" + ex.getName() + "\". " +
                            "Este nome não existe no DICIONÁRIO OFICIAL. Substitui por um nome válido da mesma categoria muscular."
            );
        }
        validarMobilidadeCondicional(correctedName, pathologyText); // NOVA VALIDAÇÃO

        String finalUrl = exerciseVideoService.getVideoUrl(correctedName);

        return TrainingExercise.builder()
                .order(ex.getOrder())
                .name(correctedName)
                .muscleGroup(ex.getMuscleGroup())
                .equipment(ex.getEquipment())
                .intensity(ex.getIntensity())
                .sets(ex.getSets())
                .reps(ex.getReps())
                .rest(ex.getRest())
                .tempo(ex.getTempo())
                .details(ex.getDetails())
                .notas(ex.getNotas())
                .weight(ex.getWeight())
                .cargaAtual(ex.getCargaAtual())
                .videoUrl(finalUrl)
                .date(java.time.LocalDate.now().toString())
                .movementPlane(ex.getMovementPlane())
                .build();
    }

    private Set<String> flattenDictionary(Map<String, List<String>> dictionary) {
        return dictionary.values().stream()
                .flatMap(List::stream)
                .collect(Collectors.toCollection(HashSet::new));
    }

    // Com response_format=json_object, o OpenAI já garante JSON sintaticamente
    // válido (sem ```json fences, sem chavetas por fechar). Este método fica como
    // rede de segurança secundária (ex: se mudares de provider/modelo no futuro)
    // e continua a tratar da vírgula decimal (25,5 -> 25.5), que o JSON mode não resolve.
    private String cleanMarkdown(String text) {
        if (text == null || text.isBlank()) return "{}";
        String cleaned = text.replaceAll("(?s)```json\\s*(.*?)\\s*```", "$1").trim();
        int firstBrace = cleaned.indexOf("{");
        int lastBrace = cleaned.lastIndexOf("}");
        cleaned = (firstBrace != -1 && lastBrace != -1) ? cleaned.substring(firstBrace, lastBrace + 1) : cleaned;

        // Corrige vírgulas decimais dentro de valores numéricos (ex: 25,5 -> 25.5),
        // sem tocar em vírgulas que separam elementos de array/objeto
        cleaned = cleaned.replaceAll("(?<=:\\s?)(\\d+),(\\d+)(?=[,\\}\\s])", "$1.$2");

        return cleaned;
    }
    private String calcularDescansoCientifico(Enum.TrainingProtocol protocol, String objective, String cargaAtual) {
        double peso;
        try {
            peso = Double.parseDouble(cargaAtual.replaceAll("[^0-9.]", ""));
        } catch (Exception e) {
            peso = 0;
        }
        if (objective.equalsIgnoreCase("Força")) return (peso > 80) ? "180" : "120";
        if (objective.equalsIgnoreCase("Hipertrofia")) return (peso > 50) ? "90" : "60";
        return "60";
    }

    /**
     * Descanso para a semana de deload: parte do descanso científico habitual mas
     * acrescenta uma margem fixa, já que o objetivo aqui é recuperação, não sobrecarga
     * progressiva — menos urgência em minimizar o tempo de descanso.
     */
    private String calcularDescansoDeload(Enum.TrainingProtocol protocol, String objective, String cargaAtual) {
        String base = calcularDescansoCientifico(protocol, objective, cargaAtual);
        try {
            int segundosBase = Integer.parseInt(base.replaceAll("[^0-9]", ""));
            return String.valueOf(segundosBase + 30);
        } catch (Exception e) {
            return "90";
        }
    }

    private int extrairMinutosTotais(String durationText) {
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

    /**
     * Garante que o summary termina com uma frase de incentivo curta e ESPECÍFICA
     * ao aluno (nome se disponível, objetivo real, e se é semana de deload) —
     * nunca uma frase genérica tipo "Continua assim!". Aplicado sempre em Java,
     * independentemente do que a IA escreveu, para consistência garantida.
     */
    private String garantirFechoMotivacional(String summary, UserProfileRequest request, boolean isDeloadWeek, int weekNumber) {
        String base = (summary == null || summary.isBlank()) ? "" : summary.trim();
        String fecho = construirFechoMotivacional(request, isDeloadWeek, weekNumber);
        return base.isEmpty() ? fecho : base + " " + fecho;
    }

    private String construirFechoMotivacional(UserProfileRequest request, boolean isDeloadWeek, int weekNumber) {
        String nome = (request.getStudentName() != null && !request.getStudentName().isBlank())
                ? request.getStudentName() : null;
        String vocativo = nome != null ? nome + ", " : "";

        String objectiveLower = defaultIfEmpty(request.getObjective(), "").toLowerCase();

        if (isDeloadWeek) {
            int semanasAnteriores = Math.max(1, weekNumber - 1);
            return vocativo + "esta semana de recuperação é o que vai permitir que o teu corpo absorva " +
                    "todo o trabalho das últimas " + semanasAnteriores + " semanas — não saltes os dias de descanso.";
        }
        if (objectiveLower.contains("glúteo") || objectiveLower.contains("gluteo")) {
            return vocativo + "cada agachamento e elevação pélvica bem executados hoje são investimento " +
                    "direto no resultado que procuras nos glúteos.";
        }
        if (objectiveLower.contains("hipertrofia")) {
            return vocativo + "cada série perto da falha controlada é um estímulo direto para o crescimento " +
                    "muscular que procuras — mantém a técnica e o foco.";
        }
        if (objectiveLower.contains("emagrec") || objectiveLower.contains("perda de gordura")
                || objectiveLower.contains("definição") || objectiveLower.contains("definicao")) {
            return vocativo + "a consistência nestes treinos, semana após semana, é o que realmente separa " +
                    "quem atinge o objetivo de emagrecimento de quem desiste a meio.";
        }
        if (objectiveLower.contains("força")) {
            return vocativo + "a força constrói-se treino a treino — respeita o descanso entre séries, " +
                    "é isso que te vai permitir progredir nas cargas.";
        }
        if (objectiveLower.contains("condicionamento") || objectiveLower.contains("resistência")
                || objectiveLower.contains("resistencia")) {
            return vocativo + "cada sessão de condicionamento que completas hoje é uma vitória contra " +
                    "o teu eu de há algumas semanas.";
        }

        return vocativo + "mantém o foco no objetivo de " + defaultIfEmpty(request.getObjective(), "saúde e bem-estar") +
                " — a consistência, sessão após sessão, é o que faz a diferença.";
    }

    private String defaultIfEmpty(String value, String defaultValue) {
        return (value == null || value.isBlank()) ? defaultValue : value;
    }

    private String buildFeedbackDeErro(String erro, Set<String> nomesUsadosNoPlano,
                                       boolean permiteCustom,
                                       Map<String, List<String>> exerciseDictionary,
                                       Set<String> nomesAnteriores) {

        String listaUsados = nomesUsadosNoPlano.isEmpty()
                ? "(nenhum ainda)"
                : String.join(", ", nomesUsadosNoPlano);

        boolean erroDeAntiPlato = erro != null &&
                (erro.contains("já foi usado no plano anterior") || erro.contains("fora do inventário"));

        String dicaCustom = "";

        if (erroDeAntiPlato) {
            String nomeFalhado = extrairNomeExercicioDoErro(erro);
            String categoria = encontrarCategoria(nomeFalhado, exerciseDictionary);

            if (categoria != null) {
                Set<String> disponiveis = new HashSet<>(exerciseDictionary.getOrDefault(categoria, List.of()));
                disponiveis.removeAll(nomesAnteriores);
                disponiveis.removeAll(nomesUsadosNoPlano);

                if (!disponiveis.isEmpty()) {
                    dicaCustom = """
                        
                        OPÇÕES VÁLIDAS RESTANTES NA CATEGORIA "%s" (escolhe OBRIGATORIAMENTE uma destas, 
                        copiando o nome EXATO — não precisas de "custom"):
                        %s
                        """.formatted(categoria, String.join(", ", disponiveis));
                } else if (permiteCustom) {
                    dicaCustom = """
                        
                        AÇÃO OBRIGATÓRIA: a categoria "%s" está ESGOTADA (todas as opções do dicionário já 
                        foram usadas neste plano ou em planos anteriores deste aluno). É OBRIGATÓRIO usar 
                        "custom": true para o próximo exercício desta categoria. Formato exato:
                        {
                          "custom": true,
                          "name": "<nome do exercício, pode ser fora do dicionário>",
                          "videoUrl": "",
                          "notas": "<justificação técnica concreta>"
                        }
                        Limite: máx. 1 "custom" por dia, 3 no total do plano.
                        """.formatted(categoria);
                } else {
                    dicaCustom = """
                        
                        ATENÇÃO: a categoria "%s" está ESGOTADA no dicionário para este aluno e "custom" não é 
                        permitido (sem patologia relatada). Reduz o número de exercícios desta categoria no plano 
                        ou reaproveita um exercício de uma categoria adjacente compatível (ex.: PEITO -> OMBROS).
                        """.formatted(categoria);
                }
            }
        }

        return """
            
            ERRO NA TENTATIVA ANTERIOR: %s
            
            EXERCÍCIOS DE TRABALHO JÁ USADOS NESTE PLANO (NÃO REPETIR): %s
            %s
            CORRIGE ISTO NA PRÓXIMA RESPOSTA E GERA O PLANO COMPLETO DE NOVO, respeitando 
            TODAS as regras anteriores, não só a última mencionada.
            """.formatted(erro, listaUsados, dicaCustom);
    }

    @NotNull
    private String buildDiretrizRelatorioMedico(String medicalReportText, String pathologyText) {
        if (medicalReportText == null || medicalReportText.isBlank()) {
            return ""; // filtrado pelo Stream — sem impacto quando não há ficheiro
        }

        return """
                [RELATÓRIO MÉDICO ANEXADO PELO ALUNO]
                - O aluno anexou um relatório médico/exame. Usa-o como CONTEXTO ADICIONAL DE SEGURANÇA, 
                  em complemento (nunca substituição) da patologia já declarada: "%s".
                - NÃO FAÇAS NOVOS DIAGNÓSTICOS OU INTERPRETAÇÕES CLÍNICAS — usa apenas achados 
                  explicitamente escritos no relatório para tornar a seleção de exercícios mais segura.
                - Se o relatório mencionar qualquer achado na coluna lombar (ex: hérnia discal, 
                  protrusão discal, retrolistese, discopatia, radiculopatia, conflito com raiz nervosa), 
                  aplica as mesmas restrições já usadas para patologia lombar: evita flexão lombar sob 
                  carga, evita impacto/saltos, evita cargas axiais elevadas, prioriza estabilização 
                  (Dead Bug, Bird Dog, Prancha Abdominal) em vez de exercícios de flexão de tronco.
                - Se mencionar achados no ombro, joelho ou anca, aplica lógica equivalente: prioriza 
                  estabilidade e amplitude segura nessa articulação em vez de sobrecarga.
                - Se o relatório não tiver relação óbvia com exercício físico, ignora-o e usa apenas 
                  a patologia declarada.
                - CONTEÚDO DO RELATÓRIO (pode estar truncado):
                \"\"\"
                %s
                \"\"\"
                """.formatted(pathologyText, medicalReportText);
    }

    @NotNull
    private static String buildDiretrizAlimentar(Macros macros) {
        StringBuilder dietTable = new StringBuilder();
        for (MealSuggestion m : macros.mealSuggestions()) {
            dietTable.append("- %s (%s): %d kcal [P: %dg, C: %dg, G: %dg]\n".formatted(
                    m.name(), m.time(), (int) (macros.dailyCalories() * m.pctCalories()),
                    (int) (macros.protein() * m.pctProtein()), (int) (macros.carbs() * m.pctCarbs()), (int) (macros.fats() * m.pctFats())
            ));
        }
        return "DIRETRIZES ALIMENTARES:\n" + dietTable + "\nEscolha ingredientes que somem estes totais.";
    }

    /**
     * Gera a sequência de divisões de treino (splits) para a frequência semanal,
     * garantindo que nunca há o mesmo grupo muscular em dias consecutivos.
     */
    private List<String> gerarSequenciaDivisao(int frequencia, boolean isGluteFocus) {
        List<String> sequencia = new ArrayList<>();

        if (frequencia <= 1) {
            sequencia.add("FULL BODY: Corpo Inteiro");
            return sequencia;
        }

        if (frequencia == 2) {
            String[] ciclo = {
                    "SUPERIORES: Peito, Costas, Ombros e Braços",
                    "INFERIORES: Pernas e Glúteos"
            };
            for (int i = 0; i < frequencia; i++) sequencia.add(ciclo[i % ciclo.length]);
            return sequencia;
        }

        // Ciclo ordenado para que o primeiro e o último elemento nunca coincidam
        // na mesma categoria quando o ciclo dá a volta (importante para 4, 5, 6... dias)
        List<String> ciclo = isGluteFocus
                ? List.of(
                "LEGS: Glúteos e Posterior de Coxa",
                "PUSH: Peito, Ombros e Tríceps",
                "LEGS: Quadríceps e Glúteos",
                "PULL: Costas e Bíceps"
        )
                : List.of(
                "PUSH: Peito, Ombros e Tríceps",
                "PULL: Costas e Bíceps",
                "LEGS: Quadríceps, Posterior e Glúteos"
        );

        for (int i = 0; i < frequencia; i++) {
            sequencia.add(ciclo.get(i % ciclo.size()));
        }
        return sequencia;
    }

    @NotNull
    private String buildDiretrizTreino(UserProfileRequest userRequest, String durationText, int volumeIdeal) {
        boolean isGluteFocus = userRequest.getObjective() != null &&
                (userRequest.getObjective().toLowerCase().contains("glúteo") ||
                        userRequest.getObjective().toLowerCase().contains("gluteo"));

        List<String> sequencia = gerarSequenciaDivisao(userRequest.getFrequencyPerWeek(), isGluteFocus);
        String divisao = String.join(" -> ", sequencia);

        return "REGRAS DE DIVISÃO: " + divisao + " | Duração: " + durationText + " | Volume: " + volumeIdeal + " ex/dia.";
    }

    @NotNull
    private static String buildDiretrizReabilitacao(String pathologyText) {
        StringBuilder correcao = new StringBuilder();
        String lower = pathologyText.toLowerCase();
        if (lower.contains("tornozelo")) correcao.append("- Mobilidade de Tornozelo, Propriocepção.\n");
        if (lower.contains("joelho")) correcao.append("- Ativação de Vasto Medial, Clamshell.\n");
        if (lower.contains("lombar")) correcao.append("- Deadbug, Cat-Cow, Bird-Dog.\n");
        if (lower.contains("ombro")) correcao.append("- Rotação Externa, Y-Raise.\n");
        return "REABILITAÇÃO ANATÓMICA:\n" + correcao;
    }

    // --- Sinónimos: normaliza variações comuns da IA para o nome oficial ---
    private static final Map<String, String> EXERCISE_MAP;

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

        // Sinónimos para os nomes canónicos únicos escolhidos no FALLBACK_DICTIONARY
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

    // Palavras-chave para identificar o(s) grupo(s) muscular(es) do dia a partir do texto "day" (ex: "Dia 3 - PULL: Costas e Bíceps")
    private static final Map<String, Set<String>> GRUPO_MUSCULAR_KEYWORDS = Map.of(
            "PEITO", Set.of("peito", "supino", "crossover", "peck", "flex"),
            "COSTAS", Set.of("costas", "dorsal", "remada", "puxada", "pull"),
            "PERNAS", Set.of("pernas", "perna", "quadríceps", "quadriceps", "isquio", "gluteo", "glúteo", "agachamento", "leg", "coxa", "anca", "tornozelo", "gémeo", "gemeo"),
            "OMBROS", Set.of("ombro", "deltoide", "lateral", "desenvolvimento", "arnold"),
            "BRAÇOS", Set.of("braço", "braco", "tríceps", "triceps", "bíceps", "biceps", "rosca")
    );

    // Mapeia exercícios de mobilidade/alongamento específicos para o(s) grupo(s) muscular(es) que servem.
// Exercícios NÃO listados aqui são considerados GERAIS e servem para qualquer dia (ex: "Cat Cow").
    private static final Map<String, Set<String>> GRUPO_ALVO_EXERCICIO_MOBILIDADE = Map.ofEntries(
            Map.entry("Knee-to-Wall", Set.of("PERNAS")),
            Map.entry("Cossack Squat", Set.of("PERNAS")),
            Map.entry("Y-W-T", Set.of("COSTAS", "OMBROS")),
            Map.entry("Open Books", Set.of("COSTAS", "OMBROS")),
            Map.entry("Flexores da Anca", Set.of("PERNAS")),
            Map.entry("Alongamento borboleta", Set.of("PERNAS")),
            Map.entry("Alongamento do músculo grande dorsal", Set.of("COSTAS")),
            Map.entry("Walking Lunges with Reach", Set.of("PERNAS")),
            Map.entry("Frankenstein Walk", Set.of("PERNAS"))
    );

    private String normalizeExerciseName(String aiSuggestion, Set<String> validNames) {
        // ... lógica atual (limpeza + EXERCISE_MAP) ...

        String cleanSuggestion = aiSuggestion.trim().replaceAll("[.,!?]$", "");
        if (validNames.contains(cleanSuggestion)) return cleanSuggestion;

        // Fallback: aproxima ao nome válido mais próximo (evita gastar retry em quase-acertos)
        String lower = cleanSuggestion.toLowerCase();
        String melhorMatch = null;
        int menorDistancia = Integer.MAX_VALUE;
        for (String valido : validNames) {
            int distancia = StringUtils.getLevenshteinDistance(lower, valido.toLowerCase());
            // só aceita se for claramente próximo, para não confundir categorias diferentes
            if (distancia < menorDistancia && distancia <= Math.max(3, valido.length() / 4)) {
                menorDistancia = distancia;
                melhorMatch = valido;
            }
        }
        if (melhorMatch != null) {
            log.warn("[AUTO-CORREÇÃO POR PROXIMIDADE] '{}' -> '{}'", cleanSuggestion, melhorMatch);
            return melhorMatch;
        }

        return cleanSuggestion; // deixa cair na validação normal (vai gerar o erro/retry como hoje)
    }

    // --- Fallback estático usado quando a BD está indisponível ou vazia ---
    private static final Map<String, List<String>> FALLBACK_DICTIONARY;

    static {
        Map<String, List<String>> map = new java.util.LinkedHashMap<>();

        map.put("PEITO", List.of(
                "Supino Plano", "Supino Inclinado", "Supino Declinado",
                "Supino Plano com Halteres", "Supino Inclinado com Halteres", "Supino Declinado com Halteres",
                "Peck Deck", "Crossover", "Crossover Baixo para Cima",
                "Flexões", "Flexões Diamond", "Flexões com Pés Elevados",
                "Dips", "Aberturas com Halteres", "Aberturas Inclinadas com Halteres",
                "Supino Máquina", "Pullover com Halter", "Chest Press Máquina"
        ));
        map.put("COSTAS", List.of(
                "Puxada à Frente", "Puxada Pega Estreita", "Puxada Pega Neutra",
                "Remada Curvada", "Remada Curvada com Halteres", "Remada Unilateral",
                "Pulldown Corda", "Remada Baixa", "Remada Cavalinho",
                "Elevações", "Remada Máquina", "Remada T-Bar",
                "Face Pull", "Encolhimentos com Halteres", "Extensão Lombar",
                "Puxada com Corda Neutra", "Remada Invertida"
        ));
        map.put("PERNAS", List.of(
                "Agachamento Livre", "Agachamento Goblet", "Agachamento Búlgaro",
                "Agachamento Sumô", "Leg Press 45", "Hack Squat",
                "Cadeira Extensora", "Mesa Flexora", "Cadeira Flexora em Pé",
                "Stiff", "Stiff Unilateral", "Elevação Pélvica",
                "Elevação Pélvica Unilateral", "Lunge", "Lunge Reverso",
                "Gémeos em Pé", "Gémeos Sentado", "Abdução de Anca na Máquina",
                "Adução de Anca na Máquina", "Step Up"
        ));
        map.put("OMBROS", List.of(
                "Desenvolvimento", "Desenvolvimento com Halteres", "Arnold Press",
                "Elevação Lateral", "Elevação Lateral Polia", "Elevação Lateral Máquina",
                "Elevação Frontal", "Elevação Frontal com Barra", "Face Pull",
                "Remada Alta", "Crucifixo Invertido", "Crucifixo Invertido na Máquina",
                "Desenvolvimento Máquina", "Encolhimentos com Barra"
        ));
        map.put("BRAÇOS", List.of(
                "Rosca Direta", "Rosca Direta com Barra EZ", "Rosca Martelo",
                "Rosca Concentrada", "Rosca Scott", "Rosca Alternada com Halteres",
                "Tríceps Corda", "Tríceps Pulley", "Tríceps Testa",
                "Tríceps Testa com Halter", "Tríceps Francês", "Mergulho no Banco",
                "Tríceps Coice com Halter", "Rosca no Cabo"
        ));
        map.put("CORE", List.of(
                "Dead Bug", "Dead Bug com Carga", "Prancha Abdominal",
                "Prancha Lateral", "Bird Dog", "Abdominal na Polia",
                "Elevação de Pernas Suspenso", "Abdominal na Bola Suíça", "Russian Twist",
                "Prancha com Toque no Ombro"
        ));
        map.put("REAB/MOBILIDADE", List.of(
                "Cat Cow", "Clamshell", "Y-W-T", "Rotação Externa",
                "Knee-to-Wall", "Cossack Squat", "Equilíbrio Unipodal",
                "Open Books", "Bird Dog Isométrico", "Mobilidade Torácica"
        ));
        map.put("ALONGAMENTO", List.of(
                "Alongamento Estático", "Alongamento Dinâmico", "Foam Roller",
                "Alongamento Isquiotibiais", "Alongamento Peitoral",
                "Alongamento do músculo grande dorsal", "Flexores da Anca",
                "Alongamento borboleta", "Walking Lunges with Reach", "Frankenstein Walk"
        ));
        map.put("FINALIZADOR", List.of(
                "Burpees", "Mountain Climbers", "Kettlebell Swing",
                "Corda de Saltar", "Battle Rope", "Jump Squat",
                "Sprint na Bike Estática", "Remo Curto Intervalado", "Circuito Metabólico AMRAP"
        ));

        FALLBACK_DICTIONARY = Collections.unmodifiableMap(map);
    }

    private String buildDiretrizDicionario(Map<String, List<String>> dictionary) {
        StringBuilder sb = new StringBuilder("""
                ### [REGRA DE OURO: RESTRIÇÃO RÍGIDA DE INVENTÁRIO] ###
                O sistema de vídeo FALHARÁ se usares um nome fora deste dicionário. \
                É OBRIGATÓRIO copiar o nome EXATO (incluindo acentuação e maiúsculas/minúsculas) \
                de um dos exercícios listados abaixo — nunca inventes, combines, traduzas ou abrevies nomes.
                
                [ERROS COMUNS A EVITAR — exemplos reais de falhas anteriores]
                - "Prancha" -> usa "Prancha Abdominal"
                - "Supino Reto" -> usa "Supino Plano"
                - "Alongamento" -> usa "Cat Cow", "Y-W-T" ou "Mobilidade do Tornozelo"
                - "Cadeira Flexora" -> usa "Mesa Flexora"
                - "Dips / Paralelas" -> usa apenas "Dips"
                
                [REGRAS DE CORRESPONDÊNCIA EXATA]
                - Copia o nome literalmente da lista, carácter a carácter.
                - Não uses sinónimos, traduções livres, plurais ou variações estéticas do nome.
                - Se nenhum exercício da lista servir perfeitamente para o grupo muscular pretendido, escolhe o mais próximo disponível NA MESMA CATEGORIA — nunca inventes um nome novo.
                
                [DICIONÁRIO OFICIAL DE EXERCÍCIOS DISPONÍVEIS]
                """);

        dictionary.forEach((categoria, nomes) ->
                sb.append("- ").append(categoria.toUpperCase())
                        .append(": ").append(String.join(", ", nomes))
                        .append("\n")
        );

        sb.append("""
                
                Nota: Se um exercício for de mobilidade/reabilitação (categoria REAB/MOBILIDADE), \
                ele DEVE ser o primeiro exercício do treino (Order 1).
                """);

        sb.append("""
                
                [EXCEÇÃO CONTROLADA: EXERCÍCIO FORA DO INVENTÁRIO]
                - Podes propor um exercício fora desta lista em dois casos:
                  (a) É ESTRITAMENTE ESSENCIAL para a patologia relatada e não existe opção adequada no dicionário; ou
                  (b) É CLARAMENTE SUPERIOR a qualquer opção do dicionário para o objetivo específico do aluno 
                      (ex: melhor ativação do grupo muscular alvo, melhor progressão biomecânica).
                - Nestes casos: marca "custom": true no JSON, deixa "videoUrl": "" e explica em "notas" 
                  de forma CONCRETA porque nenhuma opção do dicionário serve (mínimo 1 frase técnica, 
                  nunca genérica como "é melhor" sem justificação).
                - LIMITE ABSOLUTO: no máximo 1 exercício "custom" por dia, e 3 no total do plano.
                - PRIORIDADE: usa sempre o dicionário primeiro. "custom" é EXCEÇÃO, não regra — 
                  se existir uma opção razoável no dicionário, usa-a em vez de "custom".
                """);

        return sb.toString();
    }

    private static final Map<String, String> MOBILIDADE_ESPECIFICA_PARA_PATOLOGIA = Map.of(
            "Rotação Externa", "ombro",
            "Clamshell", "joelho"
    );

    private void validarMobilidadeCondicional(String exerciseName, String pathologyText) {
        String palavraChave = MOBILIDADE_ESPECIFICA_PARA_PATOLOGIA.get(exerciseName);
        if (palavraChave != null) {
            boolean patologiaCorresponde = pathologyText != null
                    && pathologyText.toLowerCase().contains(palavraChave);

            if (!patologiaCorresponde) {
                throw new RuntimeException(
                        "Exercício de mobilidade específica \"" + exerciseName +
                                "\" usado sem a patologia correspondente (\"" + palavraChave +
                                "\"). O aluno relatou: \"" + pathologyText + "\". " +
                                "Usa mobilidade GERAL em vez disso (Cat Cow, Y-W-T, Dead Bug, Bird Dog, Equilíbrio Unipodal)."
                );
            }
        }
    }

    private static final Map<String, String> ACHADOS_MEDICOS_PARA_PATOLOGIA = Map.ofEntries(
            Map.entry("retrolistese", "Lesão Lombar"),
            Map.entry("discopatia", "Lesão Lombar"),
            Map.entry("protrusão discal", "Hérnia de Disco"),
            Map.entry("protrusao discal", "Hérnia de Disco"),
            Map.entry("hérnia discal", "Hérnia de Disco"),
            Map.entry("hernia discal", "Hérnia de Disco"),
            Map.entry("radicular", "Lesão Lombar"),
            Map.entry("lombar", "Lesão Lombar"),
            Map.entry("espondilose", "Lesão Lombar"),
            Map.entry("espondilólise", "Lesão Lombar"),
            Map.entry("escoliose", "Lesão Lombar"),
            Map.entry("manguito rotador", "Lesão Ombro"),
            Map.entry("bursite subacromial", "Lesão Ombro"),
            Map.entry("tendinite do supraespinhoso", "Lesão Ombro"),
            Map.entry("capsulite adesiva", "Lesão Ombro"),
            Map.entry("luxação do ombro", "Lesão Ombro"),
            Map.entry("menisco", "Lesão Joelho"),
            Map.entry("ligamento cruzado", "Lesão Joelho"),
            Map.entry("condromalácia", "Lesão Joelho"),
            Map.entry("condromalacia", "Lesão Joelho"),
            Map.entry("tendinite patelar", "Lesão Joelho"),
            Map.entry("síndrome patelofemoral", "Lesão Joelho"),
            Map.entry("entorse", "Lesão tornozelo"),
            Map.entry("fascite plantar", "Lesão tornozelo"),
            Map.entry("tendinite de aquiles", "Lesão tornozelo"),
            Map.entry("tendinite do tendão de aquiles", "Lesão tornozelo"),
            Map.entry("epicondilite", "Lesão Cotovelo"),
            Map.entry("túnel cárpico", "Lesão Punho"),
            Map.entry("tunel carpico", "Lesão Punho")
    );

    private PatologiaInferida inferirPatologiaDoRelatorio(String medicalReportText) {
        if (medicalReportText == null || medicalReportText.isBlank()) return null;
        String lower = medicalReportText.toLowerCase();

        // Agrupa os termos encontrados por categoria, preservando a ordem de deteção
        LinkedHashMap<String, List<String>> categoriaParaTermos = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : ACHADOS_MEDICOS_PARA_PATOLOGIA.entrySet()) {
            if (lower.contains(entry.getKey())) {
                categoriaParaTermos
                        .computeIfAbsent(entry.getValue(), k -> new ArrayList<>())
                        .add(entry.getKey());
            }
        }

        if (categoriaParaTermos.isEmpty()) return null;

        String categoriasJuntas = String.join(", ", categoriaParaTermos.keySet());
        Set<String> todosTermos = categoriaParaTermos.values().stream()
                .flatMap(List::stream)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        return new PatologiaInferida(categoriasJuntas, todosTermos);
    }

    private static final java.util.regex.Pattern NOME_EXERCICIO_PATTERN =
            java.util.regex.Pattern.compile("\"([^\"]+)\"");

    private String extrairNomeExercicioDoErro(String erro) {
        if (erro == null) return null;
        java.util.regex.Matcher m = NOME_EXERCICIO_PATTERN.matcher(erro);
        return m.find() ? m.group(1) : null;
    }

    private String encontrarCategoria(String nomeExercicio, Map<String, List<String>> dictionary) {
        if (nomeExercicio == null) return null;
        for (Map.Entry<String, List<String>> entry : dictionary.entrySet()) {
            if (entry.getValue().stream().anyMatch(n -> n.equalsIgnoreCase(nomeExercicio))) {
                return entry.getKey();
            }
        }
        return null;
    }
    /**
     * Tenta obter um exercício de substituição válido da mesma categoria,
     * evitando repetições no plano atual e no histórico do aluno.
     * Devolve null se não houver alternativa disponível.
     */
    private String obterSubstitutoValido(String categoria, Map<String, List<String>> dictionary,
                                         Set<String> nomesUsadosNestaTentativa, Set<String> nomesAnteriores) {
        if (categoria == null) return null;
        return dictionary.getOrDefault(categoria, List.of()).stream()
                .filter(nome -> !nomesUsadosNestaTentativa.contains(nome))
                .filter(nome -> !nomesAnteriores.contains(nome))
                .findFirst()
                .orElse(null);
    }

    // Padrão simples: identificadores de código costumam ser snake_case ou
    // camelCase sem espaços e sem acentuação. Um histórico real de exercício
    // (ex: "sedentary", "beginner", "3 anos de ginásio") é texto legível.
    // Isto é uma rede de segurança — a causa raiz deve ser corrigida na
    // origem dos dados (DTO/controller que preenche exerciseHistory).
    private static final java.util.regex.Pattern PADRAO_IDENTIFICADOR_SUSPEITO =
            java.util.regex.Pattern.compile("^[a-z]+(_[a-z]+)+$");

    private String sanitizarExerciseHistory(String valor) {
        if (valor == null || valor.isBlank()) return "Não informado";

        if (PADRAO_IDENTIFICADOR_SUSPEITO.matcher(valor.trim()).matches()
                && !valor.equalsIgnoreCase("sedentary")) { // "sedentary" é um valor válido conhecido
            log.warn("[DADOS SUSPEITOS] exerciseHistory parece um identificador de código, não texto legível: '{}'. " +
                    "Verificar a origem deste valor no DTO/controller.", valor);
            return "Não informado";
        }
        return valor;
    }
}