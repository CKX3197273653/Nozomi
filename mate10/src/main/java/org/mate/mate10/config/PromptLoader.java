package org.mate.mate10.config;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

@Component
public class PromptLoader {
    private final Map<String, String> cache = new HashMap<>();

    public String get(String name) {
        return cache.computeIfAbsent(name, n -> {
            try (InputStream in = getClass().getResourceAsStream("/prompts/" + n + ".txt")) {
                if (in == null) throw new IllegalStateException("Prompt 不存在: " + n);
                return new String(in.readAllBytes(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new RuntimeException("加载 Prompt 失败: " + n, e);
            }
        });
    }
}
