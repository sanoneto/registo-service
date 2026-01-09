package com.aneto.registo_horas_service.dto.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class PushSubscriptionDTO {
    private String endpoint;
    private Keys keys;

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Keys {
        private String p256dh;
        private String auth;
    }
}