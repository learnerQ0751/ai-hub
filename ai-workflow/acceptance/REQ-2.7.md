# REQ-2.7 验收：网关统一 MCP 入口

- 状态：已验收（2026-11-04）
- 验收人：主人
- 需求记录：`../records/REQ-2.7.md`
- 需求来源：AI 中台架构对齐补强（2026-11-04）

## 1. 需求验收标准

- [x] 注册 mock_http/mock_stdio 后，MCP 客户端 `listTools()` 同时看到平台工具与 `{server}__{tool}` 聚合工具
- [x] `executeTool("{server}__{tool}")` 经网关路由到上游并返回结果
- [x] 平台工具行为不回归
- [x] `mvn -Pquality verify` 全绿

**验收命令 / 示例**：

```bash
curl -X POST http://localhost:18080/mcp \
  -H "Authorization: Bearer sk-local-master-key" -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":1,"method":"tools/list","params":{}}'
```

## 2. 从哪看起

1. 本文 §1
2. `records/REQ-2.7.md` §1-3
3. 核心文件：`McpServerEndpoint.java`（toolsListResult / toolsCallResult）

## 3. 实现要点与参考答案

### 3.1 三条关键实现要点

1. `tools/list` = 平台工具 + 工具目录快照工具（TTL 60s，注册/注销自动失效）
2. `tools/call` 按工具名分流：平台工具走内部服务，聚合工具走 MCP 调用代理（JSON-RPC 到上游）
3. 平台工具名无 `__`、Server 名禁 `__`，命名空间天然隔离

### 3.2 面试 1 分钟讲法

```text
网关的 MCP 入口不只暴露自己的三个平台工具，还把注册表里所有 MCP Server 的工具
动态聚合到 tools/list；Agent 只配一个 /mcp 地址就能看到并使用全部工具资产。
调用时按工具名路由：平台工具内部执行，聚合工具经代理到上游 Server。
```

### 3.3 自测题与参考答案

1. 工具目录过期时 tools/list 会怎样？
2. 聚合工具与平台工具会重名吗？
3. 聚合工具执行失败如何返回？

### 参考答案

1. `list()` 会触发刷新（TTL 60s），拿到最新快照。
2. 不会：平台工具无 `__`；Server 名禁止 `__`，聚合名必含 `__`。
3. 经 McpCallService 统一映射为 JSON-RPC error（NotFound/Timeout 等中文 message）。

### 3.4 亲手改的口子（可选）

- 任务：给聚合工具名增加可配置分隔符（当前 `__`）。
- 验证方法：改配置后 listTools 名称随之变化，调用仍可路由。

## 4. 回归影响

- 需重跑：REQ-2.6 平台工具 listTools/executeTool（已覆盖）
- 真实链路：待切换 ai-hub 环境后补演示

## 5. 验收记录

- 验收日期：2026-11-04
- 结论：理解验收默认通过
- 遗留问题：无
