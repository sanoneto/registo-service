package com.aneto.registo_horas_service.dto.response;

import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data
public class PlanoPagamentoDTO {
    private Long id;
    private String nomeSocio;
    private String noSocio;
    private String packName;
    private LocalDate dataPack;
    private int aulasPack;
    private double valor;
    private String assinatura;
    private String tipoPack; // Adicionado conforme discutimos
    private List<RegistoTreinoDTO> registosTreino; // Mapear também os treinos
}