package org.mate.mate10.controller;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import org.mate.mate10.common.CurrentUserProvider;
import org.springframework.beans.factory.annotation.Value;
import lombok.extern.slf4j.Slf4j;
import org.mate.mate10.common.Result;
import org.mate.mate10.config.ChatContextBuilder;
import org.mate.mate10.config.ChatModelFactory;
import org.mate.mate10.dto.ChatRequest;
import org.mate.mate10.entity.Chat;
import org.mate.mate10.service.ChatRecordService;
import org.mate.mate10.service.ChatService;
import org.mate.mate10.service.MongoChatMessageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@RestController
public class ChatController {

    @Autowired
    private ChatModelFactory chatModelFactory;
    @Autowired
    private ChatService chatService;
    @Autowired
    private MongoChatMessageService mongoChatMessageService;
    @Autowired
    private ChatRecordService recordService;
    @Autowired
    private ThreadPoolTaskExecutor streamExecutor;
    @Autowired
    private ChatContextBuilder chatContextBuilder;
    @Autowired
    private CurrentUserProvider  currentUser;

    @Value("${app.error.detail:false}")
    private boolean errorDetail;


    @PostMapping("/ai/chat")
    public Result<Chat> createChat(@RequestBody Chat chat) {
        //会话归属一律登陆态,忽略body里的userId
        Long userId = currentUser.userIdOrNull();
        if (userId == null) {
            return Result.error(401,"未登录");
        }
        return Result.success(chatService.createChat(userId, chat.getTitle()));
    }

    @PostMapping("/ai/chatRecord")
    public Result<String> chat(@RequestBody ChatRequest request) {
        log.info("[chatRecord] 收到 chatId={} userId={} mode={} message={}",
                request.getChatId(), request.getUserId(), request.getMode(), request.getMessage());
        String message = request.getMessage();
        String mode = request.getMode();
        Long chatId = request.getChatId();

        Long userId = currentUser.userIdOrNull();
        if (userId == null) {
            return Result.error(401,"未登录");
        }
         if (chatId != null && !canAccessChat(chatId, userId)) {
             return Result.error(403,"无权访问会话");
         }
        // 组装上下文
        List<ChatMessage> messages = chatContextBuilder.build(chatId, message, mode);

        if (userId != null && chatId != null) {
            mongoChatMessageService.saveUserMessage(chatId, userId, message);
        }

        ChatResponse response = chatModelFactory.getChatModel().chat(messages);
        String answer = response.aiMessage().text();

        if (userId != null && chatId != null) {
            mongoChatMessageService.saveAiMessage(chatId, userId, answer);
            recordService.sendChatRecordToKafka(chatId, userId, message, answer);
        }

        return Result.success(answer);
    }

    //前端读流
    @PostMapping(value = "/ai/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chatStream(@RequestBody ChatRequest request) {
        log.info("[chatStream] 收到 chatId={} userId={} mode={} message={}",
                request.getChatId(), request.getUserId(), request.getMode(), request.getMessage());
        //5分钟  不设置时间,连接会一直保持,一直占用资源
        SseEmitter emitter = new SseEmitter(5 * 60 * 1000L);
        String message = request.getMessage();
        String mode = request.getMode();
        Long chatId = request.getChatId();
        Long userId = currentUser.userIdOrNull();

        //保证 done 只发一次,多条终态路径可能先后触发
        AtomicBoolean finished = new AtomicBoolean(false);

        //流式接口的错误也走SSE约定(发error + done),不返回HTTP 401/403,方法签名也是SseEmitter,而且前端本来就在处理这两个事件,emitter返回前的send会被Spring缓冲,不会丢
        if (userId == null) {
            finish(emitter, finished,"未登录,请重新登录");
            return emitter;
        }
        if (chatId != null && !canAccessChat(chatId, userId)) {
            finish(emitter,finished,"无权访问会话");
            return emitter;
        }

        //超时也要给前端一个结束信号,否则界面一直转圈
        emitter.onTimeout(() -> {
            log.warn("SSE超时,chatId={}", chatId);
            finish(emitter, finished, "响应超时，请重试");
        });

        //放进独立线程执行,方法返回 emitter
        streamExecutor.execute(() -> {
            StringBuilder full = new StringBuilder();
            try {
                //用户消息,再try内抛异常会变成HTTP 500(不走SSE收尾)
                if (chatId != null) {
                    mongoChatMessageService.saveUserMessage(chatId, userId, message);
                }
                List<ChatMessage> messages = chatContextBuilder.build(chatId, message, mode);
                chatModelFactory.getStreamingChatModel().chat(messages,
                        new StreamingChatResponseHandler() {

                            @Override
                            public void onPartialResponse(String token) {
                                full.append(token);
                                try {
                                    emitter.send(SseEmitter.event().data(token));
                                } catch (Exception e) {
                                    //catch Exception:除 IOException 外,send 还会抛
                                    //IllegalStateException(emitter 已完成/已超时)
                                    log.warn("推送 token 失败(前端可能已断开): {}", e.getMessage());
                                    finish(emitter, finished, null);
                                }
                            }

                            @Override
                            public void onCompleteResponse(ChatResponse completeResponse) {
                                String answer = full.toString();
                                try {
                                    if (userId != null && chatId != null) {
                                        mongoChatMessageService.saveAiMessage(chatId, userId, answer);
                                        recordService.sendChatRecordToKafka(chatId, userId, message, answer);
                                    }
                                } catch (Exception e) {
                                    //保存/Kafka 失败不应该影响用户拿到回答
                                    log.error("保存回答失败,chatId={}", chatId, e);
                                } finally {
                                    finish(emitter, finished, null);
                                }
                            }

                            @Override
                            public void onError(Throwable error) {
                                log.error("流式生成失败,chatId={}", chatId, error);
                                //失败路径一样收尾,防止前端一直卡[思考中]
                                finish(emitter, finished, errorDetail
                                        ? "模型调用失败：" + error.getMessage()
                                        : "模型调用失败，请稍后重试");
                            }
                        });
            } catch (Exception e) {
                log.error("流式端点异常,chatId={}", chatId, e);
                finish(emitter, finished, errorDetail
                        ? "服务异常：" + e.getMessage()
                        : "服务异常，请稍后重试");
            }
        });

        return emitter; //保持连接
    }

    //SSE 统一收尾,正常完成,模型出错,端点异常,超时,前端断开--都走这里
    //模型契约在AGENT.md[流式约定]
    //前端不能依赖[连接关闭]判断流结束(经Vite代理时关闭信号会丢),只认done事件
    //少发一次,界面留在[思考中],用 AtomicBoolean + CAS 保证 done 只发一次
    private void finish(SseEmitter emitter, AtomicBoolean finished, String errorMsg) {
        if (!finished.compareAndSet(false, true)) {
            return;                          //已经收尾过,直接返回
        }
        log.info("[SSE] finish 收尾 -> {}", errorMsg == null ? "正常结束" : ("异常: " + errorMsg));
        if (errorMsg != null) {
            try {
                emitter.send(SseEmitter.event().name("error").data(errorMsg));
            } catch (Exception ignored) {
                //前端已断开 —— error 发不出去无所谓,下面还有 done
            }
        }
        try {
            emitter.send(SseEmitter.event().name("done").data("[DONE]"));   //★done
        } catch (Exception ignored) {
            //前端已断开 / emitter 已结束
        }
        try {
            emitter.complete();
        } catch (Exception ignored) {
        }
    }
    private boolean canAccessChat(Long chatId, Long me) {
        if (currentUser.isAdmin()) {
            return true;
        }
        Chat chat = chatService.getById(chatId);
        return chat != null && me.equals(chat.getUserId());
    }
}