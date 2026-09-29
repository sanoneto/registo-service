package com.aneto.registo_horas_service.dto.response;

import lombok.Data;
import java.time.LocalDate;

@Data
public class RegistoTreinoDTO {
    private Long id;
    private String noSocio;
    private String nomeSocio;
    private String packName; // ---> OBRIGATÓRIO
    private LocalDate data;
    private String hora;
    private Double valor;
    private Double packValor;
    private int aulasFeitas;
    private int saldo;
    private String assinatura;
}