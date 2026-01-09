package com.aneto.registo_horas_service.service;

import com.aneto.registo_horas_service.dto.request.EventRequest;
import com.aneto.registo_horas_service.dto.request.PushSubscriptionDTO;
import com.aneto.registo_horas_service.dto.response.EventsResponse;

import java.util.List;
import java.util.UUID;

public interface EventsService {
    EventsResponse criarEvento (EventRequest request);
    List<EventsResponse> listAll ();
     void agendarAlerta(EventRequest request);
    void enviarNotificacaoPush(PushSubscriptionDTO sub, String msg);
    void confirmarAlerta(UUID id);
}
