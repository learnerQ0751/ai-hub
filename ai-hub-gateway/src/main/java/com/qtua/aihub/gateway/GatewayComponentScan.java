package com.qtua.aihub.gateway;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;

/**
 * 数据面组件扫描范围（REQ-5.1）。
 *
 * <p>显式排除控制面服务包，避免管理服务被装配进数据面进程。</p>
 */
@Configuration
@MapperScan("com.qtua.aihub.dal.mapper")
@ComponentScan(
        basePackages = {
                "com.qtua.aihub.start",
                "com.qtua.aihub.common",
                "com.qtua.aihub.service",
                "com.qtua.aihub.manager",
                "com.qtua.aihub.core",
                "com.qtua.aihub.integration"
        },
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.REGEX,
                pattern = "com\\.qtua\\.aihub\\.service\\.admin\\..*"))
class GatewayComponentScan {
}
