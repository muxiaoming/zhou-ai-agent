package com.zhou.zhouaiagent.tools;

import org.springframework.ai.tool.annotation.Tool;

/**
 * 终止工具（作用是让自主规划智能体能够合理地中断）
 */
public class TerminateTool {

    // 当请求已满足，或者助手无法继续执行任务时，终止交互。
    // “当你完成所有任务后，调用此工具以结束工作。”
    @Tool(description = """
            Terminate the interaction when the request is met OR if the assistant cannot proceed further with the task.
            "When you have finished all the tasks, call this tool to end the work.
            """)
    public String doTerminate() {
        return "任务结束";
    }
}
