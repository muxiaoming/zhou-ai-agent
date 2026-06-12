package com.zhou.zhouaiagent.rag.model;

/**
 * 查询路由决策
 * 由 LLM 自主判断用户问题是否需要从知识库检索
 */
public record RouteDecision(RouteAction action, String reason) {

    public enum RouteAction {
        RETRIEVE,
        DIRECT_ANSWER
    }
}
