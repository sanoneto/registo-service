package com.aneto.registo_horas_service.models.Training;

import com.aneto.registo_horas_service.dto.request.UserProfileRequest;
import com.aneto.registo_horas_service.dto.response.TrainingPlanResponse;
import com.aneto.registo_horas_service.models.Enum;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.cloud.vertexai.api.Content;
import com.google.cloud.vertexai.api.GenerateContentResponse;
import com.google.cloud.vertexai.api.Part;
import com.google.cloud.vertexai.generativeai.GenerativeModel;
import com.google.cloud.vertexai.generativeai.ResponseHandler;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

@Component
public record Training(GenerativeModel generativeModel, ObjectMapper objectMapper) {

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
        MacroCalculator.Macros macros = MacroCalculator.calculate(
                userRequest.weightKg(), userRequest.heightCm(), userRequest.age(),
                genderText, bodyTypeText, bodyFat
        );

        String durationText = (userRequest.duration() != null && !userRequest.duration().isBlank())
                ? userRequest.duration() : "aprox. 60 minutos";

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
        String diretrizTreino = """
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

        String diretrizAlimentar = """
                DIRETRIZES ALIMENTARES (FÓRMULA %s):
                - TOTAIS DIÁRIOS: Calorias: %d kcal | Proteína: %d g | Carbs: %d g | Gordura: %d g
                - REGRA DE OURO: Cada ingrediente deve ser descrito com sua função (Ex: "100g de Frango - Proteína para reparação muscular").
                - CÁLCULO POR REFEIÇÃO: É obrigatório calcular as calorias e macros exatos de cada refeição com base nos ingredientes listados.
                - PRIORIZAÇÃO: Pequeno-almoço e almoço mais calóricos.
                """.formatted(macros.formula(), macros.calories(), macros.protein(), macros.carbs(), macros.fats());

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
                  "summary": "Explicação técnica da estratégia %s para %s, considerando %d anos e %s.",
                  "plan": [
                    {
                      "day": "Dia 1 - SUPERIOR: Peitoral e Tríceps",
                      "exercises": [
                        {
                          "order": 1,
                          "name": "Nome",
                          "muscleGroup": "Grupo (Indicar Plano: Sagital/Frontal/Transversal)",
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
                    "dailyCalories": %d,
                    "macroDistribution": { "protein": "%dg", "carbs": "%dg", "fats": "%dg" },
                    "meals": [
                      {
                        "time": "HH:MM",
                        "description": "Nome da Refeição",
                       "ingredients": [
                        "150g de Peito de Frango Grelhado - Fonte de proteína de alto valor biológico",
                        "200g de Arroz Basmati - Hidrato de carbono de absorção gradual",
                        "80g de Brócolos ao vapor - Micronutrientes e fibras para digestão"
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
                2. Para cada refeição, os campos "calories", "protein", "carbs" e "fats" DEVEM ser calculados e preenchidos com números inteiros reais baseados nos ingredientes.
                3. A soma total dos macros das refeições deve ser aproximadamente: %d kcal, %dg Prot, %dg Carbs e %dg Fats.
                """.formatted(
                // 1-10: Perfil
                userRequest.age(), bodyTypeText, genderText, userRequest.heightCm(), userRequest.weightKg(),
                historyText, pathologyText, locationText, countryText, objectiveText,

                // 11-20: Blocos de Regras
                diretrizProtocolo, diretrizBiomecanica, diretrizAquecimento, diretrizTreino, diretrizAlimentar, diretrizRepertorio,
                diretrizEquipamento, diretrizArrefecimento, diretrizIntensidade, diretrizNomenclaturaDias, diretrizReabilitacao,

                // 21-24: JSON Summary
                protocol.getLabel(), objectiveText, userRequest.age(), pathologyText,

                // 25-28: JSON Exercício
                protocol.getTempo(), protocol.getSets(), protocol.getReps(), protocol.getRest(),

                // 29-32: JSON Justificativas
                pathologyText, protocol.getLabel(), pathologyText, protocol.getTempo(),

                // 33-38: JSON Diet
                macros.calories(), macros.protein(), macros.carbs(), macros.fats(),
                locationText, countryText,

                // 39-42: REGRAS CRÍTICAS (Novos argumentos para formatar o final do prompt)
                macros.calories(), macros.protein(), macros.carbs(), macros.fats()
        );
        return executeGeneration(userPrompt);
    }

    @NotNull
    private static String getString(String pathologyText) {
        String diretrizReabilitacao = "";
        if (pathologyText != null && !pathologyText.equalsIgnoreCase("Nenhuma") && !pathologyText.isBlank()) {
            diretrizReabilitacao = """
                    REGRA DE REABILITAÇÃO (OBRIGATÓRIA):
                    - Como o aluno possui "%s", o PRIMEIRO exercício de cada dia (Order 1) deve ser obrigatoriamente um exercício de fisioterapia, mobilidade ou fortalecimento específico para tratar esta condição.
                    - Deve ser apenas 1 exercício focado na patologia por treino.
                    - No campo 'notas', justifica como este exercício ajuda especificamente na recuperação de %s.
                    """.formatted(pathologyText, pathologyText);
        }
        return diretrizReabilitacao;
    }

    private String defaultIfEmpty(String value, String defaultValue) {
        return (value == null || value.isBlank()) ? defaultValue : value;
    }

    private TrainingPlanResponse executeGeneration(String prompt) {
        Content content = Content.newBuilder().addParts(Part.newBuilder().setText(prompt)).setRole("user").build();
        try {
            GenerateContentResponse response = generativeModel.generateContent(content);
            String textResponse = ResponseHandler.getText(response);
            return objectMapper.readValue(cleanMarkdown(textResponse), TrainingPlanResponse.class);
        } catch (Exception e) {
            throw new RuntimeException("Erro ao gerar plano: " + e.getMessage(), e);
        }
    }

    private String cleanMarkdown(String text) {
        if (text == null || text.isBlank()) return "{}";

        // 1. Remove os blocos de marcação de código do Markdown
        String cleaned = text.trim()
                .replaceAll("^```json", "")
                .replaceAll("^```", "")
                .replaceAll("```$", "")
                .trim();

        // 2. TENTATIVA DE RESGATE: Se a IA escreveu algo antes do JSON (ex: "Olá! { ... }")
        // Procuramos o primeiro '{' e o último '}'
        int firstBrace = cleaned.indexOf("{");
        int lastBrace = cleaned.lastIndexOf("}");

        if (firstBrace != -1 && lastBrace != -1 && lastBrace > firstBrace) {
            return cleaned.substring(firstBrace, lastBrace + 1);
        }

        return cleaned;
    }
}