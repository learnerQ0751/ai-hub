package com.qtua.aihub.core.mcp;

import com.qtua.aihub.common.mcp.McpProbeTarget;
import com.qtua.aihub.common.mcp.McpServerStatus;
import com.qtua.aihub.common.mcp.RawTool;
import com.qtua.aihub.common.mcp.ToolAggregationResult;
import com.qtua.aihub.common.mcp.TransportType;
import com.qtua.aihub.integration.mcp.McpToolClient;
import com.qtua.aihub.manager.model.McpServerBO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * REQ-2.2 聚合器：并行执行与故障归属测试。
 *
 * <p>验证"先全部提交、再顺序收集"确实是并行执行（总耗时 ≈ 最慢任务），
 * 以及单 Server 异常的降级标记。</p>
 */
class McpToolAggregatorTest {

    private McpToolAggregator aggregator;

    @AfterEach
    void tearDown() {
        if (aggregator != null) {
            aggregator.shutdown();
        }
    }

    @Test
    void aggregates_servers_in_parallel_not_serially() {
        McpToolClient client = mock(McpToolClient.class);
        when(client.listTools(any())).thenAnswer(invocation -> {
            McpProbeTarget target = invocation.getArgument(0);
            Thread.sleep(600L);
            return List.of(new RawTool("tool_" + target.name(), "desc", "{}", false));
        });
        aggregator = new McpToolAggregator(client);

        long start = System.nanoTime();
        ToolAggregationResult result = aggregator.aggregate(
                List.of(server("server_a"), server("server_b")));
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        assertThat(result.tools()).hasSize(2);
        // 串行需要约 1200ms；并行应在 ~600ms 完成。
        assertThat(elapsedMs).isLessThan(1000L);
    }

    @Test
    void failed_server_is_marked_degraded_without_blocking_others() {
        McpToolClient client = mock(McpToolClient.class);
        when(client.listTools(any())).thenAnswer(invocation -> {
            McpProbeTarget target = invocation.getArgument(0);
            if ("bad_server".equals(target.name())) {
                throw new IllegalStateException("boom");
            }
            return List.of(new RawTool("ok_tool", "desc", "{}", false));
        });
        aggregator = new McpToolAggregator(client);

        ToolAggregationResult result = aggregator.aggregate(
                List.of(server("bad_server"), server("good_server")));

        assertThat(result.tools()).hasSize(1);
        assertThat(result.tools().get(0).exposedName()).isEqualTo("good_server__ok_tool");
        assertThat(result.degradedServers()).containsExactly("bad_server");
    }

    private static McpServerBO server(String name) {
        return new McpServerBO("id-" + name, name, TransportType.HTTP,
                "http://127.0.0.1:1/mcp", null, List.of(), null,
                McpServerStatus.ACTIVE, null, null, null, Instant.now());
    }
}
