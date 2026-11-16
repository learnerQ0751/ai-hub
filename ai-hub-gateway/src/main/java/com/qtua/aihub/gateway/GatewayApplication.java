package com.qtua.aihub.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * 数据面启动入口（REQ-5.1）。
 *
 * <p>只装载请求路径上的能力：聊天、嵌入、模型列表、MCP 调用、Prompt 运行时、
 * RAG 检索与生成、记忆召回，以及虚拟 Key 鉴权与治理链。控制面服务（service.admin）
 * 不进入本应用的组件扫描范围。</p>
 */
@SpringBootApplication
@ConfigurationPropertiesScan("com.qtua.aihub")
public class GatewayApplication {

    /**
     * 启动数据面应用。
     *
     * @param args 启动参数
     */
    public static void main(String[] args) {
        SpringApplication.run(GatewayApplication.class, args);
    }
}
