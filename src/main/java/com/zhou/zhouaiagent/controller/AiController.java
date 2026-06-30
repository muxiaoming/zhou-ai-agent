package com.zhou.zhouaiagent.controller;

import cn.hutool.core.io.IoUtil;
import com.zhou.zhouaiagent.agent.ZhouManus;
import com.zhou.zhouaiagent.app.LoveApp;
import com.zhou.zhouaiagent.config.otel.OtelContextUtils;
import com.zhou.zhouaiagent.mcp.DynamicToolCallbackProvider;
import com.zhou.zhouaiagent.memory.JdbcChatMemoryRepository;
import com.zhou.zhouaiagent.rag.AgenticRagService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

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

    @Resource
    private JdbcTemplate jdbcTemplate;

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

    /**
     * 获取示例对话（用于页面展示）
     * 返回指定 conversation_id 的所有消息，供前端展示示例效果
     *
     * @return 消息列表，包含 role 和 content
     */
    @GetMapping("/love_app/demo")
    public List<Map<String, String>> getDemoConversation() {
        JdbcChatMemoryRepository repository = new JdbcChatMemoryRepository(jdbcTemplate);
        List<Message> messages = repository.findByConversationId("799175d8-8f34-40ff-9e7a-7930da9ae7a9");

        return messages.stream()
                .map(msg -> Map.of(
                        "role", msg.getMessageType().getValue().toLowerCase(),
                        "content", msg.getText() != null ? msg.getText() : ""
                ))
                .collect(Collectors.toList());
    }

    /**
     * 获取 Manus 智能体的示例对话
     *
     * @return 消息列表，包含 role 和 content
     */
    @GetMapping("/manus/demo")
    public List<Map<String, String>> getManusDemoConversation() {
        String assistantContent = "好的！我来帮你制定一份上海静安区的约会计划。\n\n" +
                "🔍 **Step 1**: 获取静安区地理位置信息...\n\n" +
                "```json\n" +
                "{\n" +
                "\"return\": [{\n" +
                "\"country\": \"中国\",\n" +
                "\"province\": \"上海市\",\n" +
                "\"city\": \"上海市\",\n" +
                "\"district\": \"静安区\",\n" +
                "\"location\": \"121.447348,31.227718\",\n" +
                "\"level\": \"区县\"\n" +
                "}]\n" +
                "}\n" +
                "```\n\n" +
                "📍 **Step 2**: 搜索静安区5公里内的约会地点...\n\n" +
                "**📍 搜索到的地点：**\n\n" +
                "**1. 毛头老爹饭店(静安寺店)**\n" +
                "   📍 愚园路246弄6号\n\n" +
                "**2. 曜·铁板OMAKASE(JULU758店)**\n" +
                "   📍 巨鹿路758号5号楼2层\n\n" +
                "**3. Mammamia(巨鹿路店)**\n" +
                "   📍 巨鹿路758号1号楼1层\n\n" +
                "**4. 佰映三克映画(MOHO Mall店)**\n" +
                "   📍 江宁路699号B1-60B\n\n" +
                "**5. 鮨瞬Omakase(南京西路店)**\n" +
                "   📍 吴江路269号湟潽汇商场2楼\n\n" +
                "...还有更多地点\n\n" +
                "🖼️ **Step 3**: 搜索相关图片和评价...\n\n" +
                "**🔍 搜索结果：**\n\n" +
                "**1. [佰映三克映画影城 - 百度百科](https://baike.baidu.com)**\n" +
                "   📎 百度百科\n" +
                "   佰映三克映画影城（上海静安MOHO店）于2023年4月底开业，是全国首家可边吃正餐边观影的影院...\n\n" +
                "**2. [上海5家必试的Omakase日料店](https://www.xiaohongshu.com)**\n" +
                "   📎 绿叶小宁\n" +
                "   🖼️ [查看图片](https://img1.baidu.com/it/u=xxx)\n" +
                "   地址:黄浦区南京路300号恒基名人购物中心五楼516...\n\n" +
                "📝 **Step 4**: 生成约会计划PDF...\n\n" +
                "✅ PDF 已生成\n\n" +
                "📥 **下载文件：** [点击下载 PDF]\n\n" +
                "约会计划已完成！PDF中包含详细的地点介绍、交通路线和时间安排。";

        return List.of(
                Map.of(
                        "role", "user",
                        "content", "我的另一半居住在上海静安区，请帮我找到 5 公里内合适的约会地点，并结合一些网络图片，制定一份详细的约会计划，并以 PDF 格式输出"
                ),
                Map.of(
                        "role", "assistant",
                        "content", assistantContent
                )
        );
    }

    /**
     * 获取用户历史对话
     * 返回指定 chatId 的所有消息，用于页面刷新后恢复对话
     *
     * @param chatId 会话 ID
     * @return 消息列表，包含 role 和 content
     */
    @GetMapping("/love_app/history")
    public List<Map<String, String>> getConversationHistory(String chatId) {
        if (chatId == null || chatId.isBlank()) {
            return List.of();
        }
        JdbcChatMemoryRepository repository = new JdbcChatMemoryRepository(jdbcTemplate);
        List<Message> messages = repository.findByConversationId(chatId);

        return messages.stream()
                .filter(msg -> !"system".equalsIgnoreCase(msg.getMessageType().getValue()))
                .map(msg -> Map.of(
                        "role", msg.getMessageType().getValue().toLowerCase(),
                        "content", msg.getText() != null ? msg.getText() : ""
                ))
                .collect(Collectors.toList());
    }

    /**
     * 文件下载接口
     * 支持 PDF、图片等文件下载
     *
     * @param file 文件名（在 tmp/pdf 目录下）
     * @param response HTTP 响应
     */
    @GetMapping({"/download"})
    public void downloadFile(@RequestParam String file, HttpServletResponse response) {
        try {
            // 在 tmp/pdf 目录下查找文件
            String pdfDir = System.getProperty("user.dir") + "/tmp/pdf";
            File targetFile = new File(pdfDir, file);

            if (!targetFile.exists()) {
                response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                response.getWriter().write("文件不存在: " + file);
                return;
            }

            // 设置响应头
            response.setContentType("application/pdf");
            String fileName = URLEncoder.encode(targetFile.getName(), StandardCharsets.UTF_8);
            response.setHeader("Content-Disposition",
                "attachment; filename*=UTF-8''" + fileName);
            response.setContentLengthLong(targetFile.length());

            // 流式输出文件
            try (FileInputStream in = new FileInputStream(targetFile)) {
                IoUtil.copy(in, response.getOutputStream());
            }
        } catch (Exception e) {
            try {
                response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                response.getWriter().write("下载失败：" + e.getMessage());
            } catch (IOException ex) {
                // 忽略
            }
        }
    }
}
