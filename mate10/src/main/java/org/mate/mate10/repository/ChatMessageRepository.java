package org.mate.mate10.repository;

import org.mate.mate10.document.ChatMessageDocument;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ChatMessageRepository extends MongoRepository<ChatMessageDocument,String> {
    List<ChatMessageDocument>findByUserId(Long userId);
    List<ChatMessageDocument>findByChatId(Long chatId);
    List<ChatMessageDocument>findByChatIdAndRole(Long chatId,String role);
}
