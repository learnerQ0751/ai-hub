# REQ-3.4 验收：RAG 生成 + 引用溯源

- 状态：待验收（2026-11-11，实现与自检完成）
- 验收人：主人
- 需求记录：`../records/REQ-3.4.md`
- 需求来源：外部需求卡 `12-项目7-第3期-RAG与记忆服务.md` REQ-3.4

## 1. 需求验收标准

> 实现前冻结，唯一勾选点；实现完成后只补 §2-4，不改本节标准。

- [x] `POST /v1/rag/answer` 接收 `query`、`project_id`、`model`、可选 `top_k`/`min_score`/`filters`/`rerank`/
      `allow_free_answer`；返回 `answer`、`citations[]`、`no_hit`、`free_answer`、`usage`、`model`
- [x] 已知问题答案正确且 citations 指向正确文档：以已知语料上传后提问，`citations` 首个来源为期望文档，
      `doc_id`/`chunk_id` 与检索命中一致；citations 的 `index` 从 1 开始，与 prompt 中 `[1][2]` 一一对应
      （集成测试校验上游实际收到的 prompt）
- [x] 无召回：返回 HTTP 200 + `no_hit=true` + `citations=[]` + 兜底话术，**不调用模型**；
      兜底话术可通过 `gateway.rag.answer.fallback-message` 配置
- [x] 自由回答：默认关闭；服务端 `gateway.rag.answer.allow-free-answer=true` 且请求 `allow_free_answer=true`
      时，无召回仍调用模型，响应 `free_answer=true` 且 `citations=[]`（不编造引用）；请求不能越过服务端关闭开关
- [x] 检索能力复用 REQ-3.3：`project_id` 隔离、`top_k`/`min_score`/`filters`/`rerank` 语义与 `/v1/rag/search` 一致
- [x] prompt 受 `gateway.rag.answer.max-context-chars` 约束：超长时按顺序少放引用，citations 只返回实际使用的条目
- [x] 模型返回空内容：answer 使用可配置的兜底话术，citations 与 usage 如实返回
- [x] 全链路日志：同一 `request_id` 记录 query、召回 id+score、prompt 长度/摘要、模型 usage/耗时、引用清单；
      不记录 prompt 全文与切片正文
- [x] 评测集入口：`ai-workflow`/测试资源下的黄金问题集 YAML + 可运行回归测试入口，覆盖“应命中”和“应无命中”两类问题
- [x] RAG 调用登记 `call_logs`：`call_type=RAG`、`target_name=rag_answer`、`cost=0`；内部 chat 不经过 Filter，
      不产生第二条治理日志；既有 CHAT/MCP/PROMPT/MEMORY/RAG 分支回归通过
- [x] 对外错误 `error.message` 为中文，`type` / `param` / `code` / HTTP 状态码保持英文/标准值（STANDARDS §3.6）
- [x] 非功能验收：无新增线程池/连接池；prompt 有界；多实例无本地业务状态（STANDARDS §2.7）
- [x] `mvn -Pquality verify` 全绿（含既有基线测试）；模块依赖方向符合 ArchUnit

**验收命令 / 示例**：

```bash
# 上传已知语料（见 REQ-3.1），等待 index_status=READY
curl -X POST http://localhost:18080/v1/rag/documents \
  -H "Authorization: Bearer sk-local-master-key" \
  -F "file=@E:/tmp/rag-demo.md" -F "project_id=p-demo"

# 有召回
curl -X POST http://localhost:18080/v1/rag/answer \
  -H "Authorization: Bearer sk-local-master-key" \
  -H "Content-Type: application/json" \
  -d '{"query":"年假有多少天","project_id":"p-demo","model":"chat-default","top_k":4}'

# 无召回（默认兜底，不调模型）
curl -X POST http://localhost:18080/v1/rag/answer \
  -H "Authorization: Bearer sk-local-master-key" \
  -H "Content-Type: application/json" \
  -d '{"query":"火星基地建设","project_id":"p-demo","model":"chat-default"}'

# 黄金问题集回归（离线，H2 + 确定性 embedding + mock 上游）
mvn -pl ai-hub-tests -am test -Dtest=RagGoldenQuestionRegressionTest
```

## 2. 从哪看起（固定 10 分钟）

1. 本文 §1（2 分钟）
2. `records/REQ-3.4.md` §1-3 人向说明（3 分钟）
3. 核心文件（5 分钟）：`RagAnswerServiceImpl`、`RagPromptBuilder`、`RagAnswerPrompt`

## 3. 实现要点与参考答案（按需阅读）

### 3.1 三条关键实现要点

1. 检索复用、生成分层：`RagAnswerServiceImpl` 把 answer 参数转成检索请求交给 `RagSearchService`；
   检索结果只用于构造 prompt 与 citations，生成通过内部 `ChatCompletionService` 完成，不经 HTTP/Filter。
2. 引用强对应与预算：`RagPromptBuilder` 按命中顺序分配 `[1][2]`，prompt 中只出现实际使用的引用；
   citations 同序、`index` 从 1 开始，按 `max-context-chars` 控制上下文长度。
3. 兜底与可观测：无召回默认不调模型，返回可配置兜底话术；自由回答需服务端开关 + 请求显式开启，
   且 `citations=[]`；同一 request_id 记录 query/召回/prompt 摘要/usage/引用的全链路日志，不落正文。

### 3.2 面试 1 分钟讲法（骨架）

```text
RAG 生成不是把检索和模型拼在一起，而是把引用做成契约。检索复用 REQ-3.3 的服务，
拿到命中后 prompt builder 按顺序分配 [1][2]，并且只把真正放进 prompt 的资料放进 citations，
编号严格对应。上下文有长度预算，超了宁可少放资料。无召回默认不调模型，返回兜底话术，
不编造引用；只有服务端开关和请求同时允许时才自由回答，并且 citations 仍然为空。
内部 chat 走 service 调用，不再经过治理 Filter，避免一次请求被限流/计量两次。
同 request_id 记录 query、召回、prompt 摘要、usage 和引用，能还原整条链路，但不落正文。
```

### 3.3 自测题与参考答案

1. citations 的编号为什么必须和 prompt 里的 `[n]` 对应？怎么保证？
2. 无召回时为什么默认不调用模型？自由回答为什么要两层开关？
3. prompt 超长如何处理？为什么 citations 只返回实际使用的条目？
4. 为什么内部 chat 不经过 GatewayGovernanceFilter？
5. 全链路日志如何做到“可还原”又不泄露文档内容？

### 参考答案

1. 模型按 `[n]` 引用资料，业务按 index 回查文档；编号错位会导致引用指向错误文档。实现上只用一个列表
   同时生成 prompt 编号和 citations，prompt 构造完成后才返回实际使用的引用，二者天然同序。
2. 无召回时让模型自由发挥会编造答案和引用，企业场景不可接受；自由回答需要配置层（运维负责）与请求层
   （业务显式声明）同时允许，防止单个调用方绕过风险控制。
3. 按检索顺序累加资料长度，超过 `max-context-chars` 停止加入后续资料，至少保留第一条；citations 只包含
   真正进入 prompt 的切片，避免“返回了但没用上”的假引用。
4. 外层 Filter 已按 RAG 入口做一次 RPM 与 call_logs；内部 chat 若再经过 Filter 会重复限流/计量，
   与 REQ-2.5 Prompt chat 的处理一致。
5. 日志只记 query、chunk_id/score、prompt/上下文长度与 hash、usage、耗时和引用编号，不记 prompt 与正文；
   同 request_id 串联各阶段，即可按需回到数据库核对原文。

### 3.4 亲手改的口子（可选）

- 任务：把 `gateway.rag.answer.max-context-chars` 调小（如 100），用长文档提问，观察 citations 数量变化。
- 验证方法：对比响应 `citations.length` 与日志中的 `contextChars`，确认只纳入实际使用的切片。

## 4. 回归影响

- 需重跑的已有验收项：REQ-3.1（文档接入）、REQ-3.2（向量存储）、REQ-3.3（检索）、REQ-1.1（chat）、
  REQ-1.4/1.5（治理与日志）、REQ-2.3/2.5/3.5（call_type 分支）。

## 5. 验收记录

- 验收日期：2026-11-11（自检）
- 结论：实现完成，`mvn -Pquality verify` 全绿（369 个测试、0 失败）；新增 5 个 prompt 测试、9 个 service 测试、
  7 个 H2 端到端测试与 1 个黄金问题集回归；端到端覆盖引用指向、prompt 编号、无召回不调模型、
  自由回答双开关、空答案兜底、filters 传递、中文错误与 `rag_answer` 调用日志。
- 真实链路：pgvector 容器 + 真实 all-minilm embedding + DeepSeek `chat-default` 验证通过：
  年假问题引用 demo-hr.md、差旅问题引用 demo-fin.md；无召回返回兜底且 `usage=0`；服务端自由回答关闭时
  请求无法越权；同 request_id 的日志与 `call_logs`（RAG/rag_answer/cost=0）可还原全链路。
- 遗留问题：离线黄金问题集为保证确定性 embedding 可重复命中，使用“问题 = 目标切片文本”；
  真实模型下 all-minilm 对无关中文文本的余弦约 0.47–0.59，需要按业务语料标定 `min_score`（演示阈值 0.6）。
  master 理解验收待确认。
