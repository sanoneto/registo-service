package com.aneto.registo_horas_service.service;

import com.aneto.registo_horas_service.dto.request.EventRequest;
import com.aneto.registo_horas_service.dto.response.EventsResponse;

import java.util.List;

public interface EventsService {
    EventsResponse criarEvento (EventRequest request);
    List<EventsResponse> listAll ();
     void agendarAlerta(EventRequest request);
    void enviarNotificacaoPush(Object sub, String msg);
}
