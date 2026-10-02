package com.aneto.registo_horas_service.service.impl;

import com.aneto.registo_horas_service.dto.request.PlanoRequestDTO;
import com.aneto.registo_horas_service.dto.response.PlanoResponseDTO;
import com.aneto.registo_horas_service.mapper.PlanoMapper;
import com.aneto.registo_horas_service.models.Enum;
import com.aneto.registo_horas_service.models.Plano;
import com.aneto.registo_horas_service.repository.PlanoRepository;
import com.aneto.registo_horas_service.service.PlanoService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Convenção de logs:
 * - DEBUG: diagnóstico detalhado (desligado por defeito em produção; liga-se por logger).
 * - INFO : eventos de escrita relevantes (criar/apagar/atualizar).
 * - WARN / ERROR: falhas reais, sempre visíveis.
 */
@Service
@RequiredArgsConstructor
public class PlanoServiceImpl implements PlanoService {
    private static final Logger log = LoggerFactory.getLogger(PlanoServiceImpl.class);

    private final PlanoRepository repository;
    private final PlanoMapper mapper;

    @Transactional
    public PlanoResponseDTO createPlano(PlanoRequestDTO request) {
        log.debug("[createPlano] nomeAluno='{}' | especialista='{}' | estadoPlano={} | estadoPedido={} | link='{}' | semanaCiclo={} | deload={} | contaAssociada={} | alunoTempId='{}'",
                request.getNomeAluno(), request.getEspecialista(), request.getEstadoPlano(),
                request.getEstadoPedido(), request.getLink(), request.getSemanaCiclo(),
                request.isDeload(), request.isContaAssociada(), request.getAlunoTempId());

        Plano plano = mapper.toEntity(request);
        Plano salvo = repository.save(plano);
        log.info("[createPlano] Plano criado | id={} | nomeAluno='{}'", salvo.getId(), salvo.getNomeAluno());
        return mapper.toResponse(salvo);
    }

    @Transactional(readOnly = true)
    public PlanoResponseDTO getByPlanoById(UUID id) {
        log.debug("[getByPlanoById] id={}", id);
        Plano plano = repository.findById(id)
                .orElseThrow(() -> {
                    // WARN: é este o caso "plano não existe nesta BD" (ex.: ID vindo de outro ambiente)
                    log.warn("[getByPlanoById] Plano NÃO encontrado na BD | id={}", id);
                    return new NoSuchElementException("Plano não encontrado com o ID: " + id);
                });
        log.debug("[getByPlanoById] encontrado | id={} | nomeAluno='{}' | estadoPlano={} | estadoPedido={} | link='{}'",
                plano.getId(), plano.getNomeAluno(), plano.getEstadoPlano(), plano.getEstadoPedido(), plano.getLink());
        return mapper.toResponse(plano);
    }

    @Transactional
    public void deletePlano(UUID id) {
        log.debug("[deletePlano] id={}", id);
        if (!repository.existsById(id)) {
            log.warn("[deletePlano] Plano não encontrado para apagar | id={}", id);
            throw new RuntimeException("Não é possível Deletar: Plano não encontrado");
        }
        repository.deleteById(id);
        log.info("[deletePlano] Plano apagado | id={}", id);
    }

    @Transactional(readOnly = true)
    public Page<PlanoResponseDTO> listAllOrName(String nomeAluno, String estadoPlanoStr, Pageable pageable, List<String> roles, String usernameLogado) {
        log.debug("[listAllOrName] ENTRADA | nomeAluno='{}' | estadoPlano='{}' | page={} | size={} | roles={} | usernameLogado='{}'",
                nomeAluno, estadoPlanoStr,
                pageable != null ? pageable.getPageNumber() : null,
                pageable != null ? pageable.getPageSize() : null,
                roles, usernameLogado);

        Page<Plano> entidadePage;

        // CORREÇÃO: Sanitiza o filtro nomeAluno (converte "" ou "   " para null)
        String nomeFiltro = (nomeAluno != null && !nomeAluno.trim().isEmpty()) ? nomeAluno.trim() : null;
        boolean temNome = nomeFiltro != null;

        Enum.EstadoPlano estadoPlano = null;
        if (estadoPlanoStr != null && !estadoPlanoStr.isBlank()) {
            try {
                estadoPlano = Enum.EstadoPlano.valueOf(estadoPlanoStr.toUpperCase());
            } catch (IllegalArgumentException e) {
                log.warn("[listAllOrName] estadoPlano inválido ('{}') — ignorado.", estadoPlanoStr);
            }
        }
        boolean temEstado = estadoPlano != null;
        log.debug("[listAllOrName] filtros efetivos | nome='{}' | estado={}", nomeFiltro, estadoPlano);

        if (roles.contains("ROLE_ADMIN")) {
            log.debug("[listAllOrName] ramo ADMIN");
            if (temNome && temEstado) {
                entidadePage = repository.findByNomeAlunoContainingIgnoreCaseAndEstadoPlano(nomeFiltro, estadoPlano, pageable);
            } else if (temNome) {
                entidadePage = repository.findByNomeAlunoContainingIgnoreCase(nomeFiltro, pageable);
            } else if (temEstado) {
                entidadePage = repository.findByEstadoPlano(estadoPlano, pageable);
            } else {
                assert pageable != null;
                entidadePage = repository.findAll(pageable);
            }
        } else if (roles.contains("ROLE_ESPECIALISTA")) {
            log.debug("[listAllOrName] ramo ESPECIALISTA");
            entidadePage = temEstado
                    ? repository.findForEspecialista(usernameLogado, estadoPlano, nomeFiltro, pageable)
                    : repository.findForEspecialista(usernameLogado, nomeFiltro, pageable);
        } else {
            log.debug("[listAllOrName] ramo ESTAGIARIO/outro (roles={})", roles);
            entidadePage = temEstado
                    ? repository.findForEstagiario(usernameLogado, estadoPlano, pageable)
                    : repository.findForEstagiario(usernameLogado, pageable);
        }

        List<PlanoResponseDTO> dtoList = entidadePage.getContent()
                .stream()
                .map(mapper::toResponse)
                .collect(Collectors.toCollection(ArrayList::new));

        log.debug("[listAllOrName] RESULTADO | {} na página | total={}", dtoList.size(), entidadePage.getTotalElements());
        assert pageable != null;
        return new PageImpl<>(dtoList, pageable, entidadePage.getTotalElements());
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<PlanoResponseDTO> findAtivoAndConcluidoByUsername(String username) {
        log.debug("[findAtivoAndConcluidoByUsername] username='{}'", username);
        try {
            Optional<PlanoResponseDTO> resultado = repository.findByNomeAlunoContainingAndEstadoPlanoAndEstadoPedido(
                    username,
                    Enum.EstadoPlano.ATIVO,
                    Enum.EstadoPedido.FINALIZADO
            ).map(mapper::toResponse);

            log.debug("[findAtivoAndConcluidoByUsername] username='{}' -> {} | id={} | link='{}'",
                    username,
                    resultado.isPresent() ? "ENCONTRADO" : "NENHUM",
                    resultado.map(PlanoResponseDTO::getId).orElse(null),
                    resultado.map(PlanoResponseDTO::getLink).orElse(null));
            return resultado;
        } catch (Exception e) {
            // Ex.: IncorrectResultSizeDataAccessException se a query (Containing) devolver mais de 1 plano.
            log.error("[findAtivoAndConcluidoByUsername] ERRO na query | username='{}'", username, e);
            throw e;
        }
    }

    @Override
    @Transactional
    public void updatePlano(String uuid, PlanoRequestDTO requestDTO) {
        log.debug("[updatePlano] uuid='{}' | nomeAluno='{}' | especialista='{}' | estadoPlano={} | estadoPedido={} | link='{}' | semanaCiclo={} | deload={} | contaAssociada={} | alunoTempId='{}'",
                uuid, requestDTO.getNomeAluno(), requestDTO.getEspecialista(), requestDTO.getEstadoPlano(),
                requestDTO.getEstadoPedido(), requestDTO.getLink(), requestDTO.getSemanaCiclo(),
                requestDTO.isDeload(), requestDTO.isContaAssociada(), requestDTO.getAlunoTempId());

        UUID id = UUID.fromString(uuid);
        Plano plano = repository.findById(id)
                .orElseThrow(() -> {
                    log.warn("[updatePlano] Plano NÃO encontrado | id={}", uuid);
                    return new RuntimeException("Plano não encontrado com o ID: " + uuid);
                });

        plano.setNomeAluno(requestDTO.getNomeAluno());
        plano.setObjetivo(requestDTO.getObjetivo());
        plano.setEspecialista(requestDTO.getEspecialista());
        plano.setEstadoPlano(requestDTO.getEstadoPlano());
        plano.setEstadoPedido(requestDTO.getEstadoPedido());
        plano.setLink(requestDTO.getLink());
        plano.setSemanaCiclo(requestDTO.getSemanaCiclo());
        plano.setDeload(requestDTO.isDeload());
        // >>> NOVO
        plano.setContaAssociada(requestDTO.isContaAssociada());
        plano.setAlunoTempId(requestDTO.getAlunoTempId());

        repository.save(plano);
        log.info("[updatePlano] Plano atualizado | id={}", uuid);
    }

    @Override
    @Transactional
    public void changeOfProgress(String planId, String username, String newStatus) {
        log.debug("[changeOfProgress] planId='{}' | username='{}' | newStatus='{}'", planId, username, newStatus);

        Plano plano = repository.findById(UUID.fromString(planId))
                .orElseThrow(() -> {
                    log.warn("[changeOfProgress] Plano NÃO encontrado | id={}", planId);
                    return new RuntimeException("Plano não encontrado");
                });

        if (plano.getEstadoPedido() == Enum.EstadoPedido.PENDENTE) {

            Enum.EstadoPedido proximoEstado = Enum.EstadoPedido.fromDescricao(newStatus);
            log.debug("[changeOfProgress] Estado mantido: {} | novo status informado: {} | especialista='{}'",
                    plano.getEstadoPedido(), proximoEstado, username);

            // Apenas atualiza o especialista, mantendo o estado atual intacto
            plano.setEspecialista(username);
            repository.save(plano);

            log.info("[changeOfProgress] Plano {} associado a '{}' (estado mantido em {})",
                    planId, username, plano.getEstadoPedido());
        } else {
            log.debug("[changeOfProgress] IGNORADO: estado atual {} não permite alteração | planId='{}'",
                    plano.getEstadoPedido(), planId);
        }
    }

    @Override
    @Transactional
    public void prepararNovoPlanoAtivo(String username) {
        log.debug("[prepararNovoPlanoAtivo] a inativar planos ativos de username='{}'", username);
        repository.inativarPlanosAtivosPorAluno(username);
    }

    // >>> NOVO
    @Override
    @Transactional(readOnly = true)
    public Optional<PlanoResponseDTO> findAtivoAndConcluidoByAlunoTempId(String alunoTempId) {
        log.debug("[findAtivoAndConcluidoByAlunoTempId] alunoTempId='{}'", alunoTempId);
        if (alunoTempId == null || alunoTempId.isBlank()) {
            log.debug("[findAtivoAndConcluidoByAlunoTempId] alunoTempId vazio -> Optional.empty()");
            return Optional.empty();
        }
        try {
            Optional<PlanoResponseDTO> resultado = repository.findByAlunoTempIdAndEstadoPlanoAndEstadoPedido(
                    alunoTempId,
                    Enum.EstadoPlano.ATIVO,
                    Enum.EstadoPedido.FINALIZADO
            ).map(mapper::toResponse);

            log.debug("[findAtivoAndConcluidoByAlunoTempId] alunoTempId='{}' -> {} | id={}",
                    alunoTempId,
                    resultado.isPresent() ? "ENCONTRADO" : "NENHUM",
                    resultado.map(PlanoResponseDTO::getId).orElse(null));
            return resultado;
        } catch (Exception e) {
            log.error("[findAtivoAndConcluidoByAlunoTempId] ERRO na query | alunoTempId='{}'", alunoTempId, e);
            throw e;
        }
    }

    // >>> NOVO
    @Override
    @Transactional
    public void prepararNovoPlanoAtivoPorAlunoTempId(String alunoTempId) {
        log.debug("[prepararNovoPlanoAtivoPorAlunoTempId] a inativar planos ativos de alunoTempId='{}'", alunoTempId);
        repository.inativarPlanosAtivosPorAlunoTempId(alunoTempId);
    }

}