package com.qtua.aihub.console;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * 控制面启动入口（REQ-5.1）。
 *
 * <p>只装载管理能力：Key、模型注册、MCP Server 注册与状态、Prompt 模板与发布、
 * RAG 文档接入、用量/审计/Trace 查询。聊天运行时与 RAG 检索/生成核心不进入本应用。</p>
 */
@SpringBootApplication
@ConfigurationPropertiesScan("com.qtua.aihub")
public class ConsoleApplication {

    /**
     * 启动控制面应用。
     *
     * @param args 启动参数
     */
    public static void main(String[] args) {
        SpringApplication.run(ConsoleApplication.class, args);
    }
}
