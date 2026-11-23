# REQ-2.5 验收：Prompt 渲染接入网关

- 状态：已验收（2026-11-03）
- 验收人：主人
- 需求记录：`../records/REQ-2.5.md`
- 需求来源：外部需求卡 REQ-2.5

## 1. 需求验收标准

> 实现前冻结，唯一勾选点；实现完成后只补 §2-4，不改本节标准。

- [x] 渲染含变量模板 → 输出正确 messages
- [x] `/chat` 端到端返回模型响应；日志记录 prompt name/version
- [x] 变量缺失/版本不存在 → 400/404
- [x] `mvn -Pquality verify` 全绿

> 涉及对外错误/提示时，必须增加：`error.message` 为中文、`type`/`param`/`code`/HTTP 状态码保持不变（STANDARDS §3.6）。

**验收命令 / 示例**：

```bash
curl -X POST http://localhost:18080/v1/prompts/greet2/render \
  -H "Authorization: Bearer sk-local-master-key" -H "Content-Type: application/json" \
  -d '{"variables":{"name":"御风监管平台"}}'
curl -X POST http://localhost:18080/v1/prompts/greet2/chat \
  -H "Authorization: Bearer sk-local-master-key" -H "Content-Type: application/json" \
  -d '{"model":"chat-default","variables":{"name":"御风监管平台"}}'
```

## 2. 从哪看起（固定 10 分钟）

1. 本文 §1（2 分钟）
2. `records/REQ-2.5.md` §1-3 人向说明（3 分钟）
3. `DESIGN.md` 架构图本次增量标注 + 核心文件 ≤3（5 分钟）

## 3. 实现要点（按需阅读）

### 3.1 三条关键实现要点

1. **渲染与调用分离**：`/render` 只做变量替换 + System/User/Assistant 消息解析，不调用模型；`/chat` 才渲染后走内部 chat 链路。
2. **单次治理**：Filter 识别 Prompt chat 统一预占/结算/计价/日志；内部 `ChatCompletionService` 调用不经 HTTP/Filter，避免双路径重复扣费。
3. **版本可追溯**：Controller 用实际渲染版本回填 `target_name=name@vN`，日志能定位到具体模板版本。

### 3.2 面试 1 分钟讲法（骨架）

```text
Prompt 中心不只存模板，还要能真正调用：/render 只渲染出 messages（便于预览和组装），
/chat 则渲染后走内部 chat 链路。
治理关键：外层 Filter 统一做一次 RPM/TPM/预算/计价/日志，内部调用不经过 HTTP/Filter，
不会重复扣费；日志 target_name 精确到模板版本（name@vN）。
```

## 4. 回归影响

- 需重跑的已有验收项：AuthFilter Prompt 数据面规则、GatewayGovernanceFilter 路径分类、chat 链路
- 真实链路回归：chat 正常；Prompt chat 日志/计量正常

## 5. 验收记录

- 验收日期：2026-11-03
- 结论：理解验收默认通过（如需深挖看 §3）
- 遗留问题：无（流式 Prompt chat 未实现，留作扩展）
