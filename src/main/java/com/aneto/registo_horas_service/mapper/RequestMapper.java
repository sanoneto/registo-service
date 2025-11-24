package com.aneto.registo_horas_service.mapper;


import com.aneto.registo_horas_service.dto.request.RegisterRequest;
import com.aneto.registo_horas_service.dto.response.RegisterResponse;
import com.aneto.registo_horas_service.models.RegistosHoras;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface RequestMapper {


    @Mapping(target = "id", ignore = true)
    @Mapping(target = "publicId", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "userName", source = "userName")
    @Mapping(target = "descricao", source = "descricao")
    @Mapping(target = "dataRegisto", source = "dataRegisto")
    @Mapping(target = "horaEntrada", source = "horaEntrada")
    @Mapping(target = "horaSaida", source = "horaSaida")
    @Mapping(target = "horasTrabalhadas", source = "horasTrabalhadas")
    RegistosHoras mapToRegisterHoras(RegisterRequest registerRequest);

    RegisterResponse toResponse(RegistosHoras registerHoras);


    @Mapping(target = "List<RegisterHoras> ", source = "List<RegisterResponse> ")
    List<RegisterResponse> mapToListRegisterResponse(List<RegistosHoras> registerHoras);
}
