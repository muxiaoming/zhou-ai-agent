package com.zhou.zhouaiagent.config.langfuse;

import io.micrometer.common.KeyValue;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationFilter;
import org.springframework.ai.chat.observation.ChatModelObservationContext;
import org.springframework.ai.content.Content;
import org.springframework.ai.observation.ObservabilityHelper;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * Langfuse 观测过滤器
 * 确保 LLM 调用的 prompt 和 completion 内容完整注入 OTel Span
 *
 * 设计原因：
 * - Spring AI 默认不记录 prompt/completion 内容（隐私保护）
 * - YAML 配置 spring.ai.chat.observations.log-prompt/completion=true 开启后，
 *   由 Spring AI 内置 Handler 写入日志，但不一定写入 Span attributes
 * - 此 Filter 直接操作 ObservationContext，保证 gen_ai.prompt 和 gen_ai.completion
 *   作为高基数 KeyValue 出现在 OTel Span 中，Langfuse 才能展示完整对话内容
 */
@Component
public class ChatModelCompletionContentObservationFilter implements ObservationFilter {

    @Override
    public Observation.Context map(Observation.Context context) {
        if (!(context instanceof ChatModelObservationContext chatModelObservationContext)) {
            return context;
        }

        List<String> prompts = processPrompts(chatModelObservationContext);
        List<String> completions = processCompletion(chatModelObservationContext);

        chatModelObservationContext.addHighCardinalityKeyValue(new KeyValue() {
            @Override
            public String getKey() {
                return "gen_ai.prompt";
            }

            @Override
            public String getValue() {
                return ObservabilityHelper.concatenateStrings(prompts);
            }
        });

        chatModelObservationContext.addHighCardinalityKeyValue(new KeyValue() {
            @Override
            public String getKey() {
                return "gen_ai.completion";
            }

            @Override
            public String getValue() {
                return ObservabilityHelper.concatenateStrings(completions);
            }
        });

        return chatModelObservationContext;
    }

    private List<String> processPrompts(ChatModelObservationContext chatModelObservationContext) {
        if (CollectionUtils.isEmpty(chatModelObservationContext.getRequest().getInstructions())) {
            return List.of();
        }
        return chatModelObservationContext.getRequest().getInstructions()
                .stream()
                .map(Content::getText)
                .toList();
    }

    private List<String> processCompletion(ChatModelObservationContext context) {
        if (context.getResponse() == null
                || context.getResponse().getResults() == null
                || CollectionUtils.isEmpty(context.getResponse().getResults())) {
            return List.of();
        }
        if (!StringUtils.hasText(context.getResponse().getResult().getOutput().getText())) {
            return List.of();
        }
        return context.getResponse().getResults()
                .stream()
                .filter(generation -> generation.getOutput() != null
                        && StringUtils.hasText(generation.getOutput().getText()))
                .map(generation -> generation.getOutput().getText())
                .toList();
    }
}
