package com.qtua.aihub;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qtua.aihub.dal.mapper.CallLogMapper;
import com.qtua.aihub.dal.mapper.RagChunkMapper;
import com.qtua.aihub.dal.mapper.RagDocumentMapper;
import com.qtua.aihub.dal.model.CallLogDO;
import com.qtua.aihub.dal.model.RagChunkDO;
import com.qtua.aihub.dal.model.RagDocumentDO;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * REQ-3.1 端到端验收：上传/切分/列表/去重/解析失败留存/删除级联与 RAG 调用日志。
 *
 * <p>使用 H2（PostgreSQL 兼容模式）与确定性 embedding/内存向量存储，不依赖本机
 * PostgreSQL/Docker/ONNX 模型。</p>
 */
@SpringBootTest(classes = AiHubApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class RagDocumentIntegrationTest {

    private static final String MASTER_KEY = "integration-master";

    private final ObjectMapper objectMapper = new ObjectMapper();

    @LocalServerPort
    private int port;

    @Autowired
    private CallLogMapper callLogMapper;

    @Autowired
    private RagDocumentMapper documentMapper;

    @Autowired
    private RagChunkMapper chunkMapper;

    private RestClient restClient;

    @BeforeEach
    void setUp() {
        restClient = RestClient.builder()
                .baseUrl("http://localhost:" + port)
                .defaultStatusHandler(status -> true, (request, response) -> {
                })
                .build();
    }

    @DynamicPropertySource
    static void gatewayProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:h2:mem:aihub_rag;"
                + "MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        registry.add("gateway.master-key", () -> MASTER_KEY);
    }

    @Test
    void upload_creates_chunks_and_lists_by_project() {
        String content = "第一段：" + "甲".repeat(50) + "\n\n第二段：" + "乙".repeat(50)
                + "\n\n第三段：" + "丙".repeat(50);
        ResponseEntity<String> uploaded = upload("p-rag-upload", "demo.md",
                content.getBytes(StandardCharsets.UTF_8), MediaType.TEXT_MARKDOWN);
        assertThat(uploaded.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        JsonNode body = readJson(uploaded);
        String docId = body.path("doc_id").asText();
        assertThat(docId).isNotBlank();
        assertThat(body.path("source").asText()).isEqualTo("demo.md");
        assertThat(body.path("project_id").asText()).isEqualTo("p-rag-upload");
        assertThat(body.path("chunk_count").asInt()).isEqualTo(2);
        assertThat(body.path("content_hash").asText()).hasSize(64);

        List<RagChunkDO> chunks = chunkMapper.selectByDocId(docId);
        assertThat(chunks).hasSize(2);
        assertThat(chunks.get(0).getContent()).contains("甲").contains("乙");
        assertThat(chunks.get(1).getContent()).contains("丙");
        assertThat(chunks).allSatisfy(chunk -> {
            assertThat(chunk.getProjectId()).isEqualTo("p-rag-upload");
            assertThat(chunk.getSource()).isEqualTo("demo.md");
            assertThat(chunk.getChunkId()).startsWith(docId);
            assertThat(chunk.getCreatedAt()).isNotNull();
        });

        JsonNode listed = readJson(list("p-rag-upload"));
        assertThat(listed.path("count").asInt()).isEqualTo(1);
        assertThat(listed.path("documents").get(0).path("doc_id").asText()).isEqualTo(docId);
        assertThat(readJson(list("p-other")).path("count").asInt()).isZero();
    }

    @Test
    void repeated_upload_is_deduplicated_by_hash() {
        byte[] content = "same document".getBytes(StandardCharsets.UTF_8);

        ResponseEntity<String> first = upload("p-rag-dedup", "same.txt", content, MediaType.TEXT_PLAIN);
        ResponseEntity<String> second = upload("p-rag-dedup", "same.txt", content, MediaType.TEXT_PLAIN);

        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode firstBody = readJson(first);
        JsonNode secondBody = readJson(second);
        assertThat(secondBody.path("duplicated").asBoolean()).isTrue();
        assertThat(secondBody.path("doc_id").asText())
                .isEqualTo(firstBody.path("doc_id").asText());
        assertThat(readJson(list("p-rag-dedup")).path("count").asInt()).isEqualTo(1);
        assertThat(chunkMapper.countByDocId(firstBody.path("doc_id").asText())).isEqualTo(1);
    }

    @Test
    void parse_failure_returns_400_and_retains_raw_file() {
        byte[] invalid = new byte[]{(byte) 0xC3, (byte) 0x28};

        ResponseEntity<String> response = upload("p-rag-parse", "broken.txt", invalid,
                MediaType.TEXT_PLAIN);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        JsonNode error = readJson(response).path("error");
        assertThat(error.path("message").asText()).contains("UTF-8");
        assertThat(error.path("type").asText()).isEqualTo("invalid_request_error");
        assertThat(error.path("param").asText()).isEqualTo("file");
        assertThat(error.path("code").asText()).isEqualTo("400");

        JsonNode listed = readJson(list("p-rag-parse"));
        assertThat(listed.path("count").asInt()).isEqualTo(1);
        String docId = listed.path("documents").get(0).path("doc_id").asText();
        assertThat(listed.path("documents").get(0).path("index_status").asText())
                .isEqualTo("FAILED");
        assertThat(listed.path("documents").get(0).path("chunk_count").asInt()).isZero();
        RagDocumentDO stored = documentMapper.selectByDocId(docId);
        assertThat(stored.getRawContent()).isEqualTo(invalid);
        assertThat(stored.getErrorMessage()).contains("UTF-8");
    }

    @Test
    void unsupported_type_returns_400() {
        ResponseEntity<String> response = upload("p-rag-type", "demo.xyz", new byte[]{1, 2},
                MediaType.APPLICATION_OCTET_STREAM);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(readJson(response).path("error").path("message").asText())
                .contains("不支持");
    }

    @Test
    void upload_docx_parses_and_becomes_ready() throws IOException {
        byte[] docx = docxWithParagraph("Word 文档正文");

        ResponseEntity<String> uploaded = upload("p-rag-docx", "demo.docx", docx,
                MediaType.APPLICATION_OCTET_STREAM);

        assertThat(uploaded.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        JsonNode body = readJson(uploaded);
        assertThat(body.path("source").asText()).isEqualTo("demo.docx");
        assertThat(body.path("content_type").asText()).contains("wordprocessingml");
        assertThat(body.path("index_status").asText()).isEqualTo("READY");
        assertThat(body.path("chunk_count").asInt()).isGreaterThanOrEqualTo(1);
        List<RagChunkDO> chunks = chunkMapper.selectByDocId(body.path("doc_id").asText());
        assertThat(chunks).isNotEmpty();
        assertThat(chunks.get(0).getContent()).contains("Word 文档正文");
    }

    @Test
    void upload_legacy_doc_returns_actionable_message() {
        ResponseEntity<String> response = upload("p-rag-doc", "legacy.doc", new byte[]{1, 2},
                MediaType.APPLICATION_OCTET_STREAM);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(readJson(response).path("error").path("message").asText())
                .contains("另存为 .docx");
    }

    @Test
    void delete_cascades_chunks_and_missing_returns_404() {
        ResponseEntity<String> uploaded = upload("p-rag-delete", "delete.txt",
                "delete me".getBytes(StandardCharsets.UTF_8), MediaType.TEXT_PLAIN);
        String docId = readJson(uploaded).path("doc_id").asText();
        assertThat(chunkMapper.countByDocId(docId)).isEqualTo(1);

        ResponseEntity<String> deleted = delete(docId, "p-rag-delete");
        assertThat(deleted.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = readJson(deleted);
        assertThat(body.path("deleted").asBoolean()).isTrue();
        assertThat(body.path("chunks_deleted").asInt()).isEqualTo(1);
        assertThat(documentMapper.selectByDocId(docId)).isNull();
        assertThat(chunkMapper.countByDocId(docId)).isZero();

        ResponseEntity<String> missing = delete(docId, "p-rag-delete");
        assertThat(missing.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(readJson(missing).path("error").path("code").asText()).isEqualTo("404");
    }

    @Test
    void rag_call_is_logged_with_call_type_and_zero_cost() {
        ResponseEntity<String> uploaded = upload("p-rag-log", "log.txt",
                "log me".getBytes(StandardCharsets.UTF_8), MediaType.TEXT_PLAIN);
        String requestId = uploaded.getHeaders().getFirst("X-Request-Id");

        CallLogDO log = awaitLog(requestId);
        assertThat(log.getCallType()).isEqualTo("RAG");
        assertThat(log.getTargetName()).isEqualTo("ingest_document");
        assertThat(log.getModelAlias()).isEqualTo("ingest_document");
        assertThat(log.getCost()).isEqualByComparingTo("0");
        assertThat(log.getStatus()).isEqualTo(200);
    }

    private byte[] docxWithParagraph(String text) throws IOException {
        try (XWPFDocument document = new XWPFDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            document.createParagraph().createRun().setText(text);
            document.write(output);
            return output.toByteArray();
        }
    }

    private ResponseEntity<String> upload(String projectId, String fileName, byte[] content,
                                          MediaType contentType) {
        MultiValueMap<String, Object> parts = new LinkedMultiValueMap<>();
        parts.add("file", new ByteArrayResource(content) {
            @Override
            public String getFilename() {
                return fileName;
            }
        });
        parts.add("project_id", projectId);
        return restClient.post()
                .uri("/v1/rag/documents")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .headers(headers -> headers.setBearerAuth(MASTER_KEY))
                .body(parts)
                .retrieve()
                .toEntity(String.class);
    }

    private ResponseEntity<String> list(String projectId) {
        return restClient.get()
                .uri(uriBuilder -> uriBuilder.path("/v1/rag/documents")
                        .queryParam("project_id", projectId).build())
                .headers(headers -> headers.setBearerAuth(MASTER_KEY))
                .retrieve()
                .toEntity(String.class);
    }

    private ResponseEntity<String> delete(String docId, String projectId) {
        return restClient.delete()
                .uri(uriBuilder -> uriBuilder.path("/v1/rag/documents/{docId}")
                        .queryParam("project_id", projectId).build(docId))
                .headers(headers -> headers.setBearerAuth(MASTER_KEY))
                .retrieve()
                .toEntity(String.class);
    }

    private JsonNode readJson(ResponseEntity<String> response) {
        try {
            return objectMapper.readTree(response.getBody());
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Response is not JSON: " + response.getBody(), ex);
        }
    }

    private CallLogDO awaitLog(String requestId) {
        for (int i = 0; i < 50; i++) {
            Optional<CallLogDO> log = callLogMapper.selectByRequestId(requestId);
            if (log.isPresent()) {
                return log.get();
            }
            try {
                Thread.sleep(50L);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        return callLogMapper.selectByRequestId(requestId).orElseThrow();
    }
}
