package com.aneto.registo_horas_service.models.Training;

import com.aneto.registo_horas_service.dto.request.UserProfileRequest;
import com.aneto.registo_horas_service.dto.response.Macros;
import com.aneto.registo_horas_service.repository.ExerciseRepository;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import static com.aneto.registo_horas_service.models.Training.TrainingUtils.defaultIfEmpty;

@Component
@AllArgsConstructor
public class ClinicalPromptBuilder {

    private final ExerciseRepository exercicioRepository;

    /**
     * Gera a instrução dinâmica de divisão de treino, duração e volume por sessão.
     */
    private String buildDiretrizTreino(List<String> sequencia, String durationText, int volumeIdeal) {
        return "REGRAS DE DIVISÃO: " + String.join(" -> ", sequencia) + " | Duração: " + durationText + " | Volume: " + volumeIdeal + " ex/dia.";
    }

    /**
     * Obtém a diretriz de treino calculada com base na frequência semanal do utente.
     */
    private String obterDiretrizPorFrequencia(int freqSemanal, int volumeIdeal , String durationText) {
        return switch (freqSemanal) {
            // 1 a 2 dias: Full Body Funcional (Garante estímulo de todo o corpo na mesma sessão)
            case 1, 2 -> buildDiretrizTreino(
                    List.of(
                            "Dia A (Full Body - Foco Estabilidade Pélvica & Cadeia Posterior)",
                            "Dia B (Full Body - Foco Estabilidade Escapular & Quadríceps)"
                    ),
                    durationText, volumeIdeal
            );

            // 3 dias: Divisão Clássica Adaptada (Superiores / Inferiores / Postural & Integrado)
            case 3 -> buildDiretrizTreino(
                    List.of(
                            "Dia 1 (Membros Superiores - Peitoral, Dorsal & Escápulas)",
                            "Dia 2 (Membros Inferiores - Quadríceps, Glúteos & Anca)",
                            "Dia 3 (Cadeia Posterior, Mobilidade Global & Antirrotação)"
                    ),
                    durationText, volumeIdeal
            );

            // 4 dias: Upper / Lower / Postural / Full Body
            case 4 -> buildDiretrizTreino(
                    List.of(
                            "Dia A (Superiores - Empurrar & Puxar)",
                            "Dia B (Inferiores - Foco Dominante de Anca e Glúteo)",
                            "Dia C (Estabilidade Postural & Correção Cervico-Torácica)",
                            "Dia D (Inferiores & Core - Foco Dominante de Joelho)"
                    ),
                    durationText, volumeIdeal
            );

            // Default (ex: 5+ dias): Distribuição equilibrada
            default -> buildDiretrizTreino(
                    List.of(
                            "Dia 1 (Membros Superiores & Estabilidade Escapular)",
                            "Dia 2 (Membros Inferiores & Alinhamento Pélvico)",
                            "Dia 3 (Mobilidade Global, Core & Recondicionamento)"
                    ),
                    durationText, volumeIdeal
            );
        };
    }

    /**
     * Procura dinamicamente todos os exercícios na BD e formata o catálogo para o LLM.
     */
    private String carregarCatalogoDinamico() {
        List<Exercises> exercises = exercicioRepository.findAll();

        if (exercises.isEmpty()) {
            return "Nenhum exercício encontrado na base de dados.";
        }

        return exercises.stream()
                .map(e -> String.format("ID: %d | %s | %s%s",
                        e.getId(),
                        e.getName(),
                        e.getCategory(),
                        (e.getSubcategory() != null && !e.getSubcategory().isBlank()) ? " (" + e.getSubcategory() + ")" : ""
                ))
                .collect(Collectors.joining("\n"));
    }

    public String buildClinicalPrompt(UserProfileRequest userRequest, Macros macros, List<String> exerciciosAnteriores, int volumeIdeal) {
        String studentName = (userRequest.getStudentName() != null && !userRequest.getStudentName().isBlank())
                ? userRequest.getStudentName() : "Utente";

        String medicalReport = userRequest.getMedicalReportText() != null ? userRequest.getMedicalReportText() : "Nenhum relatório médico inserido.";
        String visbodyReport = userRequest.getReportVisbobyText() != null ? userRequest.getReportVisbobyText() : "Nenhum relatório Visbody inserido.";

        String listaAnteriores = (exerciciosAnteriores == null || exerciciosAnteriores.isEmpty())
                ? "Nenhum" : String.join(", ", exerciciosAnteriores);

        double weight = userRequest.getWeightKg() != null ? userRequest.getWeightKg() : 70.0;
        double height = userRequest.getHeightCm() != null ? userRequest.getHeightCm() : 170.0;
        int age = userRequest.getAge() != null ? userRequest.getAge() : 30;
        int freq = userRequest.getFrequencyPerWeek() > 0 ? userRequest.getFrequencyPerWeek() : 3;
        String durationText = defaultIfEmpty(userRequest.getDurationPerSession(), "60 minutos");


        // Obter catálogo dinâmico da BD e diretriz de treino calculada
        String catalogoDinamico = carregarCatalogoDinamico();
        String diretrizTreino = obterDiretrizPorFrequencia(freq, volumeIdeal, durationText);

        String template = """
            ATUAÇÃO: Especialista em Prescrição de Treino Clínico e Reabilitação Neuromuscular Profissional em Portugal.
            PERFIL DO UTENTE:
            - Nome: %s
            - Idade: %d anos
            - Peso: %.1f kg | Altura: %.1f cm
            - Frequência Semanal: %d dias/semana

            DIRETRIZ DE PLANEAMENTO DO TREINO:
            %s

            RELATÓRIO MÉDICO / RESSONÂNCIA MAGNÉTICA:
            %s

            RELATÓRIO VISBODY / AVALIAÇÃO POSTURAL:
            %s

            CATÁLOGO OFICIAL DE EXERCÍCIOS DA APLICAÇÃO:
            %s

            SISTEMA DE PRESCRIÇÃO CLÍNICA MULTI-DIAS EM 4 BLOCOS:
            Gera OBRIGATORIAMENTE um array 'clinicalPlan' contendo EXATAMENTE %d DIAS DIFERENTES de acordo com as regras de divisão acima.

            Para CADA DIA, divide a sessão nos 4 blocos temporizados:
            1. BLOCO 1: LIBERTAÇÃO, MOBILIDADE & ATIVAÇÃO (10 Minutos)
            2. BLOCO 2: ESTABILIZAÇÃO DO CORE & COLUNA (10 Minutos)
            3. BLOCO 3: FORTALECIMENTO ESPECÍFICO (20 Minutos)
            4. BLOCO 4: RETORNO À CALMA & DESCOMPRESSÃO (5 Minutos)

            REGRAS CRÍTICAS DE PREENCHIMENTO E TEMPOS:
            - SELEÇÃO DE EXERCÍCIOS: Escolher OBRIGATORIAMENTE do catálogo acima. Preencher 'exerciseId' e 'name'.
            - DURAÇÃO E TEMPOS:
              * 'executionDuration': Tempo ativo por série/repetição (ex: "40s por série", "12 reps (~36s)").
              * 'totalExerciseTime': Tempo total do exercício incluindo séries e descanso (ex: "4 Minutos").
              * 'totalDayDuration': Duração total acumulada do dia (ex: "45 Minutos").
            - PREENCHIMENTO DE TODOS OS CAMPOS DO EXERCÍCIO:
              * 'order', 'exerciseId', 'name', 'muscleGroup', 'movementPlane', 'equipment', 'tempo', 'sets', 'reps', 'rest', 'executionDuration', 'totalExerciseTime', 'weight', 'cargaAtual', 'videoUrl', 'details', 'notas', 'date'.
            - RESTRIÇÕES DE SEGURANÇA: Proibido agachamento livre com barra, deadlift ou cargas axiais sobre a coluna.
            - EXERCÍCIOS A EVITAR REPETIR: %s.

            FORMATO JSON OBRIGATÓRIO:
            {
              "isExistingPlan": false,
              "clientName": "%s",
              "primaryDiagnosis": "Compressão da Raiz L3 Direita e Anterolistese L5",
              "primaryObjective": "Reativação Neuromuscular do Quadríceps Direito e Estabilidade Pélvica",
              "clinicalSummary": "Síntese médica do cruzamento entre a RM Lombar e a bioimpedância/postura do VisBody...",
              "validationStatus": "REQUIERE_VALIDACAO_MEDICA",
              "safetyDirectives": [
                "Sinal de Alarme: Interrupção imediata se manifestar dor aguda ou parestesia.",
                "Contraindicação Absoluta: Cargas axiais sobre a coluna e forças de cisalhamento em L5-S1."
              ],
              "clinicalPlan": [
                {
                  "day": "Dia 1 - Foco Lombar e Mobilidade Pélvica",
                  "totalDayDuration": "45 Minutos",
                  "blocks": [
                    {
                      "blockName": "BLOCO 1: LIBERTAÇÃO & MOBILIDADE",
                      "durationText": "10 Minutos",
                      "exercises": [
                        {
                          "order": 1,
                          "exerciseId": 66,
                          "name": "Cat Cow",
                          "muscleGroup": "Coluna Toracolombar",
                          "movementPlane": "Sagital",
                          "equipment": "Colchonete",
                          "tempo": "3-1-3-1",
                          "sets": "3",
                          "reps": "10",
                          "rest": "45s",
                          "executionDuration": "40s por série",
                          "totalExerciseTime": "4 Minutos",
                          "weight": "0kg",
                          "cargaAtual": "RPE 4 (Sem carga)",
                          "videoUrl": "",
                          "details": "Em quatro apoios, alternar suavemente entre flexão e extensão torácica.",
                          "notas": "Manter respiração fluida e evitar forçar a amplitude na extensão lombar.",
                          "date": "Data"
                        }
                      ]
                    }
                  ]
                }
              ],
              "dietPlan": {
                "dailyCalories": %d,
                "imc": %.1f,
                "imcCategory": "%s",
                "macroDistribution": {
                  "protein": "%dg",
                  "carbs": "%dg",
                  "fats": "%dg"
                },
                "meals": []
              }
            }
            """;

        return String.format(Locale.US, template,
                studentName, age, weight, height, freq,          // %s, %d, %.1f, %.1f, %d
                diretrizTreino,                                   // %s (Gerado pelo buildDiretrizTreino)
                medicalReport, visbodyReport,                      // %s, %s
                catalogoDinamico,                                 // %s (Catálogo da BD)
                freq,                                             // %d (Quantidade de dias)
                listaAnteriores,                                  // %s (Exercícios a evitar)
                studentName,                                      // %s (clientName no JSON)
                macros.dailyCalories(), macros.imc(), macros.imcCategory(), // %d, %.1f, %s
                macros.protein(), macros.carbs(), macros.fats()    // %d, %d, %d
        );
    }
}