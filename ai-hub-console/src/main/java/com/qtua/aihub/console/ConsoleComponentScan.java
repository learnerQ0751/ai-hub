package com.qtua.aihub.console;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;

/**
 * 控制面组件扫描范围（REQ-5.1）。
 *
 * <p>只装载管理链路需要的包；RAG 检索核心与检索重排按类名排除，不保留编译依赖。
 * 聊天运行时服务包不在扫描范围内。</p>
 */
@Configuration
@MapperScan("com.qtua.aihub.dal.mapper")
@ComponentScan(
        basePackages = {
                "com.qtua.aihub.start",
                "com.qtua.aihub.common",
                "com.qtua.aihub.service.shared",
                "com.qtua.aihub.service.admin",
                "com.qtua.aihub.service.console",
                "com.qtua.aihub.manager",
                "com.qtua.aihub.integration.prompt",
                "com.qtua.aihub.integration.rag",
                "com.qtua.aihub.integration.mcp",
                "com.qtua.aihub.core.rag"
        },
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.REGEX,
                pattern = "com\\.qtua\\.aihub\\.core\\.rag\\."
                        + "(RagRetrievalCore|RagLexicalReranker)"))
class ConsoleComponentScan {
}
