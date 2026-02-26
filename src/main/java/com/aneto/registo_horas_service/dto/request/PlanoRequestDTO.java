package com.aneto.registo_horas_service.dto.request;

import com.aneto.registo_horas_service.models.Enum;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Transformado de Record para Classe para garantir compatibilidade com Jackson
 * e evitar UnsupportedOperationException durante a validação de Enums.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlanoRequestDTO {

    @NotBlank(message = "O nome é obrigatório")
    @Size(min = 3, max = 100, message = "O nome deve ter entre 3 e 100 caracteres")
    private String nomeAluno;

    @NotBlank(message = "O objetivo é obrigatório")
    private String objetivo;

    @NotBlank(message = "O especialista deve ser informado")
    private String especialista;

    // CORREÇÃO: Enums usam @NotNull, não @NotBlank
    @NotNull(message = "O estado do plano é obrigatório")
    private Enum.EstadoPlano estadoPlano;

    @NotNull(message = "O estado do pedido é obrigatório")
    private Enum.EstadoPedido estadoPedido;

    @NotBlank(message = "O link é obrigatório")
    private String link;

    private String recommended;
}