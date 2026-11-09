package com.aneto.registo_horas_service.models;

import com.aneto.registo_horas_service.models.Training.TargetRpe;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

public class Enum {

    // Enum para o status da ausência
    public enum AbsenceStatus {
        PENDENTE, APROVADO, REJEITADO
    }

    // Enum para o tipo de ausência
    public enum AbsenceType {
        FÉRIAS, LICENÇA_MÉDICA, FOLGA_COMPENSATÓRIA, OUTRA
    }

    public enum EstadoPedido {
        PENDENTE("PENDENTE"),
        FINALIZADO("FINALIZADO"),
        FECHADO("FECHADO"),
        A_PROCESSAR("A PROCESSAR"),
        ERRO("ERRO");

        private final String descricao;

        EstadoPedido(String descricao) {
            this.descricao = descricao;
        }

        @JsonValue
        public String getDescricao() {
            return descricao;
        }

        public static EstadoPedido fromDescricao(String texto) {
            for (EstadoPedido estado : EstadoPedido.values()) {
                if (estado.descricao.equalsIgnoreCase(texto)) {
                    return estado;
                }
            }
            return EstadoPedido.valueOf(texto.replace(" ", "_").toUpperCase());
        }
    }

    public enum EstadoPlano {
        ATIVO, INATIVO;
    }

    public enum OperacaoAuditoria {
        CREATE, UPDATE, DELETE
    }

    @Getter
    public enum TrainingProtocol {
        // --- SISTEMA NASM OPT ---
        NASM_ESTABILIZACAO(
                "nasm_estabilizacao", "NASM OPT Fase 1: Estabilização & Mobilidade", "60s", "12-20", "1-3", "4-2-1",
                TargetRpe.ESTABILIZACAO,
                "Prancha, Elevação Pélvica, Agachamento Cálice (Slow), Flexão de Braços com pausa, Deadbug, Equilíbrio Unipodal",
                "Saltos, Cargas máximas, Movimentos explosivos, Agachamento com barra livre pesada"
        ),
        NASM_HIPERTROFIA(
                "nasm_hipertrofia", "NASM OPT Fase 3: Hipertrofia Muscular", "60-90s", "6-12", "3-5", "2-0-2",
                TargetRpe.HIPERTROFIA,
                "Supino inclinado com halteres, Leg Press 45, Rosca Direta, Tríceps Pulley, Remada Serrote",
                "Exercícios excessivamente instáveis que limitam a carga (ex: agachamento na bola)"
        ),
        NASM_POTENCIA(
                "nasm_potencia", "NASM OPT Fase 5: Potência Neuromuscular", "3-5 min", "1-5", "3-5", "X-0-X",
                TargetRpe.FORCA_POTENCIA,
                "Medicine Ball Chest Pass, Agachamento com Salto, Power Clean, Kettlebell Swing Explosivo, Box Jumps",
                "Exercícios isoladores de baixa intensidade, Séries lentas sem foco na taxa de desenvolvimento de força (RFD)"
        ),

        // --- AVANÇADO / ESPECIALIZADO ---
        FST7_GLUTEOS_HIPERTROFIA(
                "fst7_hypertrophy", "FST-7: Fascia Stretch Training (Expansão Fascial)", "30-45s", "10-12", "7 (no último ex.)", "2-0-2",
                TargetRpe.HIPERTROFIA_METABOLICA,
                "Hip Thrust na Máquina, Cadeira Abdutora, Cadeira Extensora, Cable Kickback, Peck Deck",
                "Exercícios complexos com peso livre para as 7 séries finais (risco de lesão e perda de isolamento)"
        ),

        // --- REABILITAÇÃO & CORREÇÃO POSTURAL ---
        REABILITACAO_POSTURAL(
                "reabilitacao_postural", "Reabilitação Articular & Correção Postural", "45-60s", "12-15", "2-3", "4-2-2",
                TargetRpe.REABILITACAO_ARTICULAR,
                "Wall Slide, Face Pull com elástico, Bird Dog, Ponte de Glúteos com isometria, Prancha Frontal assistida",
                "Compressão axial na coluna (ex: agachamento livre), Cargas máximas, Movimentos balísticos ou descontrolados"
        ),

        // --- EMAGRECIMENTO & FUNCIONAL ---
        WEIGHT_LOSS_METABOLIC(
                "weight_loss", "Emagrecimento & Condicionamento Metabólico", "30-45s", "15-25", "3-4", "2-0-1",
                new TargetRpe(6.5, 7.5, 2, 3, "Resistência Metabólica / Condicionamento"),
                "Burpees (adaptados), Agachamento com Salto leve, Mountain Climbers, Kettlebell Swing, Thrusters",
                "Séries com muito descanso (mais de 90s), Cargas extremas para baixas repetições"
        ),
        HOME_BODYWEIGHT(
                "home_workout", "Treino Funcional em Casa (Peso Corporal)", "45s", "12-15", "3", "3-1-1",
                new TargetRpe(7.0, 8.0, 2, 3, "Tensão Muscular Sustentada com Peso Corporal"),
                "Flexão de braços no sofá, Agachamento búlgaro (cadeira), Remada com mochila, Prancha abdominal, Afundo",
                "Máquinas de ginásio, Barras olímpicas, Cabos e Polias"
        ),

        // --- ALTA INTENSIDADE & VOLUME ---
        HEAVY_DUTY(
                "heavy_duty", "Heavy Duty (HIT - Mike Mentzer)", "2-3 min", "6-10", "1-2", "4-2-4",
                new TargetRpe(9.5, 10.0, 0, 0, "Falha Muscular Absoluta / Intensidade Máxima"),
                "Leg Press, Peck Deck, Extensora, Remada na Máquina, Supino Smith",
                "Exercícios com peso livre que exigem muito equilíbrio"
        ),
        GERMAN_VOLUME(
                "german_volume", "GVT: German Volume Training (10x10)", "60-90s", "10", "10", "4-0-2",
                new TargetRpe(8.0, 9.0, 1, 2, "Fadiga Acumulada por Volume Elevado"),
                "Agachamento com Barra, Supino Reto, Remada Curvada, Leg Press 45",
                "Exercícios isoladores de pequenos músculos"
        );

        private final String id;
        private final String label;
        private final String rest;
        private final String reps;
        private final String sets;
        private final String tempo;
        private final TargetRpe defaultRpe;
        private final String suggestedExercises;
        private final String forbiddenExercises;

        TrainingProtocol(String id, String label, String rest, String reps, String sets, String tempo,
                         TargetRpe defaultRpe, String suggested, String forbidden) {
            this.id = id;
            this.label = label;
            this.rest = rest;
            this.reps = reps;
            this.sets = sets;
            this.tempo = tempo;
            this.defaultRpe = defaultRpe;
            this.suggestedExercises = suggested;
            this.forbiddenExercises = forbidden;
        }

        public static TrainingProtocol fromId(String id) {
            if (id == null || id.isBlank()) return NASM_ESTABILIZACAO;
            for (TrainingProtocol protocol : values()) {
                if (protocol.id.equalsIgnoreCase(id) || protocol.name().equalsIgnoreCase(id)) {
                    return protocol;
                }
            }
            return NASM_ESTABILIZACAO;
        }

        /**
         * Resolve o TargetRpe final considerando restrições clínicas, estado do aluno e objetivo.
         */
        public TargetRpe resolveTargetRpe(boolean isSedentary, boolean isDeloadWeek, boolean temPatologiaOuVisbody, String objectiveText) {
            if (isSedentary) return TargetRpe.SEDENTARIO;
            if (isDeloadWeek) return TargetRpe.DELOAD;

            // Se houver patologia ativa ou correções críticas do VisBody, garante margem de segurança
            if (temPatologiaOuVisbody && (this == REABILITACAO_POSTURAL || this == NASM_ESTABILIZACAO)) {
                return TargetRpe.REABILITACAO_ARTICULAR;
            }

            if (objectiveText != null) {
                String objLower = objectiveText.toLowerCase();
                if (objLower.contains("potência") || objLower.contains("potencia")) {
                    return TargetRpe.FORCA_POTENCIA;
                }
                if (objLower.contains("força") || objLower.contains("forca")) {
                    return TargetRpe.FORCA_MAXIMA;
                }
                if (objLower.contains("fst-7") || objLower.contains("fst7")) {
                    return TargetRpe.HIPERTROFIA_METABOLICA;
                }
                if (objLower.contains("hipertrofia") || objLower.contains("glúteo") || objLower.contains("gluteo")) {
                    return TargetRpe.HIPERTROFIA;
                }
            }

            return this.defaultRpe;
        }
    }
}