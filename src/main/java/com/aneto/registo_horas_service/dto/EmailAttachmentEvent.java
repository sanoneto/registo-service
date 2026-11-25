package com.aneto.registo_horas_service.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmailAttachmentEvent implements Serializable {
    private String to;
    private String subject;
    private String body;
    private String attachmentName;
    private String attachmentBase64;
    private String contentType;
}