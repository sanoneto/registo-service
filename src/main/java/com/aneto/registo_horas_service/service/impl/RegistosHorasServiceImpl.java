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
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
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
    public RegisterResponse atualizarRegistro(UUID publicId, RegisterRequest request, String username) throws Exception{
        RegistosHoras existing = registroHorasRepository.findByPublicId(publicId)
                .orElseThrow(() -> new RuntimeException("Registro não encontrado."));

        String dadosAnteriores = objectMapper.writeValueAsString(existing);

        // Atualiza campos
        existing.setDataRegisto(request.dataRegisto());
        existing.setHoraEntrada(request.horaEntrada());
        existing.setHoraSaida(request.horaSaida());
        existing.setDescricao(request.descricao());

        // 4. Salva a entrada no Histórico
        RegistoHistorico historico= RegistoHistorico.builder()
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
    public double getTotalHorasPorUsuario(String username) {
        // Assume-se que o repositório tem um método de soma
        Double total = registroHorasRepository.sumHorasCalculadasByEstagiarioUsername(username);
        return total != null ? total : 0.0;
    }

    @Override
    public double getTotalHorasPorUsuarioProjrct(String username, String project_name) {
        Double total = registroHorasRepository.sumHorasCalculadasByEstagiarioUsernameByProject(username,project_name);
        return total != null ? total : 0.0;
    }

    @Override
    public List<PerfilResponse>  findTotalHoursAndRequiredHoursByUserName(String name) {
        return registroHorasRepository.findTotalHoursAndRequiredHoursByUserName(name);
    }

    @Override
    public List<MonthlySummary> findMonthlySummary() {
        return registroHorasRepository.findMonthlySummary();
    }

    @Override
    public List<MonthlySummary> findMonthlySummary(String name) {
        return registroHorasRepository.findMonthlySummaryByUsername(name);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<RegisterResponse> findAllRegisteredHoursUser(String name, Pageable pageable) {
        log.debug("Buscando registros paginados do usuário: {} - Página: {}, Tamanho: {}",
                name, pageable.getPageNumber(), pageable.getPageSize());

        Page<RegistosHoras> page = registroHorasRepository.findByUserName(name, pageable);
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
    public PageResponse<RegisterResponse> findAllRegisteredHoursPage(Pageable pageable) {
        log.debug("Buscando registros paginados - Página: {}, Tamanho: {}",
                pageable.getPageNumber(), pageable.getPageSize());

        Page<RegistosHoras> page = registroHorasRepository.findAll(pageable);
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