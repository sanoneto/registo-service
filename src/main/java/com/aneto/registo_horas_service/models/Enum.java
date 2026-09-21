package com.aneto.registo_horas_service.models;

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
        A_PROCESSAR("A PROCESSAR");

        private final String descricao;

        EstadoPedido(String descricao) {
            this.descricao = descricao;
        }

        @JsonValue
        public String getDescricao() {
            return descricao;
        }

        // Método para converter String (com espaço) em Enum
        public static EstadoPedido fromDescricao(String texto) {
            for (EstadoPedido estado : EstadoPedido.values()) {
                if (estado.descricao.equalsIgnoreCase(texto)) {
                    return estado;
                }
            }
            // Fallback para o valueOf padrão caso não encontre pela descrição
            return EstadoPedido.valueOf(texto.replace(" ", "_").toUpperCase());
        }
    }

    public enum EstadoPlano {
        ATIVO, INATIVO;
    }

    // Enum para Operações
    public enum OperacaoAuditoria {
        CREATE, UPDATE, DELETE
    }

    @Getter
    public enum TrainingProtocol {
        // --- FAMÍLIA NASM OPT (Mantidos) ---
        NASM_ESTABILIZACAO(
                "nasm_estabilizacao", "NASM OPT: Estabilização", "60s", "12-20", "1-3", "4-2-1",
                "Prancha, Elevação Pélvica, Agachamento Cálice (Slow), Flexão de Braços com pausa, Deadbug, Equilíbrio Unipodal",
                "Saltos, Cargas máximas, Movimentos explosivos, Agachamento com barra livre pesada"
        ),
        NASM_HIPERTROFIA(
                "nasm_hipertrofia", "NASM OPT: Hipertrofia Muscular", "60-90s", "6-12", "3-5", "2-0-2",
                "Supino inclinado com halteres, Leg Press 45, Rosca Direta, Tríceps Pulley, Remada Serrote",
                "Exercícios excessivamente instáveis que limitam a carga (ex: agachamento na bola)"
        ),

        // --- NOVO: PROTOCOLO PARA EMAGRECIMENTO (METABÓLICO) ---
        WEIGHT_LOSS_METABOLIC(
                "weight_loss", "Emagrecimento e Definição", "30-45s", "15-25", "3-4", "2-0-1",
                "Burpees (adaptados), Agachamento com Salto leve, Mountain Climbers, Kettlebell Swing, Thrusters",
                "Séries com muito descanso (mais de 90s), Cargas extremas para baixas repetições"
        ),

        // --- NOVO: TREINO EM CASA (HOME WORKOUT) ---
        HOME_BODYWEIGHT(
                "home_workout", "Treino Funcional em Casa", "45s", "12-15", "3", "3-1-1",
                "Flexão de braços no sofá, Agachamento búlgaro (cadeira), Remada com mochila, Prancha abdominal, Afundo",
                "Máquinas de ginásio, Barras olímpicas, Cabos e Polias"
        ),

        // --- ALTA INTENSIDADE & VOLUME (Exemplos anteriores mantidos) ---
        HEAVY_DUTY(
                "heavy_duty", "Heavy Duty (HIT)", "2-3 min", "6-10", "1-2", "4-2-4",
                "Leg Press, Peck Deck, Extensora, Remada na Máquina, Supino Smith",
                "Exercícios com peso livre que exigem muito equilíbrio"
        ),

        GERMAN_VOLUME(
                "german_volume", "GVT (10x10)", "60-90s", "10", "10", "4-0-2",
                "Agachamento com Barra, Supino Reto, Remada Curvada, Leg Press 45",
                "Exercícios isoladores de pequenos músculos"
        );

        // ... Resto do código (campos, construtor e métodos) ...
        private final String id;
        private final String label;
        private final String rest;
        private final String reps;
        private final String sets;
        private final String tempo;
        private final String suggestedExercises;
        private final String forbiddenExercises;

        TrainingProtocol(String id, String label, String rest, String reps, String sets, String tempo, String suggested, String forbidden) {
            this.id = id;
            this.label = label;
            this.rest = rest;
            this.reps = reps;
            this.sets = sets;
            this.tempo = tempo;
            this.suggestedExercises = suggested;
            this.forbiddenExercises = forbidden;
        }

        public static TrainingProtocol fromId(String id) {
            if (id == null) return NASM_ESTABILIZACAO;
            for (TrainingProtocol protocol : values()) {
                if (protocol.id.equalsIgnoreCase(id) || protocol.name().equalsIgnoreCase(id)) {
                    return protocol;
                }
            }
            return NASM_ESTABILIZACAO;
        }
    }
}
