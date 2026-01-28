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
        // --- FAMÍLIA NASM OPT ---
        NASM_ESTABILIZACAO(
                "nasm_estabilizacao", "NASM OPT: Estabilização", "0-90s", "12-20", "1-3", "4-2-1",
                "Prancha, Elevação Pélvica, Agachamento Cálice (Slow), Flexão de Braços com pausa, Deadbug, Equilíbrio Unipodal",
                "Saltos, Cargas máximas, Movimentos explosivos, Agachamento com barra livre pesada"
        ),
        NASM_FORCA_RESISTENCIA(
                "nasm_forca_resistencia", "NASM OPT: Força de Resistência", "60-90s", "8-12", "2-4", "2-0-2",
                "Superset: Supino reto + Flexão de braços, Agachamento + Avanço, Remada sentada + TRX",
                "Exercícios de potência máxima ou baixas repetições (1-5 reps)"
        ),
        NASM_HIPERTROFIA(
                "nasm_hipertrofia", "NASM OPT: Hipertrofia Muscular", "0-60s", "6-12", "3-5", "2-0-2",
                "Supino inclinado com halteres, Leg Press 45, Rosca Direta, Tríceps Pulley, Remada Serrote",
                "Exercícios excessivamente instáveis que limitam a carga (ex: agachamento na bola)"
        ),
        NASM_FORCA_MAXIMA(
                "nasm_forca_maxima", "NASM OPT: Força Máxima", "3-5 min", "1-5", "4-6", "Explosivo",
                "Agachamento com barra, Levantamento Terra, Supino Reto, Press Militar",
                "Exercícios isoladores em máquinas de cabo (não servem para força máxima)"
        ),
        NASM_POTENCIA(
                "nasm_potencia", "NASM OPT: Potência Explosiva", "3-5 min", "1-10", "3-5", "Máxima Velocidade",
                "Medicine Ball Slam, Saltos verticais, Arremesso de kettlebell, Supino explosivo",
                "Séries levadas até a falha muscular lenta"
        ),

        // --- ALTA INTENSIDADE & VOLUME ---
        HEAVY_DUTY(
                "heavy_duty", "Heavy Duty (HIT)", "2-3 min", "6-10", "1-2", "4-2-4",
                "Leg Press, Peck Deck, Extensora, Remada na Máquina, Supino Smith",
                "Exercícios com peso livre que exigem muito equilíbrio (risco alto na falha total)"
        ),
        GERMAN_VOLUME(
                "german_volume", "GVT (10x10)", "60-90s", "10", "10", "4-0-2",
                "Agachamento com Barra, Supino Reto, Remada Curvada, Leg Press 45",
                "Exercícios isoladores de pequenos músculos (ex: elevação lateral) para as 10 séries principais"
        ),

        // --- AVANÇADOS / ESTÉTICA ---
        FST_7(
                "f_st7", "FST-7 (Fascia Stretch Training)", "30-45s", "8-12", "7", "2-0-2",
                "Finalizadores: Crossover, Peck Deck, Cadeira Extensora, Elevação Lateral (Cabo)",
                "Movimentos de potência no final do treino"
        ),
        PHAT(
                "phat", "PHAT (Power/Hypertrophy)", "2-3 min", "3-20", "3-5", "Variado",
                "Dias de Força: Terra, Agachamento. Dias de Hipertrofia: Máquinas e Cabos com alto volume",
                "Repetição excessiva da mesma técnica todos os dias"
        );

        // Getters
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
            // Tenta encontrar pelo ID ou pelo nome do Enum
            for (TrainingProtocol protocol : values()) {
                if (protocol.id.equalsIgnoreCase(id) || protocol.name().equalsIgnoreCase(id)) {
                    return protocol;
                }
            }
            return NASM_ESTABILIZACAO;
        }
    }


}
