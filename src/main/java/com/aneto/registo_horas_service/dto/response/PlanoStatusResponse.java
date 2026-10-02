package com.aneto.registo_horas_service.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Resposta do endpoint de polling (GET /plan/{planId}/status), usado pelo frontend
 * para acompanhar uma geração de plano que corre em background.
 *
 * Contrato:
 *  - estadoPedido = "A PROCESSAR" -> "plano" vem null, o cliente deve voltar a tentar
 *    daqui a alguns segundos.
 *  - estadoPedido = "FINALIZADO"  -> "plano" vem preenchido com o plano completo
 *    (lido do S3), "erroMensagem" vem null.
 *  - estadoPedido = "ERRO"        -> "plano" vem null, "erroMensagem" descreve a causa
 *    (mensagem já resumida/truncada, nunca o stacktrace completo).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlanoStatusResponse {
    private String planId;
    private String estadoPedido;
    private String erroMensagem;
    private TrainingPlanResponse plano;
}