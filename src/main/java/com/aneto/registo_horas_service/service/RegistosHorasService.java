// language: java
package com.aneto.registo_horas_service.service;


import com.aneto.registo_horas_service.dto.request.RegisterRequest;
import com.aneto.registo_horas_service.dto.response.RegisterResponse;

import java.util.List;
import java.util.UUID;

public interface RegistosHorasService {

    // Cria/Atualiza um registro (calcula as horas)
    RegisterResponse submeterHoras(RegisterRequest request, String username);

    // Retorna todos os registros (somente ADMIN)
    List<RegisterResponse> findAllRegisteredHours();

    // Retorna registros por usuário
    List<RegisterResponse> buscarRegistrosPorUsuario(String username);

    // Atualiza um registro existente
    RegisterResponse atualizarRegistro(UUID publicId, RegisterRequest request);

    // Deleta um registro
    void deleteRegistry(UUID publicId);

    // Retorna o total de horas
    double getTotalHorasPorUsuario(String username);
}