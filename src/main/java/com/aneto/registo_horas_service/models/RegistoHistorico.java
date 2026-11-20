package com.aneto.registo_horas_service.models;

import com.aneto.registo_horas_service.models.Enum.OperacaoAuditoria;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

// Entidade de Auditoria/Histórico
@Entity
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "REGISTO_HISTORICO", schema = "REGISTOS")
public class RegistoHistorico {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID) // 🔑 Correção aqui!
    @Column(name = "public_id")
    private UUID publicId;

    private String registoPublicId; // FK para RegistoHoras.publicId

    @Enumerated(EnumType.STRING)
    private OperacaoAuditoria operacao; // ENUM: UPDATE, DELETE, CREATE

    private LocalDateTime dataAlteracao;

    private String utilizadorAlteracao;

    @Column(columnDefinition = "TEXT")
    private String dadosAnterioresJson; // O JSON com o estado anterior

    @PrePersist
    protected void onCreate() {
        if (publicId == null) {
            publicId = UUID.randomUUID();
        } // Getters, Setters, Construtores...
    }
}

