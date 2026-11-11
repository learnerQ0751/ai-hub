package com.qtua.aihub.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qtua.aihub.api.ChatCompletionService;
import com.qtua.aihub.api.RagSearchService;
import com.qtua.aihub.api.model.auth.KeyAuthDTO;
import com.qtua.aihub.api.model.req.ChatCompletionReqDTO;
import com.qtua.aihub.api.model.req.RagAnswerReqDTO;
import com.qtua.aihub.api.model.req.RagSearchReqDTO;
import com.qtua.aihub.api.model.res.ChatCompletionResDTO;
import com.qtua.aihub.api.model.res.RagAnswerResDTO;
import com.qtua.aihub.api.model.res.RagSearchHitResDTO;
import com.qtua.aihub.api.model.res.RagSearchResDTO;
import com.qtua.aihub.common.config.RagProperties;
import com.qtua.aihub.common.exception.ChatCompletionException;
import com.qtua.aihub.common.metering.TokenUsage;
import com.qtua.aihub.core.rag.RagPromptBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * REQ-3.4 生成服务测试：prompt/citations 映射、无召回兜底、自由回答开关、上下文预算与异常。
 */
class RagAnswerServiceTest {

    private static final String MODEL = "chat";
    private static final String REQUEST_ID = "req-1";
    private static final KeyAuthDTO AUTH = KeyAuthDTO.master();

    private final ObjectMapper objectMapper = new ObjectMapper();

    private RagSearchService searchService;
    private ChatCompletionService chatService;
    private RagProperties properties;
    private RagAnswerServiceImpl service;

    @BeforeEach
    void setUp() {
        searchService = mock(RagSearchService.class);
        chatService = mock(ChatCompletionService.class);
        properties = new RagProperties();
        service = new RagAnswerServiceImpl(searchService, chatService, new RagPromptBuilder(),
                objectMapper, properties);
    }

    @Test
    void answer_with_hits_builds_numbered_prompt_and_citations() {
        when(searchService.search(any())).thenReturn(searchResult(List.of(
                hit("c1", "d1", "员工手册.md", 0, "年假为 5 天", 0.8),
                hit("c2", "d2", "考勤制度.md", 1, "年假需提前申请", 0.7))));
        when(chatService.chat(any())).thenReturn(chatResponse("年假为 5 天 [1]", 10, 5));

        RagAnswerResDTO response = service.answer(request(null, null), AUTH, REQUEST_ID);

        ArgumentCaptor<ChatCompletionReqDTO> chat = ArgumentCaptor.forClass(
                ChatCompletionReqDTO.class);
        verify(chatService).chat(chat.capture());
        JsonNode body = readJson(chat.getValue().getRawBody());
        assertThat(body.path("model").asText()).isEqualTo(MODEL);
        assertThat(body.path("stream").asBoolean()).isFalse();
        assertThat(body.path("messages").get(0).path("role").asText()).isEqualTo("system");
        assertThat(body.path("messages").get(1).path("content").asText())
                .contains("[1]（来源：员工手册.md）")
                .contains("[2]（来源：考勤制度.md）")
                .contains("问题：年假有多少天");
        assertThat(chat.getValue().getRequestId()).isEqualTo(REQUEST_ID);

        assertThat(response.answer()).isEqualTo("年假为 5 天 [1]");
        assertThat(response.noHit()).isFalse();
        assertThat(response.freeAnswer()).isFalse();
        assertThat(response.citations()).extracting(citation -> citation.index())
                .containsExactly(1, 2);
        assertThat(response.citations()).extracting(citation -> citation.docId())
                .containsExactly("d1", "d2");
        assertThat(response.citations()).extracting(citation -> citation.score())
                .containsExactly(0.8, 0.7);
        assertThat(response.usage().promptTokens()).isEqualTo(10);
        assertThat(response.usage().completionTokens()).isEqualTo(5);
        assertThat(response.usage().totalTokens()).isEqualTo(15);
    }

    @Test
    void no_hit_returns_fallback_without_calling_model() {
        when(searchService.search(any())).thenReturn(emptySearchResult());

        RagAnswerResDTO response = service.answer(request(null, null), AUTH, REQUEST_ID);

        verify(chatService, never()).chat(any());
        assertThat(response.noHit()).isTrue();
        assertThat(response.freeAnswer()).isFalse();
        assertThat(response.answer()).isEqualTo(properties.getAnswer().getFallbackMessage());
        assertThat(response.citations()).isEmpty();
        assertThat(response.usage().totalTokens()).isZero();
    }

    @Test
    void no_hit_free_answer_calls_model_without_citations() {
        properties.getAnswer().setAllowFreeAnswer(true);
        when(searchService.search(any())).thenReturn(emptySearchResult());
        when(chatService.chat(any())).thenReturn(chatResponse("通用建议", 3, 2));

        RagAnswerResDTO response = service.answer(request(null, true), AUTH, REQUEST_ID);

        ArgumentCaptor<ChatCompletionReqDTO> chat = ArgumentCaptor.forClass(
                ChatCompletionReqDTO.class);
        verify(chatService).chat(chat.capture());
        assertThat(readJson(chat.getValue().getRawBody()).path("messages").get(0)
                .path("content").asText()).contains("通用知识");
        assertThat(response.noHit()).isTrue();
        assertThat(response.freeAnswer()).isTrue();
        assertThat(response.citations()).isEmpty();
        assertThat(response.answer()).isEqualTo("通用建议");
    }

    @Test
    void server_switch_off_ignores_requested_free_answer() {
        when(searchService.search(any())).thenReturn(emptySearchResult());

        RagAnswerResDTO response = service.answer(request(null, true), AUTH, REQUEST_ID);

        verify(chatService, never()).chat(any());
        assertThat(response.freeAnswer()).isFalse();
        assertThat(response.answer()).isEqualTo(properties.getAnswer().getFallbackMessage());
    }

    @Test
    void blank_model_is_rejected_before_search() {
        assertThatThrownBy(() -> service.answer(
                new RagAnswerReqDTO("问题", "p1", " ", null, null, null, null, null),
                AUTH, REQUEST_ID))
                .isInstanceOfSatisfying(ChatCompletionException.class, ex -> {
                    assertThat(ex.getParam()).isEqualTo("model");
                    assertThat(ex.getCode()).isEqualTo("400");
                });
        verify(searchService, never()).search(any());
        verify(chatService, never()).chat(any());
    }

    @Test
    void context_budget_limits_citations_and_prompt() {
        properties.getAnswer().setMaxContextChars(60);
        when(searchService.search(any())).thenReturn(searchResult(List.of(
                hit("c1", "d1", "a.md", 0, "甲".repeat(100), 0.8),
                hit("c2", "d2", "b.md", 1, "乙".repeat(100), 0.7))));
        when(chatService.chat(any())).thenReturn(chatResponse("答案 [1]", 3, 2));

        RagAnswerResDTO response = service.answer(request(null, null), AUTH, REQUEST_ID);

        ArgumentCaptor<ChatCompletionReqDTO> chat = ArgumentCaptor.forClass(
                ChatCompletionReqDTO.class);
        verify(chatService).chat(chat.capture());
        assertThat(readJson(chat.getValue().getRawBody()).path("messages").get(1)
                .path("content").asText()).doesNotContain("[2]");
        assertThat(response.citations()).hasSize(1);
    }

    @Test
    void empty_model_answer_uses_configured_fallback() {
        when(searchService.search(any())).thenReturn(searchResult(List.of(
                hit("c1", "d1", "a.md", 0, "资料", 0.8))));
        when(chatService.chat(any())).thenReturn(chatResponse("", 3, 0));

        RagAnswerResDTO response = service.answer(request(null, null), AUTH, REQUEST_ID);

        assertThat(response.answer()).isEqualTo(properties.getAnswer().getEmptyAnswerMessage());
        assertThat(response.citations()).hasSize(1);
    }

    @Test
    void request_null_is_rejected() {
        assertThatThrownBy(() -> service.answer(null, AUTH, REQUEST_ID))
                .isInstanceOfSatisfying(ChatCompletionException.class,
                        ex -> assertThat(ex.getMessage()).contains("请求体"));
    }

    @Test
    void search_parameters_are_forwarded() {
        when(searchService.search(any())).thenReturn(emptySearchResult());

        service.answer(new RagAnswerReqDTO("问题", "p1", MODEL, 2, 0.5, null, false, false),
                AUTH, REQUEST_ID);

        ArgumentCaptor<RagSearchReqDTO> search = ArgumentCaptor.forClass(
                RagSearchReqDTO.class);
        verify(searchService).search(search.capture());
        assertThat(search.getValue().query()).isEqualTo("问题");
        assertThat(search.getValue().projectId()).isEqualTo("p1");
        assertThat(search.getValue().topK()).isEqualTo(2);
        assertThat(search.getValue().minScore()).isEqualTo(0.5);
        assertThat(search.getValue().rerank()).isFalse();
    }

    private RagAnswerReqDTO request(Boolean rerank, Boolean allowFreeAnswer) {
        return new RagAnswerReqDTO("年假有多少天", "p1", MODEL, 4, 0.0, null, rerank,
                allowFreeAnswer);
    }

    private RagSearchResDTO searchResult(List<RagSearchHitResDTO> hits) {
        return new RagSearchResDTO("年假有多少天", "p1", hits.size(), false, false, hits);
    }

    private RagSearchResDTO emptySearchResult() {
        return new RagSearchResDTO("年假有多少天", "p1", 0, true, false, List.of());
    }

    private RagSearchHitResDTO hit(String chunkId, String docId, String source, int index,
                                   String content, double score) {
        return new RagSearchHitResDTO(chunkId, docId, source, index, content, score, null);
    }

    private ChatCompletionResDTO chatResponse(String answer, int promptTokens,
                                              int completionTokens) {
        String raw = "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\""
                + answer + "\"}}],\"usage\":{\"prompt_tokens\":" + promptTokens
                + ",\"completion_tokens\":" + completionTokens + ",\"total_tokens\":"
                + (promptTokens + completionTokens) + "}}";
        return new ChatCompletionResDTO(raw,
                new TokenUsage(promptTokens, completionTokens, promptTokens + completionTokens),
                "upstream-chat");
    }

    private JsonNode readJson(String body) {
        try {
            return objectMapper.readTree(body);
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }
}
