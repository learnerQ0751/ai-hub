# REQ-2.8 验收：chat 入口支持 MCP 工具自动调用

- 状态：已验收（2026-11-04）
- 验收人：主人
- 需求记录：`../records/REQ-2.8.md`
- 需求来源：AI 中台架构对齐补强（2026-11-04）

## 1. 需求验收标准

- [x] 请求含 `tools:[{"type":"mcp","server_name":"mock_srv"}]` 时，模型可自动调用 MCP 工具
- [x] 最终响应为模型总结结果；`call_logs` 记录 usage（各轮累计）
- [x] 流式 + mcp：200 SSE，tool_calls 工具名为 `{server}__{tool}`（形态 A，不再 400）
- [x] 无工具调用时行为与普通 chat 一致
- [x] `mvn -Pquality verify` 全绿

**验收命令 / 示例**：

```bash
curl -X POST http://localhost:18080/v1/chat/completions \
  -H "Authorization: Bearer sk-local-master-key" -H "Content-Type: application/json" \
  -d '{"model":"chat-default","messages":[{"role":"user","content":"hi"}],
       "tools":[{"type":"mcp","server_name":"mock_http"}]}'
```

## 2. 从哪看起

1. 本文 §1
2. `records/REQ-2.8.md` §1-3
3. 核心文件：`ChatMcpServiceImpl.java`、`ChatCompletionController.java`（hasMcpTools 分流）

## 3. 实现要点

### 3.1 三条关键实现要点

1. mcp 声明展开为 OpenAI function tools（name=`{server}__{tool}`，parameters=inputSchema）
2. 模型 tool_calls → 网关执行 MCP 工具 → 回填 tool 消息 → 再调模型（上限 3 轮）
3. usage 各轮累计；外层 Filter 仍只治理一次（一次计价/结算）

### 3.2 面试 1 分钟讲法

```text
业务项目在 chat 请求里声明要用的 MCP Server，网关把它展开成 function tools；
模型决定调用哪个工具后，网关执行 MCP 工具并把结果回填，继续对话直到模型给出最终回答。
用法上和 OpenAI function calling 完全一致，只是工具来自中台注册的 MCP 资产。
```

## 4. 回归影响

- 需重跑：普通 chat（无 mcp 声明）、流式链路
- 集成测试已覆盖普通 chat 不回归

## 5. 验收记录

- 验收日期：2026-11-04
- 结论：理解验收默认通过
- 遗留问题：形态 B（服务端流式自动循环）未实现，留作增强；形态 A 已支持
