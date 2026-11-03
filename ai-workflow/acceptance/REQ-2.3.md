# REQ-2.3 验收：MCP 调用代理

- 状态：已验收（2026-11-03）
- 验收人：主人
- 需求记录：`../records/REQ-2.3.md`
- 需求来源：外部需求卡 REQ-2.3

## 1. 需求验收标准

> 实现前冻结，唯一勾选点；实现完成后只补 §2-4，不改本节标准。

- [x] mock 工具调用成功，结果原样返回（`POST /v1/mcp/tools/{server}__{tool}:call`）
- [x] 工具不存在 → 404；Server 超时 → 504；参数错误 → 400（OpenAI 错误体、中文 message）
- [x] 调用产生 `call_logs` 记录：`call_type=MCP`、`target_name=工具名`、cost=0
- [x] 复用虚拟 Key RPM 限流（超限 429）
- [x] >1MB 结果截断并记录原始大小
- [x] 幂等工具失败可重试一次（非幂等不重试）
- [x] `mvn -Pquality verify` 全绿

> 涉及对外错误/提示时，必须增加：`error.message` 为中文、`type`/`param`/`code`/HTTP 状态码保持不变（STANDARDS §3.6）。

**验收命令 / 示例**：

```bash
curl -X POST "http://localhost:18080/v1/mcp/tools/mock_http__echo:call" \
  -H "Authorization: Bearer sk-local-master-key" -H "Content-Type: application/json" \
  -d '{"arguments":{"message":"hi"}}'
```

## 2. 从哪看起（固定 10 分钟）

1. 本文 §1（2 分钟）
2. `records/REQ-2.3.md` §1-3 人向说明（3 分钟）
3. `DESIGN.md` 架构图本次增量标注 + 核心文件 ≤3（5 分钟）

## 3. 实现要点与参考答案（按需阅读）

### 3.1 三条关键实现要点

1. **治理复用**：`GatewayGovernanceFilter` 识别 `:call` 路径并设置 `callType=MCP`；治理服务按类型分支只做 Key 级 RPM，日志 `call_type=MCP`、`target_name`、cost=0——没有为新接口复制任何治理代码。
2. **错误归一**：工具/Server 不存在 404、超时 504、arguments 非对象 400、其他上游失败 502，全部 OpenAI 错误体 + 中文 message。
3. **结果保护**：>1MB 截断为包装结构（含 `truncated`/`originalSize`）；仅 `_meta.idempotent=true` 的工具失败重试一次。

### 3.2 面试 1 分钟讲法（骨架）

```text
业务不直连 MCP Server，而是走网关：/v1/mcp/tools/{server}__{tool}:call。
路由链路：工具目录找到原始名与 Server → JSON-RPC tools/call → 结果原样返回。
治理接入是重点：复用同一个 GatewayGovernanceFilter，通过 callType=MCP 分支只做 RPM，
日志记 call_type/target_name、cost=0——新增接口没有复制治理逻辑。
保护策略：>1MB 截断；只有幂等工具才允许失败重试一次，避免重复副作用。
```

### 3.3 自测题与参考答案

1. MCP 调用为什么不做 TPM/预算？
2. 非幂等工具为什么不能重试？
3. 大结果截断的阈值怎么确定？
4. `target_name` 和 `model_alias` 都记工具名，是否冗余？
5. 新增 MCP 调用接口时，治理层需要改什么？

### 参考答案

1. 工具调用不消耗 LLM Token、无模型费用；预算语义是模型费用，套用无意义。
2. 非幂等工具（如下单）重试可能产生重复副作用；只有显式声明幂等的工具才安全。
3. 按内存/响应体安全边界取 1MB；客户端缓冲上限 8MB 留出检测余量；原始大小记录在响应与日志。
4. 不冗余：`model_alias` 沿用日志表既有维度（RPM key+model 维度复用），`target_name` 表达调用目标语义，后续审计按 call_type 过滤。
5. 无需改治理代码：新接口只要加入 Filter 的路径识别并设置 callType/target，治理流程自动复用（接口无关）。

### 3.4 亲手改的口子（可选）

- 任务：把工具级超时从 3s 改为可配置（`gateway.mcp.tool-timeout`）。
- 验证方法：慢工具在默认 3s 下 504；调大后成功。

## 4. 回归影响

- 需重跑的已有验收项：REQ-1.4 限流（Filter 扩路径）、REQ-1.5 日志（CallLogBO 加字段）、REQ-2.2 工具目录
- 真实链路回归：chat 链路日志 call_type 默认 CHAT 正常；MCP 调用 200/404/400 留痕正常

## 5. 验收记录

- 验收日期：2026-11-03
- 结论：理解验收默认通过（如需深挖看 §3）
- 遗留问题：无
