// language: java
package com.aneto.registo_horas_service.service.impl;


import com.aneto.registo_horas_service.dto.request.RegisterRequest;
import com.aneto.registo_horas_service.dto.response.RegisterResponse;
import com.aneto.registo_horas_service.mapper.RequestMapper;
import com.aneto.registo_horas_service.models.RegistosHoras;
import com.aneto.registo_horas_service.repository.RegistroHorasRepository;
import com.aneto.registo_horas_service.service.RegistosHorasService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RegistosHorasServiceImpl implements RegistosHorasService {

    private final RegistroHorasRepository registroHorasRepository;
    private final RequestMapper requestMapper;

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
        // Retorna horas em formato decimal (ex: 8.5 para 8h30)
        return duration.toMinutes() / 60.0;
    }

    @Override
    public List<RegisterResponse> findAllRegisteredHours() {
        return registroHorasRepository.findAll().stream()
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
    public RegisterResponse atualizarRegistro(UUID publicId, RegisterRequest request) {
        RegistosHoras existing = registroHorasRepository.findByPublicId(publicId)
                .orElseThrow(() -> new RuntimeException("Registro não encontrado."));

        // Atualiza campos
        existing.setDataRegisto(request.dataRegisto());
        existing.setHoraEntrada(request.horaEntrada());
        existing.setHoraSaida(request.horaSaida());
        existing.setDescricao(request.descricao());

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
}