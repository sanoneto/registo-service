package com.aneto.registo_horas_service.mapper;

import com.aneto.registo_horas_service.dto.request.EventRequest;
import com.aneto.registo_horas_service.dto.response.EventsResponse;
import com.aneto.registo_horas_service.models.Evento;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;
import org.mapstruct.factory.Mappers;

@Mapper(componentModel = "spring") // Define como um Bean do Spring
public interface EventsMapper {

    EventsMapper INSTANCE = Mappers.getMapper(EventsMapper.class);

    // Converte o Request (vinda do usuário) para a Entidade (banco)
    //@Mapping(target = "id", ignore = true)
    Evento toEntity(EventRequest eventRequest);

    // Converte a Entidade (banco) para o Response (saída)
    // Note que os campos ausentes no DTO serão ignorados automaticamente
    EventsResponse toResponse(Evento evento);

    // NOVO: Atualiza a instância existente com os dados do request
    void updateEntityFromDto(EventRequest dto, @MappingTarget Evento entity);
}
