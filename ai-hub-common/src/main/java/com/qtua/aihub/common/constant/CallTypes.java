package com.qtua.aihub.common.constant;

/**
 * 调用类型常量（call_logs.call_type）。
 *
 * <p>集中维护，新增入口只在此扩展；治理 Filter 与日志门面统一引用。</p>
 */
public final class CallTypes {

    /** 聊天补全。 */
    public static final String CHAT = "CHAT";
    /** MCP 工具调用。 */
    public static final String MCP = "MCP";
    /** Prompt 渲染/对话。 */
    public static final String PROMPT = "PROMPT";
    /** 记忆服务。 */
    public static final String MEMORY = "MEMORY";

    private CallTypes() {
    }
}
