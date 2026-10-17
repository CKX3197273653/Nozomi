package org.mate.mate10.service;

import com.baomidou.mybatisplus.extension.service.IService;
import org.mate.mate10.entity.Chat;

import java.util.List;
import java.util.Map;

public interface ChatService extends IService<Chat> {
    Chat createChat(Long userId, String title);
}
