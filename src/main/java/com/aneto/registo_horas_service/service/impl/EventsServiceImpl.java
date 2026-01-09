package com.aneto.registo_horas_service.service.impl;

import com.aneto.registo_horas_service.dto.request.EventRequest;
import com.aneto.registo_horas_service.dto.response.EventsResponse;
import com.aneto.registo_horas_service.mapper.EventsMapper;
import com.aneto.registo_horas_service.models.Evento;
import com.aneto.registo_horas_service.repository.EventoRepository;
import com.aneto.registo_horas_service.service.EventsService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class EventsServiceImpl implements EventsService {

    private final EventoRepository repository;
    private final EventsMapper mapper;
    private TaskScheduler taskScheduler;

    @Override
    public EventsResponse criarEvento(EventRequest request) {
        Evento evento = mapper.toEntity(request);
        Evento salvo = repository.save(evento);

        if (request.sendAlert() && request.notificationSubscription() != null) {
            agendarAlerta(request);
        }

        return mapper.toResponse(salvo);
    }

    @Override
    public void agendarAlerta(EventRequest request) {
        LocalDateTime dataHoraEvento = LocalDateTime.of(request.referenceDate(), request.startTime());

        // Dispara 15 minutos antes (conforme o teu checkbox dizia)
        Instant momentoAlerta = dataHoraEvento
                .atZone(ZoneId.systemDefault())
                .toInstant()
                .minus(15, ChronoUnit.MINUTES);

        taskScheduler.schedule(() -> {
            enviarNotificacaoPush(request.notificationSubscription(), request.title());
        }, momentoAlerta);
    }

    @Override
    public void enviarNotificacaoPush(Object sub, String msg) {
        // Aqui o Java envia a mensagem para o serviço de Push do Google/Apple
        System.out.println("Enviando para o telemóvel: " + msg);
    }

    @Override
    public List<EventsResponse> listAll() {
        // 1. Vai buscar todas as entidades ao banco de dados
        List<Evento> eventos = repository.findAll();

        // 2. Transforma a lista de entidades em lista de DTOs usando o mapper
        return eventos.stream()
                .map(mapper::toResponse)
                .collect(Collectors.toList());
    }

}
