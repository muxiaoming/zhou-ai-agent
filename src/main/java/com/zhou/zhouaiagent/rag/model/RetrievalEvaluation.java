package com.zhou.zhouaiagent.rag.model;

/**
 * 检索质量评估
 * 由 LLM 评估检索到的文档是否足以回答用户问题
 */
public record RetrievalEvaluation(RetrievalAction action, String reason, String refinedQuery) {

    public enum RetrievalAction {
        SUFFICIENT,
        RE_RETRIEVE,
        GIVE_UP
    }
}
