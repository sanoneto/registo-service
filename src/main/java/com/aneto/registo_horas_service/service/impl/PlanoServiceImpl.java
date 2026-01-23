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
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PlanoServiceImpl implements PlanoService {
    private final PlanoRepository repository;

    private final PlanoMapper mapper;

    @Transactional
    public PlanoResponseDTO createPlano(PlanoRequestDTO request) {
        // Converte Request -> Entidade
        Plano entidade = mapper.toEntity(request);

        // Salva no banco (Gera UUID e Datas automaticamente)
        Plano salvo = repository.save(entidade);

        // Retorna o Response (Filtra links e datas automaticamente via Mapper)
        return mapper.toResponse(salvo);
    }

    @Transactional(readOnly = true)
    public PlanoResponseDTO getByPlanoById(UUID id) {
        // Busca a entidade no banco ou lança erro se não encontrar
        Plano plano = repository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Plano não encontrado com o ID: " + id));

        // Converte Entidade -> ResponseDTO
        return mapper.toResponse(plano);
    }

    @Transactional
    public void deletePlano(UUID id) {
        // Verificamos se existe antes de deletar para evitar erros
        if (!repository.existsById(id)) {
            throw new RuntimeException("Não é possível deletar: Plano não encontrado");
        }
        repository.deleteById(id);
    }

    @Transactional(readOnly = true)
    public Page<PlanoResponseDTO> listAllOrName(String nomeAluno, Pageable pageable, List<String> roles, String usernameLogado) {

        // 1. ADMIN: Vê tudo (Pode filtrar por nome de aluno se quiser)
        if (roles.contains("ROLE_ADMIN")) {
            if (nomeAluno != null && !nomeAluno.isEmpty()) {
                return repository.findByNomeAlunoContainingIgnoreCase(nomeAluno, pageable).map(mapper::toResponse);
            }
            return repository.findAll(pageable).map(mapper::toResponse);
        }

        // 2. ESPECIALISTA: Vê os seus + os que não têm dono
        if (roles.contains("ROLE_ESPECIALISTA")) {
            return repository.findForEspecialista(usernameLogado, pageable)
                    .map(mapper::toResponse);
        }

        // 3. ESTAGIÁRIO: Vê estritamente apenas os seus
        if (roles.contains("ROLE_ESTAGIARIO")) {
            return repository.findForEstagiario(usernameLogado, pageable)
                    .map(mapper::toResponse);
        }

        return Page.empty(); // Se não tiver role, retorna vazio
    }

    @Override
    public Optional<PlanoResponseDTO> findAtivoAndConcluidoByUsername(String username) {
        // Aqui assume-se que o seu Repository tem esta consulta
        return repository.findByNomeAlunoContainingAndEstadoPlanoAndEstadoPedido(
                username,
                Enum.EstadoPlano.ATIVO,        // Uso do Enum
                Enum.EstadoPedido.FINALIZADO   // Uso do Enum
        ).map(mapper::toResponse); // Converta para DTO antes de retornar
    }

    @Override
    @Transactional
    public void updatePlano(String uuid, PlanoRequestDTO requestDTO) {
        // 1. Converter a String para UUID e procurar a entidade
        UUID id = UUID.fromString(uuid);

        // 2. Procurar o plano existente (lança exceção se não encontrar)
        Plano plano = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Plano não encontrado com o ID: " + uuid));

        // 3. Atualizar os campos da entidade com os dados do DTO
        // Assumindo que a sua entidade 'Plano' tem estes setters:
        plano.setNomeAluno(requestDTO.nomeAluno());
        plano.setObjetivo(requestDTO.objetivo());
        plano.setEspecialista(requestDTO.especialista());
        plano.setEstadoPlano(requestDTO.estadoPlano());
        plano.setEstadoPedido(requestDTO.estadoPedido());
        plano.setLink(requestDTO.link());

        // 4. Gravar as alterações
        repository.save(plano);
    }

    @Override
    @Transactional
    public void changeOfProgress(String planId, String username, String newStatus) {
        // 1. Busca o plano
        Plano plano = repository.findById(UUID.fromString(planId))
                .orElseThrow(() -> new RuntimeException("Plano não encontrado"));

        // 2. Só altera se estiver PENDENTE
        if (plano.getEstadoPedido() == Enum.EstadoPedido.PENDENTE) {

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
        String formatado = status.toUpperCase().replace(" ", "_");
        return Enum.EstadoPedido.valueOf(formatado);
    }
}
