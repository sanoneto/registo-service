package com.aneto.registo_horas_service.dto.response;

import lombok.Data;

import java.time.LocalDate;

@Data
public class RegistoTreinoDTO {
    private Long id;
    private String nomeSocio;
    private String noSocio;
    private LocalDate data;
    private String hora;
    private int aulasFeitas;
    private String packValor;
    private int saldo;
    private String assinatura;
}