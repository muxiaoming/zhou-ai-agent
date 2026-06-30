package com.zhou.zhouaiagent.agent;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import com.zhou.zhouaiagent.agent.model.AgentState;
import io.micrometer.observation.annotation.Observed;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.ai.tool.ToolCallback;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 处理工具调用的基础代理类，具体实现了 think 和 act 方法，可以用作创建实例的父类
 */
@EqualsAndHashCode(callSuper = true)
@Data
@Slf4j
public class ToolCallAgent extends ReActAgent {

    // 可用的工具
    private final ToolCallback[] availableTools;

    // 保存工具调用信息的响应结果（要调用那些工具）
    private ChatResponse toolCallChatResponse;

    // 本次思考的模型回复文本（供 step() 输出）
    private String lastThinkResult;

    // 工具调用管理者
    private final ToolCallingManager toolCallingManager;

    // 禁用 Spring AI 内置的工具调用机制，自己维护选项和消息上下文
    private final ChatOptions chatOptions;

    public ToolCallAgent(ToolCallback[] availableTools) {
        super();
        this.availableTools = availableTools;
        this.toolCallingManager = ToolCallingManager.builder().build();
        // 禁用 Spring AI 内置的工具调用机制，自己维护选项和消息上下文
        this.chatOptions = DashScopeChatOptions.builder()
                .withInternalToolExecutionEnabled(false)
                .build();
    }

    /**
     * 处理当前状态并决定下一步行动
     *  ToolCallAgent 中的 think() 方法 是调用大模型, 判断是否需要调用工具
     *  把原本在 LoveApp#doChatWithTools()中由大模型判断是否调用工具,
     *  并且由SpringAi框架自动调用工具(toolCallbacks(allTools))的链式步骤拆开分为两个部分
     *  toolCallbacks(allTools)方法依然需要调用, 作用是让大模型知道有哪些工具可调用, 在 ChatOptions 中禁用内置工具调用
     *  1. think(): 调用大模型, 大模型判断是否需要调用工具
     *  2. act(): 执行tool调用(大模型判断需要调用的工具) 把原本由框架自动调用的工具 toolCallbacks(allTools)
     *             DashScopeChatOptions.builder()
     *                 .withInternalToolExecutionEnabled(false)
     *                 .build()禁用 Spring AI 内置的工具调用机制后手动调用工具 toolCallingManager.executeToolCalls(prompt, toolCallChatResponse)
     *
     * @return 是否需要执行行动，true表示需要执行，false表示不需要执行
     */
    @Override
    @Observed(name = "agent.think", contextualName = "Agent thinking step")
    public boolean think() {
        // 1、校验提示词，拼接用户提示词
        if (StrUtil.isNotBlank(getNextStepPrompt())) {
            UserMessage userMessage = new UserMessage(getNextStepPrompt());
            getMessageList().add(userMessage);
        }
        // 2、调用 AI 大模型，获取工具调用结果
        List<Message> messageList = getMessageList();
        // 用 Spring AI 内置的工具调用机制, 工具调用于act()中手动控制执行
        Prompt prompt = new Prompt(messageList, this.chatOptions);
        try {
            ChatResponse chatResponse = getChatClient().prompt(prompt)
                    .system(getSystemPrompt())
                    // 禁用 Spring AI 内置的工具调用机制，自己维护选项和消息上下文
                    // private final ChatOptions chatOptions;
                    // 工具调用已禁用, 传入 availableTools 的作用是让大模型知道有哪些工具可调用
                    .toolCallbacks(availableTools)
                    .call()
                    .chatResponse();
            // 记录响应，用于等下 Act
            this.toolCallChatResponse = chatResponse;
            // 3、解析工具调用结果，获取要调用的工具
            // 助手消息
            AssistantMessage assistantMessage = chatResponse.getResult().getOutput();
            // 获取要调用的工具列表
            List<AssistantMessage.ToolCall> toolCallList = assistantMessage.getToolCalls();
            // 输出提示信息
            this.lastThinkResult = assistantMessage.getText();

            log.info("{}的思考：{}", getName(), assistantMessage.getText());
            log.info("{}选择了 {} 个工具来使用", getName(), toolCallList.size());

            if (toolCallList.isEmpty()) {
                // 只有不调用工具时，才需要手动记录助手消息
                // 模型直接回答，无需工具 → 任务完成
                getMessageList().add(assistantMessage);
                return false;
            } else {
                // 需要调用工具时，无需记录助手消息，因为调用工具时会自动记录
                // 需要调用工具（助手消息会在 act() 中由框架自动记录）
                toolCallList.forEach(tc -> log.info("工具名称：{}，参数：{}", tc.name(), tc.arguments()));
                return true;
            }
        } catch (Exception e) {
            log.error("{}的思考过程遇到了问题：{}", getName(), e.getMessage(), e);
            getMessageList().add(new AssistantMessage("处理时遇到了错误：" + e.getMessage()));
            return false;
        }
    }

    /**
     * 执行工具调用并处理结果
     *
     * @return 执行结果
     */
    @Override
    @Observed(name = "agent.act", contextualName = "Agent action step")
    public String act() {
        if (!toolCallChatResponse.hasToolCalls()) {
            return "没有工具需要调用";
        }
        // 调用工具
        Prompt prompt = new Prompt(getMessageList(), this.chatOptions);
        ToolExecutionResult toolExecutionResult = toolCallingManager.executeToolCalls(prompt, toolCallChatResponse);
        // 记录消息上下文，conversationHistory 已经包含了助手消息和工具调用返回的结果
        setMessageList(toolExecutionResult.conversationHistory());
        // executeToolCalls 已经执行了工具调用, 最后一条消息是工具调用的消息
        ToolResponseMessage toolResponseMessage = (ToolResponseMessage) CollUtil.getLast(toolExecutionResult.conversationHistory());
        // 判断是否调用了终止工具
        boolean terminateToolCalled = toolResponseMessage.getResponses().stream()
                .anyMatch(response -> response.name().equals("doTerminate"));
        if (terminateToolCalled) {
            // 任务结束，更改状态
            setState(AgentState.FINISHED);
            return "";
        }
        // 格式化每个工具的执行结果
        StringBuilder results = new StringBuilder();
        for (var response : toolResponseMessage.getResponses()) {
            String toolName = response.name();
            String responseData = response.responseData();
            if (responseData == null || responseData.isBlank() || toolName.equals("doTerminate")) continue;

            String formatted = formatToolResult(toolName, responseData);
            if (!formatted.isBlank()) {
                results.append(formatted).append("\n");
            }
        }
        return results.toString().trim();
    }

    /**
     * 格式化工具执行结果，使其更易读
     */
    private String formatToolResult(String toolName, String result) {
        String baseUrl = "http://localhost:8123/api";

        // 地图搜索 - 格式化 POI 结果（限制 10 个）
        if (toolName.contains("amap") && (toolName.contains("search") || toolName.contains("around"))) {
            return formatMapSearchResult(result, 10);
        }

        // 图片搜索 - 工具已自己格式化
        if (toolName.contains("searchImage")) {
            return result + "\n";
        }

        // PDF 生成 - 显示可下载链接
        if (toolName.contains("generatePDF")) {
            return formatPdfResult(result, baseUrl);
        }

        // 网页搜索 - 工具已自己格式化，确保格式正确
        if (toolName.contains("searchWeb") || toolName.contains("scrapeWebPage")) {
            // 确保结果末尾有换行符
            String formattedResult = result;
            if (!formattedResult.endsWith("\n")) {
                formattedResult = formattedResult + "\n";
            }
            return formattedResult;
        }

        // 文件操作
        if (toolName.contains("readFile") || toolName.contains("writeFile")) {
            return "✅ 文件操作完成\n";
        }

        // 终端命令 - 显示执行完成状态和输出
        if (toolName.contains("executeTerminalCommand")) {
            String truncated = result.length() > 300 ? result.substring(0, 300) + "..." : result;
            return "✅ 命令执行完成\n\n```\n" + truncated + "\n```\n";
        }

        // 下载资源
        if (toolName.contains("downloadResource")) {
            return "✅ 文件下载完成\n";
        }

        // 其他情况，美化 JSON 输出
        return beautifyJson(result);
    }

    /**
     * 格式化 PDF 生成结果
     */
    private String formatPdfResult(String result, String baseUrl) {
        // 提取路径，从 "to: " 后面获取
        String path = result.replaceAll(".*to: ", "").trim();
        path = path.replaceAll("[\"']", "");  // 去掉引号

        // 提取文件名（从完整路径中提取）
        String fileName = path.substring(path.lastIndexOf("/") + 1);

        // 创建下载 URL
        String downloadUrl = baseUrl + "/download?file=" + fileName;

        return String.format(
            "✅ PDF 已生成\n\n" +
            "📥 **下载文件：** [点击下载 PDF](%s)",
            downloadUrl
        );
    }

    /**
     * 格式化地图搜索结果（POI），限制返回数量
     */
    private String formatMapSearchResult(String result, int maxResults) {
        try {
            StringBuilder sb = new StringBuilder("**📍 搜索到的地点：**\n\n");
            String[] lines = result.split("\n");
            String currentName = null;
            String currentAddress = null;
            String currentLng = null;
            String currentLat = null;
            int count = 0;

            for (String line : lines) {
                if (count >= maxResults) break;

                if (line.contains("\"name\"")) {
                    currentName = extractJsonValue(line);
                } else if (line.contains("\"address\"")) {
                    currentAddress = extractJsonValue(line);
                } else if (line.contains("\"location\"")) {
                    // 提取坐标信息（格式：lng,lat）
                    String location = extractJsonValue(line);
                    if (location != null && location.contains(",")) {
                        String[] coords = location.split(",");
                        currentLng = coords[0].trim();
                        currentLat = coords[1].trim();
                    }
                }

                // 当收集到名称和地址时，输出一条结果
                if (currentName != null && currentAddress != null) {
                    count++;
                    // 格式化输出，包含坐标
                    sb.append(String.format(
                        "**%d. %s**\n" +
                        "   📍 %s\n",
                        count, currentName, currentAddress
                    ));

                    // 添加坐标信息（JSON格式）
                    if (currentLng != null && currentLat != null) {
                        sb.append(String.format("   📐 坐标：`{\"lng\": %s, \"lat\": %s}`\n", currentLng, currentLat));
                    }
                    sb.append("\n");

                    // 重置变量
                    currentName = null;
                    currentAddress = null;
                    currentLng = null;
                    currentLat = null;
                }
            }

            if (count == 0) {
                sb.append("暂无搜索结果\n\n");
            } else if (count >= maxResults) {
                sb.append(String.format("*...还有更多地点*\n\n"));
            }

            return sb.toString();
        } catch (Exception e) {
            return "**📍 搜索结果：**\n\n" + result.substring(0, Math.min(300, result.length())) + "...\n\n";
        }
    }

    /**
     * 从 JSON 行中提取值
     */
    private String extractJsonValue(String line) {
        int colonIndex = line.indexOf(':');
        if (colonIndex == -1) return null;
        String value = line.substring(colonIndex + 1).trim();
        value = value.replaceAll("[\",]", "").trim();
        return value.isEmpty() ? null : value;
    }

    /**
     * 美化 JSON 输出
     */
    private String beautifyJson(String json) {
        if (!json.contains("{") && !json.contains("[")) {
            return json;
        }
        try {
            return "```json\n" + json.substring(0, Math.min(1000, json.length())) + "\n```\n";
        } catch (Exception e) {
            return json;
        }
    }
}
