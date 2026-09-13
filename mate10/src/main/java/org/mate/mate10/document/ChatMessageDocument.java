package org.mate.mate10.document;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.LocalDateTime;

@Data
@Document(collection = "chat_messages")
public class ChatMessageDocument {

    @Id
    private String id;
    @Field("user_id")
    private Long userId;
    @Field("chat_id")
    private Long chatId;
    @Field("role")
    private String role;
    @Field("content")
    private String content;
    @Field("create_time")
    private LocalDateTime createTime;
}
