package com.aneto.registo_horas_service.models.Training;

import java.util.Locale;

/**
 * Representa a meta de intensidade RPE (Rating of Perceived Exertion) e RIR (Reps em Reserva).
 */
public record TargetRpe(
        double minRpe,
        double maxRpe,
        int rirMin,
        int rirMax,
        String focoFisiologico
) {

    /**
     * Formata o RPE para injeção limpa no prompt do LLM.
     */
    public String toPromptString() {
        if (minRpe == maxRpe) {
            return String.format(Locale.US, "RPE %.1f (%d a %d RIR - %s)", minRpe, rirMin, rirMax, focoFisiologico);
        }
        return String.format(Locale.US, "RPE %.1f-%.1f (%d a %d RIR - %s)", minRpe, maxRpe, rirMin, rirMax, focoFisiologico);
    }

    // Presets Fisiológicos Reutilizáveis
    public static final TargetRpe SEDENTARIO = new TargetRpe(5.0, 6.0, 4, 5, "Adaptação Anatómica / Longe da Falha");
    public static final TargetRpe DELOAD = new TargetRpe(5.0, 6.0, 4, 5, "Recuperação Sistémica / Deload Programado");
    public static final TargetRpe ESTABILIZACAO = new TargetRpe(6.0, 7.0, 3, 4, "Controlo Postural e Estabilidade NASM");
    public static final TargetRpe HIPERTROFIA = new TargetRpe(7.5, 8.5, 1, 2, "Tensão Mecânica e Hipertrofia");
    public static final TargetRpe FORCA_MAXIMA = new TargetRpe(8.5, 9.5, 1, 1, "Recrutamento de Unidades Motoras / Alta Carga");
    public static final TargetRpe RESISTENCIA = new TargetRpe(7.0, 8.0, 2, 3, "Resistência Muscular Localizada");

    // Presets Adicionados (Exigidos pelo TrainingProtocol)
    public static final TargetRpe FORCA_POTENCIA = new TargetRpe(8.0, 9.0, 1, 2, "Taxa de Desenvolvimento de Força (RFD) / Trabalho Potente");
    public static final TargetRpe HIPERTROFIA_METABOLICA = new TargetRpe(7.0, 8.5, 1, 3, "Stress Metabólico e Expansão Fascial");
    public static final TargetRpe REABILITACAO_ARTICULAR = new TargetRpe(5.0, 6.5, 3, 5, "Tempo Sob Tensão Controlado / Reeducação Neuromuscular");
}