package com.aneto.registo_horas_service.models.Training;

import com.aneto.registo_horas_service.dto.request.UserProfileRequest;
import com.aneto.registo_horas_service.dto.response.Macros;
import com.aneto.registo_horas_service.dto.response.MealSuggestion;
import com.aneto.registo_horas_service.dto.response.TrainingPlanResponse;
import com.aneto.registo_horas_service.models.Enum;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jetbrains.annotations.NotNull;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Component;

@Component
public record Training(ChatModel chatModel, ObjectMapper objectMapper) {

    public TrainingPlanResponse generateTrainingPlan(UserProfileRequest userRequest) {

        // 1. Sanitização de entradas
        String protocolId = defaultIfEmpty(userRequest.protocol(), "nasm_estabilizacao");
        String pathologyText = defaultIfEmpty(userRequest.pathology(), "Nenhuma limitação relatada");
        String historyText = defaultIfEmpty(userRequest.exerciseHistory(), "Não informado");
        String objectiveText = defaultIfEmpty(userRequest.objective(), "Manutenção de saúde e bem-estar");
        String locationText = defaultIfEmpty(userRequest.location(), "Não especificada");
        String countryText = defaultIfEmpty(userRequest.country(), "Não especificado");
        String bodyTypeText = defaultIfEmpty(userRequest.bodyType(), "Ectomorfo");
        String genderText = defaultIfEmpty(userRequest.gender(), "Não especificado");

        // 2. Dados do Protocolo e Macros
        Enum.TrainingProtocol protocol = Enum.TrainingProtocol.fromId(protocolId);

        Double bodyFat = userRequest.bodyFat() != null ? userRequest.bodyFat() : 15.0;

        int mealsPerDay = userRequest.mealsPerDay() != null ? userRequest.mealsPerDay() : 6;

        Macros macros = MacroCalculator.calculate(
                userRequest.weightKg(), userRequest.heightCm(), userRequest.age(),
                genderText, bodyTypeText, bodyFat, mealsPerDay
        );

        // --- SEPARAÇÃO DAS REGRAS (DIRETRIZES) ---

        String diretrizProtocolo = """
                DIRETRIZES TÉCNICAS (%s):
                - Séries: %s | Repetições: %s | Descanso: %s | Ritmo (Tempo): %s
                """.formatted(protocol.getLabel(), protocol.getSets(), protocol.getReps(), protocol.getRest(), protocol.getTempo());

        String diretrizBiomecanica = """
                DIRETRIZES DE SELEÇÃO BIOMECÂNICA AVANÇADA:
                1. EXERCÍCIOS SUGERIDOS: %s
                2. EXERCÍCIOS PROIBIDOS: %s
                3. ADAPTAÇÃO PATOLÓGICA: Sendo a patologia "%s", substitui exercícios de alto impacto por variantes de baixo torque.
                4. PERFIL DE RESISTÊNCIA: Ajusta a curva de força para que o ponto mais difícil do exercício não coincida com a posição de maior vulnerabilidade da articulação lesionada.
                5. ESTABILIZAÇÃO: Prioriza exercícios que exijam ativação do Core para estabilizar a coluna antes de mover as extremidades.
                """.formatted(protocol.getSuggestedExercises(), protocol.getForbiddenExercises(), pathologyText);

        String diretrizAquecimento = """
                REGRA OBRIGATÓRIA DE AQUECIMENTO:
                - Se houver exercício de Reabilitação, este aquecimento deve ser o "order": 2.
                - Caso contrário, deve ser o "order": 1.
                - Se for Cárdio (ex: Passadeira): use reps como tempo (ex: "10 min").
                - Foco específico na região: %s.
                """.formatted(pathologyText);

// 1. Defina as duas diretrizes
        String diretrizTreino = getString(userRequest, pathologyText, protocol);

        String diretrizAlimentar = getString(macros);

        String diretrizRepertorio = """
                EXPANSÃO DE REPERTÓRIO:
                - Podes escolher qualquer exercício da tua base de dados que respeite a biomecânica do protocolo %s.
                - Objetivo: %s. Frequência: %d dias.
                """.formatted(protocol.getLabel(), objectiveText, userRequest.frequencyPerWeek());

        String diretrizEquipamento = """
                LOGÍSTICA DE EQUIPAMENTO:
                - Identifica o equipamento necessário no campo "equipment".
                - Localização: %s. Se for "Casa", foca em Peso Corporal/Halteres.
                """.formatted(locationText);

        String diretrizArrefecimento = """
                REGRA DE ARREFECIMENTO (VOLTA À CALMA):
                - O ÚLTIMO exercício de cada dia deve ser obrigatoriamente Arrefecimento ou Alongamento Estático.
                - No campo "reps", especifica o tempo (ex: "30-60 seg por posição").
                - No campo "details", descreve o ritmo: "Ritmo decrescente, foco na respiração profunda e relaxamento muscular".
                - Personaliza os alongamentos para os músculos solicitados no dia e para a patologia: %s.
                """.formatted(pathologyText);

        String diretrizIntensidade = """
                DIRETRIZ DE INTENSIDADE E ESFORÇO (RPE):
                - Deves atribuir obrigatoriamente um valor ao campo "intensity" para cada exercício.
                - Critérios de atribuição:
                  1. "BAIXA": Para o primeiro exercício (Mobilidade/Cárdio) e o último (Alongamento).
                  2. "MODERADA": Para exercícios acessórios ou técnicos.
                  3. "ALTA": Para exercícios multiarticulares base do protocolo %s.
                - Este campo controla a cor dinâmica na interface do aluno.
                """.formatted(protocol.getLabel());

        String diretrizNomenclaturaDias = """
                REGRAS CRÍTICAS DE NOMENCLATURA (Campo "day"):
                1. PROIBIDO gerar treinos idênticos ou repetir categorias. Cada dia DEVE ter exercícios de grupos diferentes.
                2. PADRÃO OBRIGATÓRIO: "Dia X - [CATEGORIA]: [Foco Principal]"
                3. DISTRIBUIÇÃO CONFORME FREQUÊNCIA:
                   - Frequência 3: Dia 1: SUPERIOR, Dia 2: INFERIOR, Dia 3: POSTERIOR ou CORE.
                   - Frequência 2: Dia 1: SUPERIOR, Dia 2: INFERIOR.
                4. ADAPTAÇÃO: Sendo a patologia "%s", o Foco Principal deve refletir o cuidado (Ex: "Dia X - [CAT]: Mobilidade de %s").
                """.formatted(pathologyText, pathologyText);

        String diretrizReabilitacao = getString(pathologyText);

        // --- MONTAGEM FINAL DO PROMPT ---
        String userPrompt = """
                ESTRITA REGRA: RESPONDA APENAS O JSON.
                NÃO DIGA "OLÁ", NÃO DÊ EXPLICAÇÕES FORA DO JSON.
                
                Atue como um Personal Trainer e Nutricionista especialista em reabilitação física e metodologias de treino.
                Gere um plano de treino e um plano alimentar personalizado em Português.
                
                PERFIL DO ALUNO:
                - Idade: %d anos | Biótipo: %s | Género: %s
                - Altura: %.2f cm | Peso: %.2f kg
                - Histórico: %s | Patologias: %s
                - Localização: %s, %s | Objetivo: %s
                
                %s
                %s
                %s
                %s
                %s
                %s
                %s
                %s
                %s
                %s
                %s
                
                FORMATO DE RESPOSTA (JSON APENAS):
                {
                 "summary": "Explicação técnica da estratégia %s (Fórmula: %s) para %s, considerando %d anos e a patologia %s.",
                  "plan": [
                    {
                      "day": "Dia 1 - SUPERIOR: Peitoral e Tríceps",
                      "exercises": [
                        {
                          "order": 1,
                          "name": "Nome",
                          "muscleGroup": "Grupo Muscular (Ex: Quadríceps, Isquiotibiais )",
                          "intensity": "ALTA | MODERADA | BAIXA",
                          "tempo": "%s",
                          "equipment": "Tipo de Equipamento",
                          "sets": "%s",
                          "reps": "%s",
                          "rest": "%s",
                          "details": "Dica de segurança para %s",
                          "notas": "JUSTIFICATIVA: Por que este exercício é ideal para %s e seguro para %s considerando o ritmo %s?"
                        }
                      ]
                    }
                  ],
                  "dietPlan": {
                    "methodology": "%s",
                    "dailyCalories": %d,
                    "imc": %.2f,
                    "imcCategory": "%s",
                    "statusSummary": "%s",
                    "macroDistribution": { "protein": "%dg", "carbs": "%dg", "fats": "%dg" },
                    "meals": [
                      {
                        "time": "HH:MM",
                        "description": "Nome da Refeição",
                        "ingredients": [
                          "Ex: 150g de Peito de Frango Grelhado - Fonte de proteína"
                        ],
                        "calories": 0,
                        "protein": 0,
                        "carbs": 0,
                        "fats": 0
                      }
                    ],
                    "localTips": "Dicas para %s, %s."
                  }
                }
                
                REGRAS CRÍTICAS PARA A DIETA:
                1. O campo "ingredients" DEVE ser preenchido com uma lista detalhada de alimentos e quantidades.
                2. Para cada refeição, os campos "calories", "protein", "carbs" e "fats" DEVEM ser números inteiros.
                3. A soma total deve ser aproximadamente: %d kcal, %dg Prot, %dg Carbs e %dg Fats.
                """.formatted(
                // 1-10: Perfil
                userRequest.age(), bodyTypeText, genderText, userRequest.heightCm(), userRequest.weightKg(),
                historyText, pathologyText, locationText, countryText, objectiveText,

                // 11-21: Blocos de Regras
                diretrizProtocolo, diretrizBiomecanica, diretrizAquecimento, diretrizTreino, diretrizAlimentar, diretrizRepertorio,
                diretrizEquipamento, diretrizArrefecimento, diretrizIntensidade, diretrizNomenclaturaDias, diretrizReabilitacao,

                // 22-26: JSON Summary
                protocol.getLabel(), macros.formula(), objectiveText, userRequest.age(), pathologyText,

                // 27-30: JSON Exercício
                protocol.getTempo(), protocol.getSets(), protocol.getReps(), protocol.getRest(),

                // 31-34: JSON Justificativas
                pathologyText, protocol.getLabel(), pathologyText, protocol.getTempo(),

                // 35-42: JSON Diet (Metodologia, Calorias, IMC, Categoria, Status, Macros)
                macros.formula(), macros.dailyCalories(), macros.imc(), macros.imcCategory(), macros.statusSummary(),
                macros.protein(), macros.carbs(), macros.fats(),

                // 43-44: Dicas Locais
                locationText, countryText,

                // 45-48: REGRAS CRÍTICAS FINAIS
                macros.dailyCalories(), macros.protein(), macros.carbs(), macros.fats()
        );
        return executeGeneration(userPrompt);
    }

    @NotNull
    private static String getString(Macros macros) {
        StringBuilder dietTable = new StringBuilder();
        for (MealSuggestion m : macros.mealSuggestions()) { // Alterado para macros.meals() conforme o novo record
            dietTable.append("- %s (%s): %d kcal [P: %dg, C: %dg, G: %dg]\n".formatted(
                    m.name(),
                    m.time(),
                    (int) (macros.dailyCalories() * m.pctCalories()),
                    (int) (macros.protein() * m.pctProtein()),
                    (int) (macros.carbs() * m.pctCarbs()),
                    (int) (macros.fats() * m.pctFats())
            ));
        }

        return """
                ANÁLISE BIOMÉTRICA: %s
                CATEGORIA DE IMC: %s
                
                DIRETRIZES ALIMENTARES ESTRITAS (PROIBIDO ALTERAR VALORES):
                1. O plano deve conter EXATAMENTE estas %d refeições e valores:
                %s
                2. REGRA DE CÁLCULO: Para cada refeição, escolhe ingredientes que somem exatamente os valores acima.
                3. VALIDAÇÃO: A soma final das refeições no JSON deve ser: %d kcal, %dg P, %dg C, %dg G.
                4. FOCO NUTRICIONAL: %s
                """.formatted(
                macros.statusSummary(),
                macros.imcCategory(),
                macros.mealSuggestions().size(),
                dietTable.toString(),
                macros.dailyCalories(), macros.protein(), macros.carbs(), macros.fats(),
                macros.statusSummary() // Repetimos para dar ênfase
        );
    }


    @NotNull
    private static String getString(UserProfileRequest userRequest, String pathologyText, Enum.TrainingProtocol protocol) {
        String durationText = (userRequest.duration() != null && !userRequest.duration().isBlank())
                ? userRequest.duration() : "aprox. 60 minutos";

        return """
                DIRETRIZES DE ESTRUTURA E BIOMECÂNICA (REGRAS CRÍTICAS):
                1. DIVISÃO E DURAÇÃO: Exatamente %d dias por semana, com sessões de %s.
                2. MÉTODO DE DIVISÃO (ANTI-REPETIÇÃO): Se frequência >= 3, PROIBIDO repetir o mesmo treino "Full Body".
                   - Deves obrigatoriamente separar por grupos (Ex: Superior, Inferior, Posterior).
                3. VARIAÇÃO DE PLANOS (OBRIGATÓRIO): Cada treino deve conter exercícios nos 3 planos:
                   - PLANO SAGITAL: Frente/Trás (ex: Agachamento, Flexão).
                   - PLANO FRONTAL: Lateral (ex: Abdução, Elevação Lateral).
                   - PLANO TRANSVERSAL: Rotação (ex: Woodchop, Rotação de Tronco).
                4. REABILITAÇÃO (ORDER 1): O primeiro exercício deve focar na patologia "%s" no plano de maior limitação.
                5. LÓGICA: Alternar grupos e planos mantém o ritmo %s e evita desgaste articular.
                """.formatted(userRequest.frequencyPerWeek(), durationText, pathologyText, protocol.getTempo());
    }

    @NotNull
    private static String getString(String pathologyText) {
        if (pathologyText == null || pathologyText.equalsIgnoreCase("Nenhuma") || pathologyText.isBlank()) {
            return "";
        }

        StringBuilder correcao = new StringBuilder();
        String lowerPathology = pathologyText.toLowerCase();

        // 1. JOELHO (Valgo/Varo/Estabilidade)
        if (lowerPathology.contains("valgo") || lowerPathology.contains("joelho")) {
            correcao.append("""
                    - DIRETRIZ JOELHO: Foco em estabilizadores da anca (Glúteo Médio) e Vasto Medial.
                    - GRUPOS MUSCULARES ALVO: Abdutores e Quadríceps.
                    - OBRIGATÓRIO: Incluir exercícios no PLANO FRONTAL (ex: Clamshells ou Side-walk).
                    """);
        }

        // 2. TORNOZELO (Instabilidade/Entorse/Mobilidade)
        if (lowerPathology.contains("tornozelo") || lowerPathology.contains("entorse") || lowerPathology.contains("pé")) {
            correcao.append("""
                    - DIRETRIZ TORNOZELO: Foco em Propriocepção e Fortalecimento da cadeia inferior.
                    - GRUPOS MUSCULARES ALVO: Tibial Anterior, Peroniais e Gastrocnémios.
                    - OBRIGATÓRIO: Exercícios de equilíbrio e dorsiflexão.
                    """);
        }

        // 3. OMBRO (Manguito Rotador/Impacto)
        if (lowerPathology.contains("ombro") || lowerPathology.contains("manguito") || lowerPathology.contains("escápula")) {
            correcao.append("""
                    - DIRETRIZ OMBRO: Foco na estabilidade da cintura escapular.
                    - GRUPOS MUSCULARES ALVO: Manguito Rotador (Subescapular, Supraespinhoso, Infraespinhoso) e Serrátil Anterior.
                    - OBRIGATÓRIO: Rotações externas e estabilização isométrica.
                    """);
        }

        // 4. PESCOÇO / CERVICAL
        if (lowerPathology.contains("pescoço") || lowerPathology.contains("cervical")) {
            correcao.append("""
                    - DIRETRIZ CERVICAL: Corrigir anteriorização e hipercifose.
                    - GRUPOS MUSCULARES ALVO: Flexores profundos do pescoço e Trapézio Inferior/Médio.
                    - OBRIGATÓRIO: Chin Tucks e Retração Escapular (Y-W-T).
                    """);
        }

        // 5. COLUNA (Lombar/Hérnia)
        if (lowerPathology.contains("lombar") || lowerPathology.contains("hérnia") || lowerPathology.contains("costas")) {
            correcao.append("""
                    - DIRETRIZ COLUNA: Foco em estabilização segmentar e "Bracing".
                    - GRUPOS MUSCULARES ALVO: Transverso do abdómen, Multífidos e Eretores da espinha.
                    - OBRIGATÓRIO: Pranchas (Plank) e Dead Bug (evitar flexão excessiva se houver dor).
                    """);
        }

        return """
                REGRAS ANATÓMICAS CRÍTICAS PARA REABILITAÇÃO:
                %s
                1. DIFERENCIAÇÃO: Nunca listes uma articulação (ex: "Tornozelo") no campo 'muscleGroup'. Usa sempre o músculo motor (ex: "Tibial", "Gémeos").
                2. ORDEM 1: Como o aluno possui "%s", o primeiro exercício de cada dia deve ser a correção motora indicada acima.
                3. JUSTIFICATIVA: No campo 'notas', explica a biomecânica (ex: "Fortalecimento do Tibial para estabilizar a articulação do tornozelo").
                """.formatted(correcao.toString(), pathologyText);
    }

    private String defaultIfEmpty(String value, String defaultValue) {
        return (value == null || value.isBlank()) ? defaultValue : value;
    }

    private TrainingPlanResponse executeGeneration(String prompt) {

        try {
            String textResponse = chatModel.call(prompt);
            // Se configurou o MIME type, textResponse já vira um JSON puro.
            // O cleanMarkdown ainda é bom por segurança, mas o erro de "end-of-input" deve sumir.
            return objectMapper.readValue(cleanMarkdown(textResponse), TrainingPlanResponse.class);
        } catch (Exception e) {
            throw new RuntimeException("Erro ao gerar plano: " + e.getMessage(), e);
        }
    }

    private String cleanMarkdown(String text) {
        if (text == null || text.isBlank()) return "{}";

        // Remove blocos de código
        String cleaned = text.replaceAll("(?s)```json\\s*(.*?)\\s*```", "$1").trim();

        int firstBrace = cleaned.indexOf("{");
        int lastBrace = cleaned.lastIndexOf("}");

        if (firstBrace != -1 && lastBrace != -1) {
            return cleaned.substring(firstBrace, lastBrace + 1);
        }
        return cleaned;
    }
}