package com.aneto.registo_horas_service.service.impl;

import com.aneto.registo_horas_service.dto.request.PlanoRequestDTO;
import com.aneto.registo_horas_service.dto.response.PlanoResponseDTO;
import com.aneto.registo_horas_service.mapper.PlanoMapper;
import com.aneto.registo_horas_service.models.Enum;
import com.aneto.registo_horas_service.models.Plano;
import com.aneto.registo_horas_service.repository.PlanoRepository;
import com.aneto.registo_horas_service.service.PlanoService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PlanoServiceImpl implements PlanoService {
    private final PlanoRepository repository;
    private final PlanoMapper mapper;

    @Transactional
    public PlanoResponseDTO createPlano(PlanoRequestDTO request) {
        Plano plano = mapper.toEntity(request);
        Plano salvo = repository.save(plano);

        // Usamos o mapper, mas garantimos que o retorno seja tratado
        return mapper.toResponse(salvo);
    }

    @Transactional(readOnly = true)
    public PlanoResponseDTO getByPlanoById(UUID id) {
        Plano plano = repository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Plano não encontrado com o ID: " + id));

        return mapper.toResponse(plano);
    }

    @Transactional
    public void deletePlano(UUID id) {
        if (!repository.existsById(id)) {
            throw new RuntimeException("Não é possível deletar: Plano não encontrado");
        }
        repository.deleteById(id);
    }

    @Transactional(readOnly = true)
    public Page<PlanoResponseDTO> listAllOrName(String nomeAluno, Pageable pageable, List<String> roles, String usernameLogado) {

        Page<Plano> entidadePage;

        if (roles.contains("ROLE_ADMIN")) {
            entidadePage = (nomeAluno != null && !nomeAluno.isEmpty())
                    ? repository.findByNomeAlunoContainingIgnoreCase(nomeAluno, pageable)
                    : repository.findAll(pageable);
        } else if (roles.contains("ROLE_ESPECIALISTA")) {
            entidadePage = repository.findForEspecialista(usernameLogado, pageable);
        } else {
            entidadePage = repository.findForEstagiario(usernameLogado, pageable);
        }

        // A MÁGICA: Transformamos em uma lista puramente Java, sem vínculos com o Page original do Hibernate
        List<PlanoResponseDTO> dtoList = entidadePage.getContent()
                .stream()
                .map(mapper::toResponse)
                .collect(Collectors.toCollection(ArrayList::new)); // ArrayList é sempre serializável

        return new PageImpl<>(dtoList, pageable, entidadePage.getTotalElements());
    }
    @Override
    @Transactional(readOnly = true)
    public Optional<PlanoResponseDTO> findAtivoAndConcluidoByUsername(String username) {
        return repository.findByNomeAlunoContainingAndEstadoPlanoAndEstadoPedido(
                username,
                Enum.EstadoPlano.ATIVO,
                Enum.EstadoPedido.FINALIZADO
        ).map(mapper::toResponse);
    }

    @Override
    @Transactional
    public void updatePlano(String uuid, PlanoRequestDTO requestDTO) {
        UUID id = UUID.fromString(uuid);
        Plano plano = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Plano não encontrado com o ID: " + uuid));

        plano.setNomeAluno(requestDTO.getNomeAluno());
        plano.setObjetivo(requestDTO.getObjetivo());
        plano.setEspecialista(requestDTO.getEspecialista());
        plano.setEstadoPlano(requestDTO.getEstadoPlano());
        plano.setEstadoPedido(requestDTO.getEstadoPedido());
        plano.setLink(requestDTO.getLink());

        repository.save(plano);
    }

    @Override
    @Transactional
    public void changeOfProgress(String planId, String username, String newStatus) {
        Plano plano = repository.findById(UUID.fromString(planId))
                .orElseThrow(() -> new RuntimeException("Plano não encontrado"));

        if (plano.getEstadoPedido() == Enum.EstadoPedido.PENDENTE ||
                plano.getEstadoPedido() == Enum.EstadoPedido.A_PROCESSAR) {

            Enum.EstadoPedido proximoEstado = Enum.EstadoPedido.fromDescricao(newStatus);
            plano.setEstadoPedido(proximoEstado);
            plano.setEspecialista(username);
            repository.save(plano);
        }
    }

    @Override
    @Transactional
    public void prepararNovoPlanoAtivo(String username) {
        repository.inativarPlanosAtivosPorAluno(username);
    }

    private Enum.EstadoPedido converterParaEnum(String status) {
        try {
            String formatado = status.toUpperCase().replace(" ", "_");
            return Enum.EstadoPedido.valueOf(formatado);
        } catch (Exception e) {
            return Enum.EstadoPedido.PENDENTE;
        }
    }
}