package com.aneto.registo_horas_service.service;

import com.aneto.registo_horas_service.dto.request.EventRequest;
import com.aneto.registo_horas_service.dto.request.PushSubscriptionDTO;
import com.aneto.registo_horas_service.dto.response.EventsResponse;

import java.util.List;
import java.util.UUID;

public interface EventsService {
    EventsResponse create(EventRequest request, String googleToken);
    List<EventsResponse> listAll ();
     void agendarAlerta(EventRequest request);
    void enviarNotificacaoPush(PushSubscriptionDTO sub, String msg);
    void confirmarAlerta(UUID id);
    //void deleteById(UUID id);
    void deleteById(UUID id, String googleToken);
    EventsResponse findById(UUID id);
    EventsResponse update(UUID id, EventRequest request);
    List<EventsResponse> syncFromGoogle(String googleToken);
}
