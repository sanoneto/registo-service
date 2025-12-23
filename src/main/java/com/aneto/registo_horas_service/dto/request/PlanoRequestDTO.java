package com.aneto.registo_horas_service.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.With;

@With
public record PlanoRequestDTO(

        @NotBlank(message = "O nome é obrigatório")
        @Size(min = 3, max = 100, message = "O nome deve ter entre 3 e 100 caracteres")
        String nomeAluno,

        @NotBlank(message = "O objetivo é obrigatório")
        String objetivo,

        @NotBlank(message = "O especialista deve ser informado")
        String especialista,

        @NotBlank(message = "O estado do plano é obrigatório")
        String estadoPlano,

        @NotBlank(message = "O estado do pedido é obrigatório")
        String estadoPedido,

        @NotBlank(message = "O link é obrigatório")
        String link
) {
}