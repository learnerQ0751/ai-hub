package com.qtua.aihub.service;

import com.qtua.aihub.api.model.req.RagSearchFilterDTO;
import com.qtua.aihub.api.model.req.RagSearchReqDTO;
import com.qtua.aihub.api.model.res.RagSearchResDTO;
import com.qtua.aihub.common.config.RagProperties;
import com.qtua.aihub.common.exception.ChatCompletionException;
import com.qtua.aihub.common.rag.RagSearchRequest;
import com.qtua.aihub.core.rag.RagRetrievalCore;
import com.qtua.aihub.core.rag.RagRetrievalResult;
import com.qtua.aihub.core.rag.RagRetrievedChunk;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * REQ-3.3 检索服务测试：参数校验、默认值/上限收敛、过滤归一化、重排开关与响应映射。
 */
class RagSearchServiceTest {

    private RagRetrievalCore retrievalCore;
    private RagProperties properties;
    private RagSearchServiceImpl service;

    @BeforeEach
    void setUp() {
        retrievalCore = mock(RagRetrievalCore.class);
        properties = new RagProperties();
        service = new RagSearchServiceImpl(retrievalCore, properties);
        when(retrievalCore.retrieve(any())).thenReturn(new RagRetrievalResult(
                List.of(new RagRetrievedChunk("c1", "d1", "p1", "demo.md", 0, "内容",
                        0.8, null)), 1, false));
    }

    @Test
    void applies_defaults_and_maps_response() {
        RagSearchResDTO response = service.search(new RagSearchReqDTO("年假", "p1", null, null,
                null, null));

        RagSearchRequest request = captureRequest();
        assertThat(request.topK()).isEqualTo(5);
        assertThat(request.minScore()).isZero();
        assertThat(request.candidateLimit()).isEqualTo(20);
        assertThat(request.rerank()).isTrue();
        assertThat(request.filter().isEmpty()).isTrue();
        assertThat(response.query()).isEqualTo("年假");
        assertThat(response.projectId()).isEqualTo("p1");
        assertThat(response.count()).isEqualTo(1);
        assertThat(response.noHit()).isFalse();
        assertThat(response.results().get(0).chunkId()).isEqualTo("c1");
    }

    @Test
    void clamps_top_k_to_configured_max() {
        properties.getSearch().setMaxTopK(10);
        service.search(new RagSearchReqDTO("q", "p1", 20, null, null, false));

        RagSearchRequest request = captureRequest();
        assertThat(request.topK()).isEqualTo(10);
        assertThat(request.candidateLimit()).isEqualTo(40);
    }

    @Test
    void rejects_invalid_top_k() {
        assertThatThrownBy(() -> service.search(new RagSearchReqDTO("q", "p1", 0, null, null,
                null)))
                .isInstanceOfSatisfying(ChatCompletionException.class, ex -> {
                    assertThat(ex.getParam()).isEqualTo("top_k");
                    assertThat(ex.getCode()).isEqualTo("400");
                    assertThat(ex.getMessage()).contains("top_k");
                });
    }

    @Test
    void rejects_out_of_range_min_score() {
        assertThatThrownBy(() -> service.search(new RagSearchReqDTO("q", "p1", 3, 2.0, null,
                null)))
                .isInstanceOfSatisfying(ChatCompletionException.class, ex -> {
                    assertThat(ex.getParam()).isEqualTo("min_score");
                    assertThat(ex.getCode()).isEqualTo("400");
                });
    }

    @Test
    void rejects_missing_query_and_project() {
        assertThatThrownBy(() -> service.search(new RagSearchReqDTO(" ", "p1", null, null, null,
                null)))
                .isInstanceOfSatisfying(ChatCompletionException.class,
                        ex -> assertThat(ex.getParam()).isEqualTo("query"));
        assertThatThrownBy(() -> service.search(new RagSearchReqDTO("q", null, null, null, null,
                null)))
                .isInstanceOfSatisfying(ChatCompletionException.class,
                        ex -> assertThat(ex.getParam()).isEqualTo("project_id"));
        assertThatThrownBy(() -> service.search(null))
                .isInstanceOfSatisfying(ChatCompletionException.class,
                        ex -> assertThat(ex.getMessage()).contains("请求体"));
    }

    @Test
    void normalizes_filter_doc_ids() {
        service.search(new RagSearchReqDTO("q", "p1", 3, null,
                new RagSearchFilterDTO(List.of(" d1 ", "d1", "d2"), " demo.md "), false));

        RagSearchRequest request = captureRequest();
        assertThat(request.filter().docIds()).containsExactly("d1", "d2");
        assertThat(request.filter().source()).isEqualTo("demo.md");
    }

    @Test
    void rejects_too_many_filter_doc_ids() {
        properties.getSearch().setMaxFilterDocIds(2);
        assertThatThrownBy(() -> service.search(new RagSearchReqDTO("q", "p1", 3, null,
                new RagSearchFilterDTO(List.of("d1", "d2", "d3"), null), false)))
                .isInstanceOfSatisfying(ChatCompletionException.class,
                        ex -> assertThat(ex.getParam()).isEqualTo("filters.doc_ids"));
    }

    @Test
    void server_rerank_switch_disables_requested_rerank() {
        properties.getSearch().setRerankEnabled(false);
        service.search(new RagSearchReqDTO("q", "p1", 3, null, null, true));

        assertThat(captureRequest().rerank()).isFalse();
    }

    @Test
    void request_can_disable_rerank() {
        service.search(new RagSearchReqDTO("q", "p1", 3, null, null, false));

        assertThat(captureRequest().rerank()).isFalse();
    }

    @Test
    void empty_result_marks_no_hit() {
        when(retrievalCore.retrieve(any())).thenReturn(new RagRetrievalResult(List.of(), 0,
                false));

        RagSearchResDTO response = service.search(new RagSearchReqDTO("q", "p1", 3, null, null,
                false));

        assertThat(response.noHit()).isTrue();
        assertThat(response.count()).isZero();
        assertThat(response.results()).isEmpty();
    }

    private RagSearchRequest captureRequest() {
        ArgumentCaptor<RagSearchRequest> captor = ArgumentCaptor.forClass(RagSearchRequest.class);
        verify(retrievalCore).retrieve(captor.capture());
        return captor.getValue();
    }
}
