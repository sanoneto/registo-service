package com.aneto.registo_horas_service.models.Training;

import com.aneto.registo_horas_service.dto.request.UserProfileRequest;
import com.aneto.registo_horas_service.dto.response.Macros;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

@Component
public class ClinicalPromptBuilder {

    // Lista oficial de exercícios extraídos diretamente da tua Base de Dados
    private static final String CATALOGO_REABILITACAO_E_MOBILIDADE = """
        Cat Cow, Clamshell, Y-W-T, Rotação Externa, Prancha Abdominal, Bird Dog, Knee-to-Wall,
        Dead Bug, Prancha Lateral, Dead Bug com Carga, Bird Dog com Carga, Flexores da Anca,
        Open Books, Alongamento borboleta, Alongamento do músculo grande dorsal, Walking Lunges with Reach,
        Frankenstein Walk, Cossack Squat, Bird Dog com Rotação, Ponte Unipodal, Wall Slide (Ombro),
        Mobilização do Tornozelo em Carga, Ativação de Glúteo Médio em Decúbito Lateral,
        Mobilidade Torácica em Quatro Apoios, Rotação de Tronco Deitado, 90/90 Hip Switch,
        World's Greatest Stretch, Alongamento de Isquiotibiais em Pé, Alongamento Peitoral,
        Alongamento de Flexores do Punho, Alongamento de Trapézio, Equilíbrio Unipodal, Glute Bridge,
        Cadeira Extensora, Prancha com Toque no Ombro, Passada com Halteres
        """;

    public String buildClinicalPrompt(UserProfileRequest userRequest, Macros macros, List<String> exerciciosAnteriores) {
        String studentName = (userRequest.getStudentName() != null && !userRequest.getStudentName().isBlank())
                ? userRequest.getStudentName() : "Utente";

        String medicalReport = userRequest.getMedicalReportText() != null ? userRequest.getMedicalReportText() : "";
        String visbodyReport = userRequest.getReportVisbobyText() != null ? userRequest.getReportVisbobyText() : "";

        String listaAnteriores = (exerciciosAnteriores == null || exerciciosAnteriores.isEmpty())
                ? "Nenhum" : String.join(", ", exerciciosAnteriores);

        double weight = userRequest.getWeightKg() != null ? userRequest.getWeightKg() : 70.0;
        double height = userRequest.getHeightCm() != null ? userRequest.getHeightCm() : 170.0;
        int age = userRequest.getAge() != null ? userRequest.getAge() : 30;
        int freq = userRequest.getFrequencyPerWeek();

        String template = """
        ATUAÇÃO: Especialista em Prescrição de Treino Clínico e Reabilitação Neuromuscular Profissional em Portugal.
        PERFIL DO UTENTE:
        - Nome: %s
        - Idade: %d anos
        - Peso: %.1f kg | Altura: %.1f cm
        - Frequência Semanal: %d dias/semana
        
        RELATÓRIO MÉDICO / RESSONÂNCIA MAGNÉTICA:
        %s
        
        RELATÓRIO VISBODY / AVALIAÇÃO POSTURAL:
        %s
        
        SISTEMA DE PRESCRIÇÃO CLÍNICA MULTI-DIAS EM 4 BLOCOS:
        Gera OBRIGATORIAMENTE um array 'clinicalPlan' contendo EXATAMENTE %d DIAS DIFERENTES (ex: "Dia 1 - Foco Estabilidade Lombar", "Dia 2 - Foco Membros Inferiores & Glúteo", etc.).
        
        Para CADA DIA, divide a sessão nos seguintes 4 blocos temporizados:
        1. BLOCO 1: LIBERTAÇÃO, MOBILIDADE & ATIVAÇÃO (10 Minutos)
        2. BLOCO 2: ESTABILIZAÇÃO DO CORE & COLUNA (10 Minutos)
        3. BLOCO 3: FORTALECIMENTO ESPECÍFICO (20 Minutos)
        4. BLOCO 4: RETORNO À CALMA & DESCOMPRESSÃO (5 Minutos)
        
        REGRAS CRÍTICAS DE EXERCÍCIOS E SEGURANÇA:
        - Para exercícios de MOBILIDADE, REABILITAÇÃO, ALONGAMENTO e CORE, DEVES PRIORIZAR E UTILIZAR EXATAMENTE os nomes da nossa lista oficial:
          [%s]
        - NUNCA prescrever cargas mecânicas absolutas. No campo 'tempoRpe', usar cadência e RPE (ex: 'Cadência 3-2-1 / RPE 6-7').
        - Preencher 'dosageRight' e 'dosageLeft' explicitamente nos exercícios de pernas (ex: 'Dir: 4x 10-12 reps' e 'Esq: 2x 10 reps').
        - Proibido agachamento livre com barra, deadlift ou cargas axiais sobre os ombros.
        - Não repetir exercícios já realizados: %s.
        - Preencher 'videoUrl' como string vazia (será enriquecido posteriormente pelo backend).
        
        FORMATO JSON OBRIGATÓRIO:
        {
          "isExistingPlan": false,
          "clientName": "%s",
          "primaryDiagnosis": "Compressão da Raiz L3 Direita e Anterolistese L5",
          "primaryObjective": "Reativação Neuromuscular do Quadríceps Direito e Estabilidade Pélvica",
          "clinicalSummary": "Síntese médica do cruzamento entre a RM Lombar e a bioimpedância/postura do VisBody...",
          "validationStatus": "REQUIERE_VALIDACAO_MEDICA",
          "safetyDirectives": [
            "Sinal de Alarme: Interrupção imediata se manifestar dor aguda, parestesia ou dormência na coxa/joelho direito.",
            "Contraindicação Absoluta: Cargas axiais sobre a coluna e forças de cisalhamento na zona L5-S1."
          ],
          "clinicalPlan": [
            {
              "day": "Dia 1 - Foco Lombar e Mobilidade Pélvica",
              "blocks": [
                {
                  "blockName": "BLOCO 1: LIBERTAÇÃO & MOBILIDADE",
                  "durationText": "10 Minutos",
                  "exercises": [
                    {
                      "order": 1,
                      "name": "Cat Cow",
                      "targetFocus": "Alívio Neural",
                      "dosageRight": "3 Minutos",
                      "dosageLeft": "N/A",
                      "tempoRpe": "Isometria / RPE 4",
                      "executionInstructions": "Movimento ritmado respeitando o limite de dor.",
                      "safetyNotes": "Evitar qualquer curvatura forçada da coluna.",
                      "videoUrl": ""
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
                studentName, age, weight, height, freq,
                medicalReport, visbodyReport, freq,
                CATALOGO_REABILITACAO_E_MOBILIDADE, listaAnteriores,
                studentName,
                macros.dailyCalories(), macros.imc(), macros.imcCategory(),
                macros.protein(), macros.carbs(), macros.fats()
        );
    }
}