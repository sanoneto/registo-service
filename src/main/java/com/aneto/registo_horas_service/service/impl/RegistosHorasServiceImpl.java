// language: java
package com.aneto.registo_horas_service.service.impl;


import com.aneto.registo_horas_service.dto.request.RegisterRequest;
import com.aneto.registo_horas_service.dto.response.MonthlySummary;
import com.aneto.registo_horas_service.dto.response.PageResponse;
import com.aneto.registo_horas_service.dto.response.PerfilResponse;
import com.aneto.registo_horas_service.dto.response.RegisterResponse;
import com.aneto.registo_horas_service.mapper.RequestMapper;
import com.aneto.registo_horas_service.models.OperacaoAuditoria;
import com.aneto.registo_horas_service.models.RegistoHistorico;
import com.aneto.registo_horas_service.models.RegistosHoras;
import com.aneto.registo_horas_service.repository.RegistoHistoricoRepository;
import com.aneto.registo_horas_service.repository.RegistroHorasRepository;
import com.aneto.registo_horas_service.service.RegistosHorasService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RegistosHorasServiceImpl implements RegistosHorasService {

    private static final Logger log = LoggerFactory.getLogger(RegistosHorasServiceImpl.class);
    private final RegistroHorasRepository registroHorasRepository;
    private final RegistoHistoricoRepository registoHistoricoRepository;
    private final RequestMapper requestMapper;
    private final ObjectMapper objectMapper; // Jackson ou similar para JSON

    private final EntityManager entityManager;

    // 🔑 Injete a Query do YAML
    @Value("${app.queries.user-required-vs-total-hours}")
    private String userRequiredVsTotalHoursQuery;

    // 🔑 Injete a Query do YAML
    @Value("${app.queries.monthly-summary}")
    private String monthlySummaryQuery;

    @Override
    public RegisterResponse submeterHoras(RegisterRequest request, String username) {
        RegistosHoras registro = requestMapper.mapToRegisterHoras(request);

        // Atribui o username obtido do token
        registro.setUserName(username);

        // 1. Cálculo das Horas
        registro.setHorasTrabalhadas(calcularHoras(registro.getHoraEntrada(), registro.getHoraSaida()));

        RegistosHoras saved = registroHorasRepository.save(registro);
        return requestMapper.toResponse(saved);
    }

    private double calcularHoras(LocalTime entrada, LocalTime saida) {
        if (entrada == null || saida == null || saida.isBefore(entrada)) {
            return 0.0;
        }
        Duration duration = Duration.between(entrada, saida);

        // Calcula as horas em formato decimal
        double horasDecimais = duration.toMinutes() / 60.0;

        // Arredonda o valor para uma casa decimal
        // 1. Multiplica por 10 (ex: 2.666... vira 26.666...)
        // 2. Arredonda para o inteiro mais próximo (ex: 27)
        // 3. Divide por 10.0 (ex: 27 vira 2.7)
        return Math.round(horasDecimais * 10.0) / 10.0;
    }

    @Override
    public List<RegisterResponse> findAllRegisteredHours() {
        Sort sortByDataRegisto = Sort.by(Sort.Direction.DESC, "dataRegisto");
        return registroHorasRepository.findAll(sortByDataRegisto).stream()
                .map(requestMapper::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    public List<RegisterResponse> buscarRegistrosPorUsuario(String username) {
        return registroHorasRepository.findByUserName(username).stream()
                .map(requestMapper::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    public RegisterResponse atualizarRegistro(UUID publicId, RegisterRequest request, String username) throws Exception {
        RegistosHoras existing = registroHorasRepository.findByPublicId(publicId)
                .orElseThrow(() -> new RuntimeException("Registro não encontrado."));

        String dadosAnteriores = objectMapper.writeValueAsString(existing);

        // Atualiza campos
        existing.setDataRegisto(request.dataRegisto());
        existing.setHoraEntrada(request.horaEntrada());
        existing.setHoraSaida(request.horaSaida());
        existing.setDescricao(request.descricao());

        // 4. Salva a entrada no Histórico
        RegistoHistorico historico = RegistoHistorico.builder()
                .registoPublicId(String.valueOf(publicId))
                .operacao(OperacaoAuditoria.UPDATE)
                .dataAlteracao(LocalDateTime.now())
                .utilizadorAlteracao(username)
                .dadosAnterioresJson(dadosAnteriores)
                .build();

        registoHistoricoRepository.save(historico);

        // Recalcula horas
        existing.setHorasTrabalhadas(calcularHoras(existing.getHoraEntrada(), existing.getHoraSaida()));

        RegistosHoras updated = registroHorasRepository.save(existing);
        return requestMapper.toResponse(updated);
    }

    @Override
    public void deleteRegistry(UUID publicId) {
        RegistosHoras registro = registroHorasRepository.findByPublicId(publicId)
                .orElseThrow(() -> new RuntimeException("Registro não encontrado."));
        registroHorasRepository.delete(registro);
    }

    @Override
    public double getTotalHorasPorUsuarioProjrct(String username, String project_name) {
        Optional<Double> total = registroHorasRepository.findSumHorasTrabalhadasByProjectName(project_name);
        return total.orElse(0.0);

    }

    @Transactional(readOnly = true)
    public List<PerfilResponse> findTotalHoursAndRequiredHoursByUserName(String username) {

        // 1. Cria a Native Query
        Query nativeQuery = entityManager.createNativeQuery(userRequiredVsTotalHoursQuery);

        // 2. Define o parâmetro
        nativeQuery.setParameter("username", username);

        // 3. Obtém o resultado como Object[] e mapeia manualmente para o DTO
        @SuppressWarnings("unchecked")
        List<Object[]> results = nativeQuery.getResultList();

        return results.stream()
                .map(row -> {
                    // 1. Recebe o valor como BigDecimal (o tipo real retornado)
                    java.math.BigDecimal totalHorasBd = (java.math.BigDecimal) row[4];

                    // 2. Converte para Double usando doubleValue()
                    Double totalHorasDouble = totalHorasBd.doubleValue();

                    return new PerfilResponse(
                            (String) row[0],
                            (String) row[1],
                            (String) row[2],
                            (Double) row[3], // required_hours (Mantido como Double)
                            totalHorasDouble // Total_horas_trabalhadas (Convertido para Double)
                    );
                })
                .collect(Collectors.toList());
    }

    /**
     * Exemplo para o MonthlySummary (Query sem parâmetros)
     */
    @Transactional(readOnly = true)
    public List<MonthlySummary> findMonthlySummary(String username) {

        // O username pode ser null para indicar "todos", conforme a lógica SQL.
        // Se o cliente passar uma string vazia (""), converta para null ou "all" para padronização.
        String userToFilter = (username != null && !username.trim().isEmpty()) ? username.trim() : "all";

        // OBS: O 'monthlySummaryQuery' DEVE conter a lógica condicional "WHERE :username IS NULL OR :username = 'all' OR rh.username = :username"
        Query nativeQuery = entityManager.createNativeQuery(monthlySummaryQuery);

        nativeQuery.setParameter("username", userToFilter);

        // 3. Obtém o resultado
        @SuppressWarnings("unchecked")
        List<Object[]> results = nativeQuery.getResultList();

        // 4. Mapeamento do resultado para o objeto MonthlySummary
        return results.stream()
                .map(row -> new MonthlySummary(
                        (String) row[0], // mes_e_ano
                        ((Number) row[1]).doubleValue()  // total_horas_trabalhadas
                ))
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<RegisterResponse> findAllRegisteredHoursUserProjectName(String name, String projectName, Pageable pageable) {
        log.debug("Buscando registros paginados do usuário: {} - Página: {}, Tamanho: {}",
                name, pageable.getPageNumber(), pageable.getPageSize());
        Page<RegistosHoras> page;
        if (projectName.equals("all")) {
            page = registroHorasRepository.findByUserName(name, pageable);
        } else {
            page = registroHorasRepository.findByUserNameAndProjectName(name, projectName, pageable);
        }
        List<RegisterResponse> content = requestMapper.mapToListRegisterResponse(page.getContent());

        log.info("Encontrados {} registros para o usuário {} na página {} de {}",
                page.getNumberOfElements(), name, page.getNumber(), page.getTotalPages());

        return PageResponse.of(
                content,
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isFirst(),
                page.isLast()
        );
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<RegisterResponse> findAllRegisteredHoursPageProjectName(String projectName,Pageable pageable) {
        log.debug("Buscando registros paginados - Página: {}, Tamanho: {}",
                pageable.getPageNumber(), pageable.getPageSize());
        Page<RegistosHoras> page;
        if (projectName.equals("all")) {
         page = registroHorasRepository.findAll(pageable);
        } else {
            page = registroHorasRepository.findByProjectName(projectName,pageable);
        }
        List<RegisterResponse> content = requestMapper.mapToListRegisterResponse(page.getContent());

        log.info("Encontrados {} registros na página {} de {}",
                page.getNumberOfElements(), page.getNumber(), page.getTotalPages());

        return PageResponse.of(
                content,
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isFirst(),
                page.isLast()
        );
    }


}