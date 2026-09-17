package org.mate.mate10.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "app.chat")
public class ChatProperties {
    //历史消息条数上限
    private int maxHistoryMessages = 10;
    //历史消息字符预算
    private int maxHistoryChars = 2000;
    //系统提示词
    private String systemPrompt = "你是质检知识助手,回答简介、准确";
}
