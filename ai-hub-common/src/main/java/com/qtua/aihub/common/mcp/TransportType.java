package com.qtua.aihub.common.mcp;

import org.springframework.util.StringUtils;

/**
 * MCP Server 传输方式。
 *
 * <p>{@code HTTP} 为 Streamable HTTP（首版实现）；{@code STDIO} 为本地子进程（首版实现）；
 * {@code SSE} 保留枚举、暂不实现（注册时明确报错，避免静默降级）。</p>
 */
public enum TransportType {

    /** Streamable HTTP 传输（远程 Server）。 */
    HTTP("http"),

    /** Server-Sent Events 传输（预留，暂不支持）。 */
    SSE("sse"),

    /** 标准输入输出传输（本地子进程）。 */
    STDIO("stdio");

    private final String value;

    TransportType(String value) {
        this.value = value;
    }

    /**
     * 协议取值（小写，对齐 LiteLLM/MCP 配置）。
     *
     * @return 枚举对应的字符串值
     */
    public String value() {
        return value;
    }

    /**
     * 按字符串解析传输方式（大小写不敏感）。
     *
     * @param value 传输方式字符串，如 http/stdio
     * @return 枚举；无法识别时返回 null
     */
    public static TransportType from(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        for (TransportType type : values()) {
            if (type.value.equalsIgnoreCase(value.trim())) {
                return type;
            }
        }
        return null;
    }
}
