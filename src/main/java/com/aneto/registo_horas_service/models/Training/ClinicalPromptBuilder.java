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

    private String buildDiretrizTreino(List<String> sequencia, String durationText, int volumeIdeal) {
        return "REGRAS DE DIVISÃO: " + String.join(" -> ", sequencia)
                + " | Duração Total: " + durationText
                + " | Volume Exato: " + volumeIdeal + " exercícios/dia.";
    }

    private String obterDiretrizPorFrequencia(int freqSemanal, int volumeIdeal, String durationText) {
        return switch (freqSemanal) {
            case 1, 2 -> buildDiretrizTreino(
                    List.of(
                            "Dia A (Full Body - Estabilidade Pélvica, Cadeia Posterior Controlada & Empurrar Horizontal)",
                            "Dia B (Full Body - Estabilidade Escapular, Cadeia Anterior & Carregamento Anti-Cisalhamento)"
                    ),
                    durationText, volumeIdeal
            );
            case 3 -> buildDiretrizTreino(
                    List.of(
                            "Dia A (Membros Superiores - Peitoral, Dorsal & Estabilidade Escapular)",
                            "Dia B (Membros Inferiores - Quadríceps, Glúteos & Controlo de Anca)",
                            "Dia C (Cadeia Posterior, Mobilidade Global & Core Anti-Rotação)"
                    ),
                    durationText, volumeIdeal
            );
            case 4 -> buildDiretrizTreino(
                    List.of(
                            "Dia A (Superiores - Empurrar & Puxar Horizontal/Vertical)",
                            "Dia B (Inferiores - Dominante de Anca, Glúteo & Cadeia Posterior)",
                            "Dia C (Estabilidade Postural, Correção Cervico-Torácica & Core)",
                            "Dia D (Inferiores - Dominante de Joelho & Estabilidade Unilateral)"
                    ),
                    durationText, volumeIdeal
            );
            default -> buildDiretrizTreino(
                    List.of(
                            "Dia A (Membros Superiores - Foco Empurrar & Estabilidade Escapular)",
                            "Dia B (Membros Inferiores - Dominante de Joelho & Controlo Articular)",
                            "Dia C (Membros Superiores - Foco Puxar & Reequilíbrio Postural)",
                            "Dia D (Membros Inferiores - Cadeia Posterior & Glúteos)",
                            "Dia E (Condicionamento Metabólico, Mobilidade Global & Core)"
                    ),
                    durationText, volumeIdeal
            );
        };
    }

    private String calcularEstruturaBlocos(String durationText, int volumeIdeal) {
        int totalMin = 60;
        if (durationText != null) {
            String digits = durationText.replaceAll("[^0-9]", "");
            if (!digits.isEmpty()) {
                totalMin = Integer.parseInt(digits);
            }
        }

        int b1Min = Math.max(5, (int) Math.round(totalMin * 0.17));
        int b2Min = Math.max(10, (int) Math.round(totalMin * 0.25));
        int b4Min = 5;
        int b3Min = Math.max(15, totalMin - b1Min - b2Min - b4Min);

        int b1Ex = Math.max(1, volumeIdeal / 4);
        int b2Ex = Math.max(1, volumeIdeal / 4);
        int b4Ex = 1;
        int b3Ex = Math.max(2, volumeIdeal - b1Ex - b2Ex - b4Ex);

        return String.format(Locale.US, """
                Para CADA DIA (%d Minutos totais / %d exercícios), divide a sessão obrigatoriamente nestes 4 blocos:
                1. BLOCO 1: LIBERTAÇÃO, MOBILIDADE & ATIVAÇÃO NEURAL (%d Minutos | ~%d exercícios) -> Intensidade alvo: RPE 4-5
                2. BLOCO 2: ESTABILIZAÇÃO DO CORE & COLUNA ANTI-CISALHAMENTO (%d Minutos | ~%d exercícios) -> Intensidade alvo: RPE 6-7 (RIR 3-4)
                3. BLOCO 3: FORTALECIMENTO ESPECÍFICO & HIPERTROFIA PROTEGIDA (%d Minutos | ~%d exercícios) -> Intensidade alvo: RPE 7-8.5 (RIR 1.5-3)
                4. BLOCO 4: RETORNO À CALMA & DESCOMPRESSÃO NEURAL (%d Minutos | %d exercício) -> Intensidade alvo: RPE 3-4
                NOTA: A soma de 'totalExerciseTime' de todos os exercícios de cada dia TEM de totalizar exatamente "%d Minutos".""",
                totalMin, volumeIdeal,
                b1Min, b1Ex,
                b2Min, b2Ex,
                b3Min, b3Ex,
                b4Min, b4Ex,
                totalMin
        );
    }

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

        String medicalReport = (userRequest.getMedicalReportText() != null && !userRequest.getMedicalReportText().isBlank())
                ? userRequest.getMedicalReportText() : "Nenhum relatório médico inserido (aplicar princípios gerais de proteção articular e coluna neutra).";

        String visbodyReport = (userRequest.getReportVisbobyText() != null && !userRequest.getReportVisbobyText().isBlank())
                ? userRequest.getReportVisbobyText() : "Nenhum relatório Visbody inserido.";

        String listaAnteriores = (exerciciosAnteriores == null || exerciciosAnteriores.isEmpty())
                ? "Nenhum" : String.join(", ", exerciciosAnteriores);

        double weight = userRequest.getWeightKg() != null ? userRequest.getWeightKg() : 70.0;
        double height = userRequest.getHeightCm() != null ? userRequest.getHeightCm() : 170.0;
        int age = userRequest.getAge() != null ? userRequest.getAge() : 30;
        int freq = userRequest.getFrequencyPerWeek() > 0 ? userRequest.getFrequencyPerWeek() : 3;
        int diasAGerar = Math.min(freq, 5);
        String durationText = defaultIfEmpty(userRequest.getDurationPerSession(), "60 Minutos");

        String catalogoDinamico = carregarCatalogoDinamico();
        String diretrizTreino = obterDiretrizPorFrequencia(diasAGerar, volumeIdeal, durationText);
        String estruturaBlocos = calcularEstruturaBlocos(durationText, volumeIdeal);

        String template = """
            ATUAÇÃO: Especialista Sénior em Prescrição de Exercício Clínico, Reabilitação Neuromuscular e Biomecânica Aplicada em Portugal.

            PERFIL DO UTENTE:
            - Nome: {{STUDENT_NAME}}
            - Idade: {{AGE}} anos
            - Peso: {{WEIGHT}} kg | Altura: {{HEIGHT}} cm
            - Frequência Semanal: {{FREQ}} dias/semana

            DIRETRIZ DE PLANEAMENTO DO TREINO:
            {{DIRETRIZ_TREINO}}

            RELATÓRIO MÉDICO / EXAMES COMPLEMENTARES:
            {{MEDICAL_REPORT}}

            RELATÓRIO VISBODY / AVALIAÇÃO POSTURAL E COMPOSIÇÃO CORPORAL:
            {{VISBODY_REPORT}}

            CATÁLOGO OFICIAL DE EXERCÍCIOS DA APLICAÇÃO:
            {{CATALOGO_DINAMICO}}

            SISTEMA DE PRESCRIÇÃO CLÍNICA MULTI-DIAS EM 4 BLOCOS:
            Gera OBRIGATORIAMENTE um array 'clinicalPlan' contendo EXATAMENTE {{DIAS_GERAR}} DIAS DIFERENTES de acordo com a diretriz de divisão acima.

            {{ESTRUTURA_BLOCOS}}

            REGRAS CLÍNICAS, SEGURANÇA E CONTROLO DE INTENSIDADE (RPE / RIR):
            1. SELEÇÃO DE EXERCÍCIOS: Escolher OBRIGATORIAMENTE apenas exercícios existentes no Catálogo Oficial acima ('exerciseId' e 'name' exatos).
            2. CONTROLO DE ESFORÇO (RPE E RIR):
               - Prescrever obrigatoriamente o campo 'rpe' (escala 1-10) e refletir a relação RPE/RIR em 'cargaAtual' e no 'progressionProtocol' (ex: RPE 7-8 corresponde a RIR 2-3).
               - Nunca prescrever RPE 9.5-10 (falha concêntrica) em exercícios que envolvam estabilidade da coluna, ombro ou joelho com desvio postural.
            3. RESTRIÇÕES BIOMECÂNICAS DINÂMICAS:
               - Analisa rigorosamente o Relatório Médico e o Visbody acima.
               - Se existir listese, discopatia, hérnia/protrusão ou conflito radicular: proíbe cargas axiais compressivas sobre a coluna (agachamento livre com barra, good morning, desenvolvimento militar em pé) e movimentos que agravem o cisalhamento ou estiramento neural.
               - Se existirem desvios posturais no Visbody (ex: hiperextensão de joelho, anteversão pélvica, protração escapular, anteriorização da cabeça): adapta a seleção e escreve cuidados específicos nas 'notas' de cada exercício.
            4. QUALIDADE DO TEXTO CLÍNICO:
               - Nos campos 'details' e 'notas', escreve instruções técnicas precisas e justificadas biomecanicamente (COMO executar e PORQUÊ protege a estrutura lesionada ou corrige a postura).
            5. PLANO ALIMENTAR ('dietPlan.meals'):
               - Preenche o array 'meals' com 4 a 5 refeições diárias equilibradas (ex: Pequeno-Almoço, Almoço, Lanche, Jantar, Ceia), utilizando alimentos comuns em Portugal e com quantidades em gramas no array 'ingredients'.
               - A soma das calorias e macronutrientes ('calories', 'protein', 'carbs', 'fats') das refeições deve aproximar-se de {{DAILY_CALORIES}} kcal ({{PROTEIN}}g P, {{CARBS}}g HC, {{FATS}}g G).
            6. EXERCÍCIOS A EVITAR REPETIR DO CICLO ANTERIOR: {{LISTA_ANTERIORES}}.
            7. PREENCHIMENTO DE CAMPOS: Preenche TODOS os campos obrigatórios do esquema JSON. Responde APENAS com JSON válido, sem markdown ou texto extra.

            FORMATO JSON OBRIGATÓRIO:
            {
              "isExistingPlan": false,
              "clientName": "{{STUDENT_NAME}}",
              "primaryDiagnosis": "<Diagnóstico clínico extraído estritamente do Relatório Médico ou avaliação funcional>",
              "primaryObjective": "<Objetivo principal de reabilitação, estabilidade e recomposição corporal>",
              "clinicalSummary": "<Síntese clínica detalhada cruzando o Relatório Médico com a bioimpedância e desvios posturais do Visbody>",
              "validationStatus": "REQUIERE_VALIDACAO_MEDICA",
              "biomechanicalGuidelines": [
                "<Diretriz Biomecânica 1: Controlo articular/vertebral específico para as patologias do utente>",
                "<Diretriz Biomecânica 2: Proteção neural/discal e amplitude segura>",
                "<Diretriz Biomecânica 3: Estratégia de reequilíbrio postural baseada no Visbody>"
              ],
              "safetyDirectives": [
                "<Movimentos Contraindicados: Lista explícita de exercícios e padrões proibidos para este caso>",
                "<Critério de Interrupção 1: Dor aguda localizada na região afetada>",
                "<Critério de Interrupção 2: Sinais neurológicos como choque, dormência, parestesia ou dor irradiada>",
                "<Critério de Interrupção 3: Perda de força motora ou instabilidade articular>"
              ],
              "progressionProtocol": [
                {
                  "period": "Semanas 1 a 4",
                  "rpe": "RPE 7 - 7.5",
                  "rir": "RIR 2-3 (deixar 2 a 3 reps antes da falha técnica)",
                  "criteria": "Consolidação dos padrões motores e controlo postural. Fase excêntrica controlada (2-3s) mantendo alinhamento neutro."
                },
                {
                  "period": "Semanas 5 a 8",
                  "rpe": "RPE 8",
                  "rir": "RIR 2",
                  "criteria": "Regra de Dupla Progressão: Ao completar o topo das repetições prescritas em todas as séries com técnica perfeita e RPE <= 8, aumentar a carga em 2,5% a 5% na sessão seguinte e regressar à base das repetições."
                },
                {
                  "period": "Semanas 9 a 12",
                  "rpe": "RPE 8 - 8.5 (multiarticulares) | RPE 7 - 7.5 (core/ombro)",
                  "rir": "RIR 1-2 (nos exercícios multiarticulares principais)",
                  "criteria": "Intensificação controlada nos exercícios principais (mantendo RIR 2-3 / RPE 7-7.5 no core e estabilizadores). Reavaliar composição corporal e sintomatologia ao final da semana 12."
                }
              ],
              "clinicalPlan": [
                {
                  "day": "<Nome do Dia conforme a Diretriz de Planeamento>",
                  "exercises": [
                    {
                      "order": 1,
                      "exerciseId": 0,
                      "name": "<Nome exato do catálogo>",
                      "muscleGroup": "<Grupo muscular>",
                      "movementPlane": "<Sagital | Frontal | Transverso | Multiplanar>",
                      "equipment": "<Equipamento>",
                      "tempo": "<Ex: 3-1-2-1>",
                      "sets": "<Ex: 3>",
                      "reps": "<Ex: 10-12>",
                      "rest": "<Ex: 45s>",
                      "rpe": "<Ex: RPE 5 ou RPE 7-8>",
                      "executionDuration": "<Tempo ativo por série, ex: 40s por série>",
                      "totalExerciseTime": "<Tempo total incluindo séries e descanso, ex: 5 Minutos>",
                      "weight": "<Carga sugerida, ex: 0kg ou 4kg>",
                      "cargaAtual": "",
                      "videoUrl": "",
                      "details": "<Instrução técnica detalhada de execução>",
                      "notas": "<Justificação clínica e segurança biomecânica específica para este utente>",
                      "date": ""
                    }
                  ]
                }
              ],
              "dietPlan": {
                "dailyCalories": {{DAILY_CALORIES}},
                "imc": {{IMC}},
                "imcCategory": "{{IMC_CATEGORY}}",
                "macroDistribution": {
                  "protein": "{{PROTEIN}}g",
                  "carbs": "{{CARBS}}g",
                  "fats": "{{FATS}}g"
                },
                "meals": [
                  {
                    "time": "08:00",
                    "description": "Pequeno-Almoço",
                    "ingredients": [
                      "2 ovos inteiros mexidos",
                      "50g de pão integral",
                      "1 peça de fruta"
                    ],
                    "calories": 420,
                    "protein": 22,
                    "carbs": 45,
                    "fats": 16
                  }
                ]
              }
            }
            """;

        return template
                .replace("{{STUDENT_NAME}}", studentName)
                .replace("{{AGE}}", String.valueOf(age))
                .replace("{{WEIGHT}}", String.format(Locale.US, "%.1f", weight))
                .replace("{{HEIGHT}}", String.format(Locale.US, "%.1f", height))
                .replace("{{FREQ}}", String.valueOf(freq))
                .replace("{{DIRETRIZ_TREINO}}", diretrizTreino)
                .replace("{{MEDICAL_REPORT}}", medicalReport)
                .replace("{{VISBODY_REPORT}}", visbodyReport)
                .replace("{{CATALOGO_DINAMICO}}", catalogoDinamico)
                .replace("{{DIAS_GERAR}}", String.valueOf(diasAGerar))
                .replace("{{ESTRUTURA_BLOCOS}}", estruturaBlocos)
                .replace("{{LISTA_ANTERIORES}}", listaAnteriores)
                .replace("{{DURATION_TEXT}}", durationText)
                .replace("{{DAILY_CALORIES}}", String.valueOf(macros.dailyCalories()))
                .replace("{{IMC}}", String.format(Locale.US, "%.2f", macros.imc()))
                .replace("{{IMC_CATEGORY}}", macros.imcCategory())
                .replace("{{PROTEIN}}", String.valueOf(macros.protein()))
                .replace("{{CARBS}}", String.valueOf(macros.carbs()))
                .replace("{{FATS}}", String.valueOf(macros.fats()));
    }
}