package com.aneto.registo_horas_service.mapper;

import com.aneto.registo_horas_service.dto.response.RegistoTreinoDTO;
import com.aneto.registo_horas_service.models.Training.RegistoTreino;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface RegistoTreinoMapper {

    // Converte DTO (entrada) para Entidade (banco)
    @Mapping(target = "id", ignore = true)
    // --- CORREÇÃO AQUI ---
    // RegistoTreino não tem o campo 'registosTreino'.
    // Devemos ignorar o 'planoPagamento' se não estiver a passá-lo manualmente.
    @Mapping(target = "planoPagamento", ignore = true)
    RegistoTreino toRegistoTreino(RegistoTreinoDTO registoTreinoDTO);

    // Converte a Entidade (banco) para o DTO (saída)
    @Mapping(target = "packValor", source = "packValor")
    RegistoTreinoDTO toRegistoTreinoDTO(RegistoTreino registoTreino);
}