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

    // >>> NOVO: periodização de treino
    @Builder.Default
    private int semanaCiclo = 1;

    private boolean deload;

    // >>> NOVO: suporte a "aluno sem conta"
    // true  = plano associado a uma conta real (fluxo normal, comportamento atual)
    // false = plano criado por um especialista para um aluno fictício/sem registo
    @Builder.Default
    private boolean contaAssociada = true;

    // >>> NOVO: identificador estável do aluno fictício, gerado no momento da
    // criação do primeiro plano (UUID string). Fica null quando contaAssociada=true,
    // porque nesse caso já usamos o username real para tudo.
    private String alunoTempId;
}