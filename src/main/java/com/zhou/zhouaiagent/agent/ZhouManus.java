package com.zhou.zhouaiagent.agent;

import com.zhou.zhouaiagent.advisor.MyLoggerAdvisor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Component;

/**
 *  AI 超级智能体（拥有自主规划能力，可以直接使用）
 *  整个agent核心代码就三块:
 *      1. BaseAgent agent loop , 有限(最大次数及完成状态, 大模型判断完成或无法继续调用中断方法)循环执行每一步
 *          将每一步抽象为一个抽象方法 step(), 具体实现由子类agent实现.
 *          (有限循环执行step()方法)
 *      2. ReActAgent 实现step方法, 实现逻辑是先思考 think(), 再判断是否需要执行 act()
 *          只是在step()中实现执行逻辑, think()/act()也是抽象方法, 思考与行动的实现也是由子类agent实现.
 *          (实现step()方法, 只有执行逻辑但未实现具体思考与行动)
 *      3. ToolCallAgent 实现了think()/act(), think()调用大模型判断是否需要执行工具调用, act()中手动调用think()中需要执行的工具
 *          在 ChatOptions 中禁用 Spring AI 内置的工具调用机制后手动调用工具
 *          think()调用参考 LoveApp#doChatWithTools(), 因需要禁用内置工具调用, 得自己构造Prompt调用ChatClient
 *          act() 将在think()中需要执行的工具手动控制调用
 */
@Component
public class ZhouManus extends ToolCallAgent {

    public ZhouManus(ToolCallback[] allTools, ChatModel dashscopeChatModel) {
        super(allTools);
        this.setName("zhouManus");
        // 你是ZhouManus，一位全能型AI助手，旨在解决用户提出的任何任务。
        // 你拥有多种工具可供调用，以便高效完成复杂请求。
        String SYSTEM_PROMPT = """
                You are ZhouManus, an all-capable AI assistant, aimed at solving any task presented by the user.
                You have various tools at your disposal that you can call upon to efficiently complete complex requests.
                """;
        // 多语言版, 根据用户提示词输出相同语言
        // 或者 在调用 LLM 或 agent 之前，增加一个简单的语言检测（例如用 langdetect 或判断中文字符比例），然后将一个类似 "output_language": "zh" 的参数传给 agent，并在系统提示词中引用该参数。
        String SYSTEM_PROMPT_LANGUAGE = """
                You are ZhouManus, an all-capable AI assistant, aimed at solving any task presented by the user.
                You have various tools at your disposal that you can call upon to efficiently complete complex requests.
                You must detect the language of the user's prompt and respond in the SAME language.
                If the user prompt is in Chinese, all your outputs (including any generated PDF text) must be in Simplified Chinese.
                If the user prompt is in English, output in English.
                Do not mix languages unless quoting proper nouns.
                """;
        this.setSystemPrompt(SYSTEM_PROMPT);
        // 根据用户需求，主动选择最合适的工具或工具组合。
        // 对于复杂任务，你可以拆解问题，并逐步使用不同的工具来解决。
        // 使用每个工具后，清晰说明执行结果并提出下一步建议。
        // 若要在任何时候停止交互，请使用 `terminate` 工具/函数调用。
        String NEXT_STEP_PROMPT = """
                Based on user needs, proactively select the most appropriate tool or combination of tools.
                For complex tasks, you can break down the problem and use different tools step by step to solve it.
                After using each tool, clearly explain the execution results and suggest the next steps.
                If you want to stop the interaction at any point, use the `terminate` tool/function call.
                """;
        this.setNextStepPrompt(NEXT_STEP_PROMPT);
        this.setMaxSteps(20);
        // 初始化 AI 对话客户端
        ChatClient chatClient = ChatClient.builder(dashscopeChatModel)
                .defaultAdvisors(new MyLoggerAdvisor())
                .build();
        this.setChatClient(chatClient);
    }
}
