package com.aneto.registo_horas_service.service.impl;

import com.aneto.registo_horas_service.dto.request.PlanoRequestDTO;
import com.aneto.registo_horas_service.dto.request.UserProfileRequest;
import com.aneto.registo_horas_service.dto.response.PlanoResponseDTO;
import com.aneto.registo_horas_service.models.Enum;
import com.aneto.registo_horas_service.service.PlanoService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * NOVO: centraliza a criação/atualização do registo de um plano na tabela "planos",
 * extraída de TrainingPlanServiceImpl.salvarDadosDoPlano (cujo conteúdo de negócio
 * -- decidir nomeAluno/especialista consoante há ou não aluno com conta -- se mantém
 * idêntico). A diferença chave: aqui o estadoPedido é um PARÂMETRO em vez de estar
 * fixo (PENDENTE na criação, FINALIZADO na atualização), para suportar o ciclo de
 * vida assíncrono A_PROCESSAR -> FINALIZADO / ERRO.
 *
 * Usado por:
 *  - TrainingPlanServiceImpl.iniciarGeracaoAssincrona (regista A_PROCESSAR, síncrono)
 *  - TrainingGenerationAsyncService (regista FINALIZADO ou ERRO, em background)
 */
@Component
@RequiredArgsConstructor
public class PlanoRegistoHelper {

    private final PlanoService planoService;

    /**
     * Cria o registo (quando planId é null/vazio) ou atualiza o existente, sempre com o
     * estadoPedido indicado. Devolve o PlanoResponseDTO resultante — importante no caso de
     * criação, porque é aí que se obtém o "id" definitivo gerado pela BD, que tem de ser
     * reutilizado em chamadas seguintes (nunca voltar a passar planId=null, ou cria-se um
     * segundo registo em vez de atualizar o primeiro).
     */
    public PlanoResponseDTO upsert(String username, UserProfileRequest request, String key, String planId,
                                   int semanaCiclo, boolean isDeload, Enum.EstadoPedido estadoPedido,
                                   String erroMensagem) {

        boolean isAlunoSemConta = request.getAlunoTempId() != null && !request.getAlunoTempId().isBlank();

        if (planId == null || planId.isEmpty()) {
            boolean temNomeAluno = request.getStudentName() != null && !request.getStudentName().isBlank();
            String nomeNoPlano = temNomeAluno ? request.getStudentName() : username;
            String especialista = temNomeAluno ? username : "Sem Especialista";
            String recommended = temNomeAluno ? request.getRecommended() : username;

            PlanoRequestDTO dto = PlanoRequestDTO.builder()
                    .nomeAluno(nomeNoPlano)
                    .objetivo(request.getObjective())
                    .especialista(especialista)
                    .estadoPlano(Enum.EstadoPlano.ATIVO)
                    .estadoPedido(estadoPedido)
                    .link(key)
                    .recommended(recommended)
                    .semanaCiclo(semanaCiclo)
                    .deload(isDeload)
                    .contaAssociada(!isAlunoSemConta)
                    .alunoTempId(isAlunoSemConta ? request.getAlunoTempId() : null)
                    .erroMensagem(erroMensagem)
                    .build();
            return planoService.createPlano(dto);
        }

        PlanoResponseDTO planoExistente = planoService.getByPlanoById(UUID.fromString(planId));
        if (planoExistente == null) {
            throw new RuntimeException("Plano não encontrado para o ID: " + planId);
        }

        boolean temNomeAluno = request.getStudentName() != null && !request.getStudentName().isBlank();
        String especialistaAtualizado = temNomeAluno ? username : "Sem Especialista";

        PlanoRequestDTO dto = PlanoRequestDTO.builder()
                .nomeAluno(planoExistente.getNomeAluno())
                .objetivo(planoExistente.getObjetivo())
                .especialista(especialistaAtualizado)
                .estadoPlano(Enum.EstadoPlano.ATIVO)
                .estadoPedido(estadoPedido)
                .link(key)
                .recommended(planoExistente.getRecommended())
                .semanaCiclo(semanaCiclo)
                .deload(isDeload)
                .contaAssociada(planoExistente.isContaAssociada())
                .alunoTempId(planoExistente.getAlunoTempId())
                .erroMensagem(erroMensagem)
                .build();
        planoService.updatePlano(planId, dto);
        return planoService.getByPlanoById(UUID.fromString(planId));
    }
}