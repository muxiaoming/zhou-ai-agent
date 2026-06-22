package com.zhou.zhouaiagent.config.otel;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.TextMapSetter;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ForkJoinPool;

/**
 * OpenTelemetry 上下文传播工具类（Spring Bean）
 *
 * 核心职责：
 * 1. wrap(Runnable/Callable) —— 捕获调用线程的 OTel Context，在异步线程自动恢复
 * 2. wrapExecutor(Executor) —— 包装任意 Executor，使提交的 Runnable 自动携带 Context
 * 3. 全局 commonPool() —— 替代 ForkJoinPool.commonPool()，所有 CompletableFuture.runAsync 应使用此实例
 * 4. buildTraceparent(Context) —— 从指定 Context 提取 W3C traceparent 字符串
 * 5. withSpan(name, Runnable) —— 创建子 Span 包裹执行逻辑
 *
 * 必须作为 Spring Bean 初始化：@PostConstruct 将 Spring 管理的 OpenTelemetry 实例
 * 注册到 GlobalOpenTelemetry，解决 Spring Boot OTel AutoConfigure 默认不注册全局实例的问题。
 */
@Component
public final class OtelContextUtils {

    private static final Object lock = new Object();
    private static volatile Tracer tracer;
    private static volatile Executor wrappedCommonPool;

    private final OpenTelemetry openTelemetry;

    public OtelContextUtils(OpenTelemetry openTelemetry) {
        this.openTelemetry = openTelemetry;
    }

    /**
     * 将 Spring 管理的 OpenTelemetry 注册到 GlobalOpenTelemetry
     * 解决 otel.java.global-autoconfigure.enabled=false（默认）时 GlobalOpenTelemetry 返回 NoOp 的问题
     */
    @PostConstruct
    public void init() {
        try {
            GlobalOpenTelemetry.set(openTelemetry);
        } catch (IllegalStateException e) {
            // GlobalOpenTelemetry 已被其他组件设置（如 OTel AutoConfigure），忽略
        }
        // 直接从注入的实例获取 Tracer，不依赖 GlobalOpenTelemetry 的设置顺序
        tracer = openTelemetry.getTracer("zhou-ai-agent");
    }

    private static Tracer getTracer() {
        if (tracer == null) {
            synchronized (lock) {
                if (tracer == null) {
                    // 兜底：Spring Bean 未初始化时使用 GlobalOpenTelemetry（可能返回 NoOp）
                    tracer = GlobalOpenTelemetry.getTracer("zhou-ai-agent");
                }
            }
        }
        return tracer;
    }

    /**
     * 获取携带 OTel Context 的全局公共线程池
     * 替代 ForkJoinPool.commonPool()，避免异步任务丢失 Trace 上下文
     */
    public static Executor commonPool() {
        if (wrappedCommonPool == null) {
            synchronized (OtelContextUtils.class) {
                if (wrappedCommonPool == null) {
                    wrappedCommonPool = wrap(ForkJoinPool.commonPool());
                }
            }
        }
        return wrappedCommonPool;
    }

    /**
     * 包装 Executor，使提交的 Runnable 自动携带提交线程的 OTel Context
     */
    public static Executor wrap(Executor delegate) {
        return command -> delegate.execute(wrap(command));
    }

    /**
     * 包装 ExecutorService（保留 shutdown 能力）
     */
    public static ExecutorService wrap(ExecutorService delegate) {
        return new ContextPropagatingExecutorService(delegate);
    }

    /**
     * 包装 Runnable，捕获当前线程的 OTel Context，在执行时自动恢复
     */
    public static Runnable wrap(Runnable runnable) {
        Context captured = Context.current();
        return () -> {
            try (Scope ignored = captured.makeCurrent()) {
                runnable.run();
            }
        };
    }

    /**
     * 包装 Callable，捕获当前线程的 OTel Context，在执行时自动恢复
     */
    public static <T> Callable<T> wrap(Callable<T> callable) {
        Context captured = Context.current();
        return () -> {
            try (Scope ignored = captured.makeCurrent()) {
                return callable.call();
            }
        };
    }

    /**
     * 从给定 Context 提取 W3C traceparent 字符串
     * 格式：00-{traceId}-{spanId}-{traceFlags}
     *
     * @param context OTel Context（含 Span）
     * @return traceparent 字符串，无效时返回 null
     */
    public static String buildTraceparent(Context context) {
        Map<String, String> carrier = new HashMap<>();
        GlobalOpenTelemetry.getPropagators().getTextMapPropagator()
                .inject(context, carrier, Map::put);
        return carrier.get("traceparent");
    }

    /**
     * 从当前活跃 Context 提取 traceparent
     */
    public static String buildTraceparent() {
        return buildTraceparent(Context.current());
    }

    /**
     * 在子 Span 内执行任务（自动成为当前活跃 Span 的子 Span）
     *
     * @param spanName Span 名称
     * @param runnable 执行逻辑
     */
    public static void withSpan(String spanName, Runnable runnable) {
        Span span = getTracer().spanBuilder(spanName)
                .setSpanKind(SpanKind.INTERNAL)
                .startSpan();
        try (Scope ignored = span.makeCurrent()) {
            runnable.run();
        } catch (Exception e) {
            span.recordException(e);
            throw e;
        } finally {
            span.end();
        }
    }

    /**
     * 在子 Span 内执行任务并返回结果
     *
     * @param spanName Span 名称
     * @param callable 执行逻辑
     * @return 执行结果
     */
    public static <T> T withSpan(String spanName, Callable<T> callable) {
        Span span = getTracer().spanBuilder(spanName)
                .setSpanKind(SpanKind.INTERNAL)
                .startSpan();
        try (Scope ignored = span.makeCurrent()) {
            return callable.call();
        } catch (Exception e) {
            span.recordException(e);
            throw new RuntimeException(e);
        } finally {
            span.end();
        }
    }
}
