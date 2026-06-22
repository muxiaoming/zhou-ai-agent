package com.zhou.zhouaiagent.controller;

import com.zhou.zhouaiagent.agent.ZhouManus;
import com.zhou.zhouaiagent.app.LoveApp;
import com.zhou.zhouaiagent.config.otel.OtelContextUtils;
import com.zhou.zhouaiagent.mcp.DynamicToolCallbackProvider;
import com.zhou.zhouaiagent.rag.AgenticRagService;
import jakarta.annotation.Resource;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/ai")
public class AiController {

    @Resource
    private LoveApp loveApp;

    @Resource
    private DynamicToolCallbackProvider dynamicToolCallbackProvider;

    @Resource
    private ChatModel dashscopeChatModel;

    @Resource
    private AgenticRagService agenticRagService;

    /**
     * 同步调用 AI 恋爱大师应用
     *
     * @param message
     * @param chatId
     * @return
     */
    @GetMapping("/love_app/chat/sync")
    public String doChatWithLoveAppSync(String message, String chatId) {
        return loveApp.doChat(message, chatId);
    }

    /**
     * Agentic RAG 流式查询（SseEmitter 方式）
     * LLM 驱动路由 + 混合检索 + 多轮反思
     *
     * @param message 用户问题
     * @param chatId  会话 ID
     * @return SseEmitter 流式响应
     */
    @GetMapping("/love_app/rag")
    public SseEmitter doChatWithAgenticRag(String message, String chatId) {
        SseEmitter sseEmitter = new SseEmitter(180000L);
        // 捕获当前 OTel Context（HTTP 请求线程的 Trace），传递到异步线程
        Context otelContext = Context.current();
        // 使用 OtelContextUtils.commonPool() 避免占用默认 ForkJoinPool 导致线程饥饿
        CompletableFuture.runAsync(() -> {
            try (Scope ignored = otelContext.makeCurrent()) {
                // 包裹整个 RAG 流程为根 Span，chatId 内所有 query 共享同一 TraceId
                try {
                    OtelContextUtils.withSpan("rag.chat." + (chatId != null ? chatId : "anonymous"), () -> {
                        try {
                            String answer = agenticRagService.query(message, chatId);
                            sseEmitter.send(answer);
                            sseEmitter.complete();
                        } catch (Exception e) {
                            sseEmitter.completeWithError(e);
                        }
                    });
                } catch (Exception e) {
                    // withSpan 本身失败（如 tracer 异常），确保 SseEmitter 仍然被关闭
                    sseEmitter.completeWithError(e);
                }
            }
        }, OtelContextUtils.commonPool());
        return sseEmitter;
    }

    /**
     * SSE 流式调用 AI 恋爱大师应用
     *
     * @param message
     * @param chatId
     * @return
     */
    @GetMapping(value = "/love_app/chat/sse", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> doChatWithLoveAppSSE(String message, String chatId) {
        return loveApp.doChatByStream(message, chatId);
    }

    /**
     * SSE 流式调用 AI 恋爱大师应用
     *
     * @param message
     * @param chatId
     * @return
     */
    @GetMapping(value = "/love_app/chat/server_sent_event")
    public Flux<ServerSentEvent<String>> doChatWithLoveAppServerSentEvent(String message, String chatId) {
        return loveApp.doChatByStream(message, chatId)
                .map(chunk -> ServerSentEvent.<String>builder()
                        .data(chunk)
                        .build());
    }

    /**
     * SSE 流式调用 AI 恋爱大师应用
     *
     * @param message
     * @param chatId
     * @return
     */
    @GetMapping(value = "/love_app/chat/sse_emitter")
    public SseEmitter doChatWithLoveAppServerSseEmitter(String message, String chatId) {
        // 创建一个超时时间较长的 SseEmitter
        SseEmitter sseEmitter = new SseEmitter(180000L); // 3 分钟超时
        // 获取 Flux 响应式数据流并且直接通过订阅推送给 SseEmitter
        loveApp.doChatByStream(message, chatId)
                .subscribe(chunk -> {
                    try {
                        sseEmitter.send(chunk);
                    } catch (IOException e) {
                        sseEmitter.completeWithError(e);
                    }
                }, sseEmitter::completeWithError, sseEmitter::complete);
        // 返回
        return sseEmitter;
    }

    /**
     * 流式调用 Manus 超级智能体（Flux 方式 - 推荐）
     * 使用 Flux<String> 返回，线程安全，支持响应式背压
     *
     * @param message 用户消息
     * @return Flux 流式结果
     */
    @GetMapping(value = "/manus/chat/flux", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> doChatWithManusFlux(String message) {
        ZhouManus zhouManus = new ZhouManus(dynamicToolCallbackProvider.getToolCallbacks(), dashscopeChatModel);
        return zhouManus.runStreamFlux(message);
    }

    /**
     * 流式调用 Manus 超级智能体（SseEmitter 方式 - 兼容旧前端）
     *
     * @param message 用户消息
     * @return SseEmitter
     */
    @GetMapping("/manus/chat")
    public SseEmitter doChatWithManus(String message) {
        ZhouManus zhouManus = new ZhouManus(dynamicToolCallbackProvider.getToolCallbacks(), dashscopeChatModel);
        return zhouManus.runStream(message);
    }
}
