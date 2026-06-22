package com.zhou.zhouaiagent.mcp;

import com.zhou.zhouaiagent.config.otel.OtelContextUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.nio.file.ClosedWatchServiceException;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * MCP 配置文件监听器
 * 监听 mcp-servers.json 文件变更，自动重新加载 MCP Server 配置
 */
@Component
public class McpConfigFileWatcher {

    private static final Logger log = LoggerFactory.getLogger(McpConfigFileWatcher.class);

    private final McpToolRegistry mcpToolRegistry;
    // 用 OtelContextUtils.wrap 包装，确保异步线程自动继承提交线程的 OTel Context
    private final ExecutorService executor = OtelContextUtils.wrap(
            Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "mcp-config-watcher");
                t.setDaemon(true);
                return t;
            })
    );
    private final AtomicBoolean running = new AtomicBoolean(false);
    private WatchService watchService;

    public McpConfigFileWatcher(McpToolRegistry mcpToolRegistry) {
        this.mcpToolRegistry = mcpToolRegistry;
    }

    @PostConstruct
    public void startWatching() {
        try {
            Path configDir = Paths.get("src/main/resources");
            if (!configDir.toFile().exists()) {
                log.warn("Config directory does not exist: {}", configDir);
                return;
            }

            watchService = FileSystems.getDefault().newWatchService();
            configDir.register(watchService, StandardWatchEventKinds.ENTRY_MODIFY);
            running.set(true);

            executor.submit(this::watchLoop);
            log.info("Started watching MCP config file in directory: {}", configDir.toAbsolutePath());

            // 异步加载初始 MCP Server 配置，避免阻塞启动
            // 使用 OtelContextUtils.commonPool() 确保 OTel Context 传播，避免占用默认 ForkJoinPool
            CompletableFuture.runAsync(
                    OtelContextUtils.wrap(() -> {
                        try {
                            mcpToolRegistry.refreshFromConfigFile();
                        } catch (Exception e) {
                            log.warn("Failed to load initial MCP server config", e);
                        }
                    }),
                    OtelContextUtils.commonPool()
            );
        } catch (IOException e) {
            log.error("Failed to start MCP config file watcher", e);
        }
    }

    @PreDestroy
    public void stopWatching() {
        running.set(false);
        if (watchService != null) {
            try {
                watchService.close();
            } catch (IOException e) {
                log.warn("Error closing watch service", e);
            }
        }
        executor.shutdown();
        log.info("Stopped MCP config file watcher");
    }

    private void watchLoop() {
        while (running.get()) {
            try {
                WatchKey key = watchService.take();
                for (WatchEvent<?> event : key.pollEvents()) {
                    if (event.kind() == StandardWatchEventKinds.ENTRY_MODIFY) {
                        Path filename = (Path) event.context();
                        if (filename.toString().equals("mcp-servers.json")) {
                            log.info("MCP config file changed, refreshing...");
                            // 延迟 500ms 避免文件写入未完成
                            Thread.sleep(500);
                            OtelContextUtils.withSpan("mcp.config.refresh",
                                    () -> mcpToolRegistry.refreshFromConfigFile());
                        }
                    }
                }
                key.reset();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (ClosedWatchServiceException e) {
                // WatchService 已关闭，正常退出
                break;
            } catch (Exception e) {
                log.error("Error in watch loop", e);
            }
        }
    }
}
