package com.zhou.zhouaiagent.controller;

import com.zhou.zhouaiagent.mcp.DynamicToolCallbackProvider;
import com.zhou.zhouaiagent.mcp.McpToolRegistry;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * MCP Server 管理 API
 * 支持运行时动态注册/注销 MCP Server，实现工具热插拔
 */
@Tag(name = "MCP Server 管理", description = "动态管理 MCP Server 连接和工具")
@RestController
@RequestMapping("/mcp/servers")
public class McpServerController {

    private final McpToolRegistry mcpToolRegistry;
    private final DynamicToolCallbackProvider dynamicToolCallbackProvider;

    public McpServerController(McpToolRegistry mcpToolRegistry,
                               DynamicToolCallbackProvider dynamicToolCallbackProvider) {
        this.mcpToolRegistry = mcpToolRegistry;
        this.dynamicToolCallbackProvider = dynamicToolCallbackProvider;
    }

    @Operation(summary = "列出所有已注册的 MCP Server")
    @GetMapping
    public Map<String, Object> listServers() {
        List<String> servers = mcpToolRegistry.getRegisteredServers();
        DynamicToolCallbackProvider.ToolStats stats = dynamicToolCallbackProvider.getStats();
        return Map.of(
                "servers", servers,
                "stats", Map.of(
                        "builtIn", stats.builtIn(),
                        "mcp", stats.mcp(),
                        "dynamic", stats.dynamic(),
                        "total", stats.total()
                )
        );
    }

    @Operation(summary = "注册新的 MCP Server（SSE 模式）")
    @PostMapping
    public Map<String, Object> registerServer(@RequestBody McpServerRequest request) {
        boolean success = mcpToolRegistry.registerServer(request.name(), request.url());
        return Map.of(
                "success", success,
                "server", request.name(),
                "message", success ? "Server registered successfully" : "Failed to register server"
        );
    }

    @Operation(summary = "注销 MCP Server")
    @DeleteMapping("/{name}")
    public Map<String, Object> unregisterServer(@PathVariable String name) {
        boolean success = mcpToolRegistry.unregisterServer(name);
        return Map.of(
                "success", success,
                "server", name,
                "message", success ? "Server unregistered successfully" : "Server not found"
        );
    }

    @Operation(summary = "从配置文件重新加载所有 MCP Server")
    @PostMapping("/refresh")
    public Map<String, Object> refreshServers() {
        mcpToolRegistry.refreshFromConfigFile();
        List<String> servers = mcpToolRegistry.getRegisteredServers();
        return Map.of(
                "success", true,
                "servers", servers,
                "message", "Refreshed " + servers.size() + " servers from config file"
        );
    }

    @Operation(summary = "列出所有可用工具（内置 + MCP + 动态）")
    @GetMapping("/tools")
    public Map<String, Object> listTools() {
        ToolCallback[] allTools = dynamicToolCallbackProvider.getToolCallbacks();
        List<Map<String, String>> toolList = java.util.Arrays.stream(allTools)
                .map(tool -> Map.of(
                        "name", tool.getToolDefinition().name(),
                        "description", tool.getToolDefinition().description()
                ))
                .collect(Collectors.toList());
        return Map.of(
                "total", toolList.size(),
                "tools", toolList
        );
    }

    public record McpServerRequest(String name, String url) {}
}
