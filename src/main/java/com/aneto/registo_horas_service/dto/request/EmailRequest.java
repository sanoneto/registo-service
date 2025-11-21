package com.aneto.registo_horas_service.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.io.Serializable;


public record EmailRequest(
        @NotBlank
        @Size(max = 100)
        String name,

        @NotBlank
        @Email
        @Size(max = 200)
        String email,

        @NotBlank
        @Size(max = 2000)
        String message
) implements Serializable { // Adicione 'implements Serializable'
}
