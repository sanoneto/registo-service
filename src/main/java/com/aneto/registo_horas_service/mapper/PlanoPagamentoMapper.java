package com.aneto.registo_horas_service.mapper;

import com.aneto.registo_horas_service.dto.response.PlanoPagamentoDTO;
import com.aneto.registo_horas_service.models.Training.PlanoPagamento;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

// 1. Defina como componente Spring para usar injeção de dependência (@Autowired)
@Mapper(componentModel = "spring")
public interface PlanoPagamentoMapper { // 2. Mude de 'class' para 'interface'

    // 3. Remova a instância manual INSTANCE se estiver usando componentModel = "spring"

    // Mapeia da Entidade para o DTO
    PlanoPagamentoDTO toPlanoPagamentoDTO(PlanoPagamento planoPagamento);

    // Mapeia do DTO para a Entidade
    PlanoPagamento toPlanoPagamento(PlanoPagamentoDTO planoPagamentoDTO);

    // Método para atualizar uma entidade existente com dados de um DTO (útil para PUT)
    @Mapping(target = "id", ignore = true)
    // Não atualizar o ID
    void updatePlanoFromDto(PlanoPagamentoDTO dto, @MappingTarget PlanoPagamento entity);
}