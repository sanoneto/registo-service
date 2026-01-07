package com.aneto.registo_horas_service.service.impl;

import com.aneto.registo_horas_service.dto.request.PlanoRequestDTO;
import com.aneto.registo_horas_service.dto.response.PlanoResponseDTO;
import com.aneto.registo_horas_service.mapper.PlanoMapper;
import com.aneto.registo_horas_service.models.EstadoPedido;
import com.aneto.registo_horas_service.models.EstadoPlano;
import com.aneto.registo_horas_service.models.Plano;
import com.aneto.registo_horas_service.repository.PlanoRepository;
import com.aneto.registo_horas_service.service.PlanoService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    public Page<PlanoResponseDTO> listAllOrName(String nome, Pageable pageable) {
        // Se o nome for uma string vazia, a query nativa tratará como "trazer tudo"
        Page<Plano> planos = repository.findByNomeCustom(nome, pageable);

        // Converte a página de Entidades para página de ResponseDTO (remove links e datas)
        return planos.map(mapper::toResponse);
    }

    @Override
    public Optional<PlanoResponseDTO> findAtivoAndConcluidoByUsername(String username) {
        // Aqui assume-se que o seu Repository tem esta consulta
        return repository.findByNomeAlunoContainingAndEstadoPlanoAndEstadoPedido(
                username,
                EstadoPlano.ATIVO,        // Uso do Enum
                EstadoPedido.FINALIZADO   // Uso do Enum
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
        plano.setEstadoPlano(EstadoPlano.valueOf(requestDTO.estadoPlano()));
        plano.setEstadoPedido(EstadoPedido.valueOf(requestDTO.estadoPedido()));
        plano.setLink(requestDTO.link());

        // 4. Gravar as alterações
        repository.save(plano);
    }

    @Override
    public void changeOfProgress(String planId, String username, String newStatus) {
        // 1. Busca o plano no banco
        Plano plano = repository.findById(UUID.fromString(planId))
                .orElseThrow(() -> new RuntimeException("Plano não encontrado"));

        if (plano.getEstadoPedido() == EstadoPedido.PENDENTE) {
            // 2. Atribuição direta (newStatus deve vir como o Enum EstadoPedido)
            plano.setEstadoPedido(EstadoPedido.valueOf(newStatus.toUpperCase()));
            repository.save(plano);
        }
    }
}
