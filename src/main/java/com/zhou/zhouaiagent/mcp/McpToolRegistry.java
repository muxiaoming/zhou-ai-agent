package com.zhou.zhouaiagent.mcp;

import cn.hutool.json.JSON;
import cn.hutool.json.JSONObject;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientSseClientTransport;
import io.modelcontextprotocol.client.transport.ServerParameters;
import io.modelcontextprotocol.client.transport.StdioClientTransport;
import io.modelcontextprotocol.spec.McpClientTransport;
import io.modelcontextprotocol.spec.McpSchema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * MCP 工具注册中心
 * 维护所有 MCP Server 连接和工具映射，支持动态注册/注销
 */
@Component
public class McpToolRegistry {

    private static final Logger log = LoggerFactory.getLogger(McpToolRegistry.class);

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 已注册的 MCP Server 连接
     * key: server name, value: McpSyncClient
     */
    private final ConcurrentHashMap<String, McpSyncClient> serverClients = new ConcurrentHashMap<>();

    /**
     * 每个 Server 提供的工具列表
     * key: server name, value: tool callbacks
     */
    private final ConcurrentHashMap<String, List<ToolCallback>> serverTools = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        refreshFromConfigFile();
    }

    /**
     * 获取所有 MCP 工具（从所有已注册的 Server）
     */
    public List<ToolCallback> getAllMcpTools() {
        List<ToolCallback> allTools = new ArrayList<>();
        for (List<ToolCallback> tools : serverTools.values()) {
            allTools.addAll(tools);
        }
        return allTools;
    }

    /**
     * 获取所有已注册的 Server 名称
     */
    public List<String> getRegisteredServers() {
        return new ArrayList<>(serverClients.keySet());
    }

    /**
     * 注册 MCP Server（SSE 模式）
     */
    public synchronized boolean registerServer(String name, String url) {
        try {
            // 如果已存在，先注销
            if (serverClients.containsKey(name)) {
                unregisterServer(name);
            }

            log.info("Registering MCP Server (SSE): {} at {}", name, url);

            // 创建 SSE 传输层
            McpClientTransport transport = HttpClientSseClientTransport.builder(url).build();

            return doRegister(name, transport);
        } catch (Exception e) {
            log.error("Failed to register MCP Server: {}", name, e);
            return false;
        }
    }

    /**
     * 注册 MCP Server（stdio 模式）
     * 注入当前 Trace 的 traceparent 环境变量，实现跨进程链路串联
     */
    public synchronized boolean registerStdioServer(String name, String command, List<String> args, Map<String, String> env) {
        try {
            // 如果已存在，先注销
            if (serverClients.containsKey(name)) {
                unregisterServer(name);
            }

            log.info("Registering MCP Server (stdio): {} command: {} args: {}", name, command, args);

            // 构建 ServerParameters
            ServerParameters.Builder paramsBuilder = ServerParameters.builder(command);
            if (args != null && !args.isEmpty()) {
                paramsBuilder.args(args);
            }

            // 注入 traceparent 环境变量，使 MCP 子进程能关联到当前 Trace
            Map<String, String> mergedEnv = new HashMap<>();
            if (env != null) {
                mergedEnv.putAll(env);
            }
            // 注意：环境变量在子进程启动时固化，无法随每次工具调用更新。
            // 实际的跨进程 traceparent 传递由 McpToolCallback.injectTraceparent()
            // 在每次调用时通过工具输入 JSON 的 _traceparent 字段实现。
            // 此处保留 env 合并逻辑以支持配置文件中自定义环境变量。
            if (!mergedEnv.isEmpty()) {
                paramsBuilder.env(mergedEnv);
            }

            // 创建 stdio 传输层
            McpClientTransport transport = new StdioClientTransport(paramsBuilder.build());

            return doRegister(name, transport);
        } catch (Exception e) {
            log.error("Failed to register stdio MCP Server: {}", name, e);
            return false;
        }
    }

    /**
     * 通用注册逻辑
     */
    private boolean doRegister(String name, McpClientTransport transport) {
        try {
            // 创建 MCP 客户端
            McpSyncClient client = McpClient.sync(transport)
                    .clientInfo(new McpSchema.Implementation("zhou-ai-agent", "1.0.0"))
                    .build();

            // 初始化连接
            client.initialize();

            // 发现工具
            List<ToolCallback> tools = discoverTools(client, name);

            // 存储
            serverClients.put(name, client);
            serverTools.put(name, tools);

            log.info("Registered MCP Server: {} with {} tools", name, tools.size());
            return true;
        } catch (Exception e) {
            log.error("Failed to register MCP Server: {}", name, e);
            return false;
        }
    }

    /**
     * 注销 MCP Server
     */
    public synchronized boolean unregisterServer(String name) {
        McpSyncClient client = serverClients.remove(name);
        List<ToolCallback> tools = serverTools.remove(name);

        if (client != null) {
            try {
                client.close();
            } catch (Exception e) {
                log.warn("Error closing MCP client for server: {}", name, e);
            }
            log.info("Unregistered MCP Server: {}", name);
            return true;
        }
        return false;
    }

    /**
     * 从配置文件重新加载所有 MCP Server
     */
    public synchronized void refreshFromConfigFile() {
        try {
            // 先注销所有现有连接
            for (String name : new ArrayList<>(serverClients.keySet())) {
                unregisterServer(name);
            }

            // 从配置文件读取
            JsonNode config = loadConfigFile();
            if (config == null || !config.has("mcpServers")) {
                log.info("No MCP servers found in config file");
                return;
            }

            JsonNode servers = config.get("mcpServers");
            servers.fieldNames().forEachRemaining(serverName -> {
                JsonNode serverConfig = servers.get(serverName);
                if (serverConfig.has("url")) {
                    // SSE 模式
                    String url = serverConfig.get("url").asText();
                    registerServer(serverName, url);
                } else if (serverConfig.has("command")) {
                    // stdio 模式
                    String command = serverConfig.get("command").asText();

                    List<String> args = new ArrayList<>();
                    if (serverConfig.has("args")) {
                        serverConfig.get("args").forEach(node -> args.add(node.asText()));
                    }

                    Map<String, String> env = new java.util.HashMap<>();
                    if (serverConfig.has("env")) {
                        serverConfig.get("env").fields().forEachRemaining(entry ->
                                env.put(entry.getKey(), entry.getValue().asText())
                        );
                    }

                    registerStdioServer(serverName, command, args, env);
                }
            });

            log.info("Refreshed MCP servers from config file");
        } catch (Exception e) {
            log.error("Failed to refresh MCP servers from config file", e);
        }
    }

    private List<ToolCallback> discoverTools(McpSyncClient client, String serverName) {
        List<ToolCallback> tools = new ArrayList<>();
        try {
            McpSchema.ListToolsResult result = client.listTools();
            if (result != null && result.tools() != null) {
                for (McpSchema.Tool tool : result.tools()) {
                    tools.add(new McpToolCallback(client, tool, serverName));
                }
            }
        } catch (Exception e) {
            log.error("Failed to discover tools from server: {}", serverName, e);
        }
        return tools;
    }

    private JsonNode loadConfigFile() {
        try {
            // 尝试从 classpath 加载
            ClassPathResource resource = new ClassPathResource("mcp-servers.json");
            if (resource.exists()) {
                return objectMapper.readTree(resource.getInputStream());
            }
            // 尝试从文件系统加载
            File file = new File("src/main/resources/mcp-servers.json");
            if (file.exists()) {
                return objectMapper.readTree(file);
            }
        } catch (Exception e) {
            log.error("Failed to load MCP config file", e);
        }
        return null;
    }

    /**
     * MCP 工具回调适配器
     * 每次调用时注入当前 Trace 的 traceparent 到输入 JSON，实现跨进程链路串联
     */
    private static class McpToolCallback implements ToolCallback {
        private static final com.fasterxml.jackson.databind.ObjectMapper MAPPER = new com.fasterxml.jackson.databind.ObjectMapper();
        private final McpSyncClient client;
        private final McpSchema.Tool tool;
        private final String serverName;

        public McpToolCallback(McpSyncClient client, McpSchema.Tool tool, String serverName) {
            this.client = client;
            this.tool = tool;
            this.serverName = serverName;
        }

        @Override
        public String call(String toolInput) {
            // 注入当前 Trace 的 traceparent，使 MCP 子进程能关联到主进程链路
            String enrichedInput = injectTraceparent(toolInput);
            try {
                McpSchema.CallToolResult result = client.callTool(
                        new McpSchema.CallToolRequest(tool.name(), enrichedInput)
                );
                if (result != null && result.content() != null) {
                    StringBuilder sb = new StringBuilder();
                    for (McpSchema.Content content : result.content()) {
                        if (content instanceof McpSchema.TextContent textContent) {
                            sb.append(textContent.text());
                        }
                    }
                    return sb.toString();
                }
                return "";
            } catch (Exception e) {
                return "Error calling MCP tool: " + e.getMessage();
            }
        }

        /**
         * 将当前 OTel Trace 的 traceparent 注入到工具输入 JSON 中
         * 子进程可从 _traceparent 字段提取并关联到主进程的 Trace 链路
         */
        private String injectTraceparent(String toolInput) {
            try {
                String traceparent = com.zhou.zhouaiagent.config.otel.OtelContextUtils.buildTraceparent();
                if (traceparent == null) {
                    return toolInput;
                }
                com.fasterxml.jackson.databind.JsonNode node = MAPPER.readTree(
                        toolInput != null && !toolInput.isBlank() ? toolInput : "{}");
                if (!(node instanceof com.fasterxml.jackson.databind.node.ObjectNode root)) {
                    return toolInput;
                }
                root.put("_traceparent", traceparent);
                return MAPPER.writeValueAsString(root);
            } catch (Exception e) {
                return toolInput;
            }
        }

        @Override
        public ToolDefinition getToolDefinition() {
            try {
                return ToolDefinition.builder()
                        .name(serverName + "_" + tool.name())
                        .description(tool.description() != null ? tool.description() : tool.name())
                        .inputSchema(tool.inputSchema() != null ? MAPPER.writeValueAsString(tool.inputSchema()) : "{}")
                        .build();
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
