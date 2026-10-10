package com.aneto.registo_horas_service.models;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

/**
 * Conteúdo JSON do plano. Tabela separada de "planos" para que as listagens
 * de planos não carreguem o JSON (cerca de 9 KB por plano) de cada vez.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "plano_dados", schema = "registos")
public class PlanoDados {

    @Id
    @Column(name = "link", length = 500)
    private String link;

    // JsonNode (e não String): com String o Hibernate 6 guardaria um texto JSON escapado.
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "dados", columnDefinition = "jsonb", nullable = false)
    private JsonNode dados;

    @UpdateTimestamp
    @Column(name = "atualizado_em")
    private Instant atualizadoEm;
}