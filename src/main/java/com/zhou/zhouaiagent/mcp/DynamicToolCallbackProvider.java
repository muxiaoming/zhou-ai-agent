package com.zhou.zhouaiagent.mcp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 动态工具提供者
 * 统一管理内置工具和 MCP 工具，支持运行时动态添加/移除
 */
@Component
public class DynamicToolCallbackProvider implements ToolCallbackProvider {

    private static final Logger log = LoggerFactory.getLogger(DynamicToolCallbackProvider.class);

    private final McpToolRegistry mcpToolRegistry;
    private final ToolCallback[] builtInTools;

    /**
     * 额外的动态工具（通过 API 添加）
     */
    private final CopyOnWriteArrayList<ToolCallback> dynamicTools = new CopyOnWriteArrayList<>();

    public DynamicToolCallbackProvider(McpToolRegistry mcpToolRegistry, ToolCallback[] allTools) {
        this.mcpToolRegistry = mcpToolRegistry;
        this.builtInTools = allTools;
    }

    @Override
    public ToolCallback[] getToolCallbacks() {
        List<ToolCallback> allTools = new ArrayList<>();

        // 1. 内置工具
        allTools.addAll(Arrays.asList(builtInTools));

        // 2. MCP 工具（动态）
        allTools.addAll(mcpToolRegistry.getAllMcpTools());

        // 3. 额外的动态工具
        allTools.addAll(dynamicTools);

        log.debug("Providing {} total tools (built-in: {}, mcp: {}, dynamic: {})",
                allTools.size(), builtInTools.length,
                mcpToolRegistry.getAllMcpTools().size(), dynamicTools.size());

        return allTools.toArray(new ToolCallback[0]);
    }

    /**
     * 添加动态工具
     */
    public void addDynamicTool(ToolCallback tool) {
        dynamicTools.add(tool);
        log.info("Added dynamic tool: {}", tool.getToolDefinition().name());
    }

    /**
     * 移除动态工具
     */
    public void removeDynamicTool(String toolName) {
        dynamicTools.removeIf(tool -> tool.getToolDefinition().name().equals(toolName));
        log.info("Removed dynamic tool: {}", toolName);
    }

    /**
     * 获取工具统计信息
     */
    public ToolStats getStats() {
        return new ToolStats(
                builtInTools.length,
                mcpToolRegistry.getAllMcpTools().size(),
                dynamicTools.size()
        );
    }

    public record ToolStats(int builtIn, int mcp, int dynamic) {
        public int total() {
            return builtIn + mcp + dynamic;
        }
    }
}
