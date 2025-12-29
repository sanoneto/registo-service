package com.aneto.registo_horas_service.models;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "jogos_tv")
@Data // Se usares Lombok (gera getters/setters)
@NoArgsConstructor
@AllArgsConstructor
public class JogoTV {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false)
    private String equipas; // Ex: "Benfica vs Porto"
    private String canal;  // Ex: "Sport TV 1"
    private String hora;    // Ex: "20:45"
    private String competicao;

    // Útil para saber quando os dados foram atualizados pela última vez
    private LocalDateTime dataCriacao;

    @PrePersist
    protected void onCreate() {
        dataCriacao = LocalDateTime.now();
    }
}