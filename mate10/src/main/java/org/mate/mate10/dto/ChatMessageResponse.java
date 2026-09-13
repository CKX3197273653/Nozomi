package org.mate.mate10.dto;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class ChatMessageResponse {
    private String id;
    private Long userId;
    private Long chatId;
    private String role;
    private String content;
    private LocalDateTime createTime;
}
