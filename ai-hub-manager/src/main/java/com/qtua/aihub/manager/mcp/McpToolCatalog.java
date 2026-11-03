package com.qtua.aihub.manager.mcp;

import com.qtua.aihub.common.mcp.McpToolDescriptor;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * 工具目录本地快照缓存。
 *
 * <p>多实例说明：只读配置类数据的本地缓存，TTL 60s 内允许短暂不一致；
 * 注册/注销时调用 {@link #invalidate()} 让本实例立即失效，其他实例最多
 * 延迟一个 TTL。快照通过 volatile 引用整体替换，读无锁。</p>
 */
@Component
public class McpToolCatalog {

    private volatile Snapshot snapshot;

    /**
     * 工具目录快照。
     *
     * @param tools           聚合工具
     * @param degradedServers 降级 Server 名
     * @param fetchedAt       聚合时间
     */
    public record Snapshot(List<McpToolDescriptor> tools, List<String> degradedServers,
                           Instant fetchedAt) {

        /**
         * 防御性复制集合。
         */
        public Snapshot {
            tools = Objects.isNull(tools) ? List.of() : List.copyOf(tools);
            degradedServers = Objects.isNull(degradedServers) ? List.of() : List.copyOf(degradedServers);
        }
    }

    /**
     * 当前快照。
     *
     * @return 快照；从未聚合时为空
     */
    public Optional<Snapshot> current() {
        return Optional.ofNullable(snapshot);
    }

    /**
     * 快照是否仍在 TTL 内。
     *
     * @param ttl 有效期
     * @return true 表示可用
     */
    public boolean isFresh(Duration ttl) {
        Snapshot current = snapshot;
        if (Objects.isNull(current)) {
            return false;
        }
        return current.fetchedAt().plus(ttl).isAfter(Instant.now());
    }

    /**
     * 保存新快照（整体替换）。
     *
     * @param newSnapshot 新快照
     */
    public void save(Snapshot newSnapshot) {
        this.snapshot = newSnapshot;
    }

    /**
     * 失效快照（注册/注销/手动刷新时调用）。
     */
    public void invalidate() {
        this.snapshot = null;
    }
}
