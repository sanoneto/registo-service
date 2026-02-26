package com.aneto.registo_horas_service.mapper;

import com.aneto.registo_horas_service.dto.request.PlanoRequestDTO;
import com.aneto.registo_horas_service.dto.response.PlanoResponseDTO;
import com.aneto.registo_horas_service.models.Plano;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import java.util.UUID;

@Mapper(componentModel = "spring")
public interface PlanoMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "dataCreate", ignore = true)
    @Mapping(target = "dataUpdate", ignore = true)
    Plano toEntity(PlanoRequestDTO requestDTO);

    // Mapeamentos explícitos para garantir que o Jackson receba Strings puras
    @Mapping(target = "id", source = "id", qualifiedByName = "uuidToString")
    @Mapping(target = "estadoPlano", source = "estadoPlano", qualifiedByName = "enumToString")
    @Mapping(target = "estadoPedido", source = "estadoPedido", qualifiedByName = "enumToString")
    @Mapping(target = "dataCreate", source = "dataCreate", qualifiedByName = "dateToString")
    @Mapping(target = "dataUpdate", source = "dataUpdate", qualifiedByName = "dateToString")
    PlanoResponseDTO toResponse(Plano plano);

    @Named("uuidToString")
    default String uuidToString(UUID id) {
        return id != null ? id.toString() : null;
    }

    @Named("enumToString")
    default String enumToString(java.lang.Enum<?> enumeration) {
        return enumeration != null ? enumeration.name() : null;
    }

    @Named("dateToString")
    default String dateToString(java.time.LocalDate date) {
        return date != null ? date.toString() : null;
    }
}