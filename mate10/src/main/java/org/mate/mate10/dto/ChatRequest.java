package org.mate.mate10.dto;

import lombok.Data;

@Data
public class ChatRequest {
    private Long userId;
    private Long chatId;
    private String message;
}
