package com.aneto.registo_horas_service.service;

import com.aneto.registo_horas_service.models.JogoTV;

import java.util.List;

public interface ProgramacaoTVService {
    List<JogoTV> extrairJogos();
}
