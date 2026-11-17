package com.qtua.aihub.core.rag;

import com.qtua.aihub.manager.model.RagChunkBO;
import com.qtua.aihub.manager.rag.RagChunkManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * 回答侧上下文拼装（REQ-5.4）。
 *
 * <p>检索结果按相关度排序，回答侧需要原文顺序：先按文档首次命中顺序分组，
 * 组内按 `chunk_index` 升序还原。可选邻接扩展按租户 + 项目 + 文档查询命中前后
 * 各 n 条，按 `chunk_index` 去重；扩展邻居只作上下文，不参与引用。</p>
 *
 * <p>扩展是增强而非正确性前提：邻居查询失败时记 WARN 并退回「仅原命中」。
 * 本类无可变业务状态，多实例安全。</p>
 */
@Component
public class RagContextAssembler {

    private static final Logger LOG = LoggerFactory.getLogger(RagContextAssembler.class);

    /** 邻接窗口上限，避免一次回答回库拉取过多切片。 */
    private static final int MAX_NEIGHBOR_WINDOW = 5;
    private static final int MIN_INDEX = 0;

    private final RagChunkManager chunkManager;

    /**
     * 构造拼装器。
     *
     * @param chunkManager 切片门面
     */
    public RagContextAssembler(RagChunkManager chunkManager) {
        this.chunkManager = chunkManager;
    }

    /**
     * 按文档与序号还原上下文，并按需扩展邻居。
     *
     * @param hits          检索命中（相关度序）
     * @param tenantId      租户 ID
     * @param projectId     项目 ID
     * @param neighborWindow 前后邻居条数；0 表示不扩展
     * @return 按文档分组、组内序号升序的片段
     */
    public List<RagContextSegment> assemble(List<RagRetrievedChunk> hits, String tenantId,
                                            String projectId, int neighborWindow) {
        if (Objects.isNull(hits) || hits.isEmpty()) {
            return List.of();
        }
        int window = Math.max(MIN_INDEX, Math.min(MAX_NEIGHBOR_WINDOW, neighborWindow));
        Map<String, List<RagRetrievedChunk>> byDoc = new LinkedHashMap<>();
        for (RagRetrievedChunk hit : hits) {
            byDoc.computeIfAbsent(hit.docId(), ignored -> new ArrayList<>()).add(hit);
        }
        List<RagContextSegment> segments = new ArrayList<>();
        for (Map.Entry<String, List<RagRetrievedChunk>> entry : byDoc.entrySet()) {
            appendDocument(entry.getKey(), entry.getValue(), tenantId, projectId, window, segments);
        }
        return segments;
    }

    private void appendDocument(String docId, List<RagRetrievedChunk> hits, String tenantId,
                                String projectId, int window, List<RagContextSegment> out) {
        List<RagRetrievedChunk> anchors = new ArrayList<>(hits);
        anchors.sort(Comparator.comparingInt(RagRetrievedChunk::chunkIndex)
                .thenComparing(RagRetrievedChunk::chunkId));
        TreeMap<Integer, RagRetrievedChunk> anchorByIndex = new TreeMap<>();
        for (RagRetrievedChunk anchor : anchors) {
            anchorByIndex.putIfAbsent(anchor.chunkIndex(), anchor);
        }
        TreeMap<Integer, RagChunkBO> neighbors = new TreeMap<>();
        if (window > 0) {
            loadNeighbors(tenantId, projectId, docId, anchorByIndex.keySet(), window, neighbors);
        }
        Set<Integer> included = new TreeSet<>(anchorByIndex.keySet());
        included.addAll(neighbors.keySet());
        for (Integer chunkIndex : included) {
            RagRetrievedChunk anchor = anchorByIndex.get(chunkIndex);
            if (!Objects.isNull(anchor)) {
                out.add(RagContextSegment.fromHit(anchor));
                continue;
            }
            RagChunkBO neighbor = neighbors.get(chunkIndex);
            out.add(new RagContextSegment(neighbor.chunkId(), neighbor.docId(),
                    neighbor.source(), neighbor.chunkIndex(), neighbor.content(), 0.0d, false));
        }
    }

    /**
     * 按合并后的区间回库读取邻居；越租户/越项目或无权限的行直接丢弃。
     */
    private void loadNeighbors(String tenantId, String projectId, String docId,
                               Set<Integer> anchorIndexes, int window,
                               Map<Integer, RagChunkBO> neighbors) {
        for (int[] range : mergeRanges(anchorIndexes, window)) {
            List<RagChunkBO> rows;
            try {
                rows = chunkManager.listByDocIdIndexRange(tenantId, projectId, docId,
                        range[0], range[1]);
            } catch (RuntimeException ex) {
                // 扩展失败只降级为原命中，不影响回答主链路。
                LOG.warn("rag neighbor expansion failed: tenantId={}, projectId={}, docId={}, "
                                + "range={}-{}, error={}", tenantId, projectId, docId, range[0],
                        range[1], ex.getMessage());
                continue;
            }
            for (RagChunkBO row : rows) {
                if (!Objects.equals(tenantId, row.tenantId())
                        || !Objects.equals(projectId, row.projectId())
                        || !Objects.equals(docId, row.docId())
                        || anchorIndexes.contains(row.chunkIndex())) {
                    continue;
                }
                neighbors.putIfAbsent(row.chunkIndex(), row);
            }
        }
    }

    private List<int[]> mergeRanges(Set<Integer> anchorIndexes, int window) {
        List<int[]> ranges = new ArrayList<>();
        int currentFrom = -1;
        int currentTo = -1;
        for (Integer anchor : anchorIndexes) {
            int from = Math.max(MIN_INDEX, anchor - window);
            int to = anchor + window;
            if (currentFrom < 0) {
                currentFrom = from;
                currentTo = to;
            } else if (from <= currentTo + 1) {
                currentTo = Math.max(currentTo, to);
            } else {
                ranges.add(new int[]{currentFrom, currentTo});
                currentFrom = from;
                currentTo = to;
            }
        }
        if (currentFrom >= 0) {
            ranges.add(new int[]{currentFrom, currentTo});
        }
        return ranges;
    }
}
