package com.aneto.registo_horas_service.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class PushSubscriptionDTO {
    private String endpoint;
    private Long expirationTime;
    private Keys keys;

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Keys {
        private String p256dh;
        private String auth;
    }
}