# REQ-4.4 验收：管理端（模型/Key/Prompt/MCP/日志）

- 状态：待验收（2026-11-16 实现与自检完成；2026-11-18 管理台页面联调复核通过，见 §5）
- 验收人：主人
- 需求记录：`../records/REQ-4.4.md`

## 1. 需求验收标准

> 实现前冻结，唯一勾选点。

- [x] 管理 API：模型列表/部署状态、Key 列表/封禁/解封、日志查询；Prompt 发布/回滚与
      MCP Server 状态复用既有 master-only 接口
- [x] 统一管理鉴权：`/v1/admin/**` 仅 master；非管理员 403 `authorization_error`
- [x] 模型别名/部署实例 CRUD（创建、查询、更新、删除、部署新增/更新）
- [x] 项目级授权新增 API，授权后跨项目记忆可读写
- [x] 敏感字段脱敏：明文 Key、上游 api-key 永不返回；Key 哈希仅作不可逆标识
- [x] 分页：默认 50、上限 200、`has_more`
- [x] 管理动作写审计（REQ-4.5）
- [x] 页面不做（需求卡页面可选），以最小 API 交付
- [x] `mvn -Pquality verify` 全绿

**验收命令**：

```bash
# Key 列表/封禁
curl "http://localhost:18080/v1/admin/keys?tenant_id=tenant-a" -H "Authorization: Bearer $MASTER"
curl -X POST "http://localhost:18080/v1/admin/keys/<hash>/block" -H "Authorization: Bearer $MASTER"

# 模型 CRUD
curl -X POST http://localhost:18080/v1/admin/models -H "Authorization: Bearer $MASTER" \
  -H 'Content-Type: application/json' \
  -d '{"alias":"chat","deployments":[{"deployment_key":"chat#0","api_base":"https://api.example.com","api_key":"sk-xxx","upstream_model":"m1"}]}'

# 日志
curl "http://localhost:18080/v1/admin/logs?tenant_id=tenant-a&limit=20" -H "Authorization: Bearer $MASTER"
```

## 2. 从哪看起（固定 10 分钟）

1. 本文 §1（2 分钟）
2. `records/REQ-4.4.md` §1-3（3 分钟）
3. 核心文件（5 分钟）：`AdminController`、`AdminServiceImpl`、`AdminIntegrationTest`

## 3. 实现要点与参考答案（按需阅读）

### 3.1 三条关键实现要点

1. **鉴权收敛**：`/v1/admin/**` 由 AuthFilter 统一判断 master，虚拟 Key 403；管理接口不复用业务鉴权。
2. **脱敏边界**：只返回 `deployment_key/api_base/upstream_model/weight/priority/status`；
   `api_key_cipher` 与明文 Key 永不出现。
3. **可运营**：分页 + 租户/项目/模型/Key/状态/时间过滤 + 审计。

### 3.2 面试 1 分钟讲法

```text
管理端所有接口收在 /v1/admin 下，入口过滤器统一要求 master，虚拟 Key 直接 403。Key 列表和日志
都按租户/项目分页过滤；模型别名与部署实例从 YAML 迁到库里，可在管理 API 增删改，部署凭证只写
不读，响应里永远看不到 api-key。每个管理动作写一条审计，后续可追溯。
```

### 3.3 自测题与参考答案

1. 为什么管理接口不复用业务 Key 鉴权？
2. Key 哈希算敏感字段吗？为什么可以作为管理标识？
3. 为什么每页多取 1 条？
4. 模型配置改 DB 后如何保证多实例一致？
5. 管理动作与审计如何保证一致？

### 参考答案

1. 复用会导致业务 Key 拥有管理权限；管理面必须独立鉴权。
2. SHA-256 单向不可逆，无法还原明文；用于关联日志/审计是安全的。
3. 用 `limit + 1` 判 `has_more`，避免额外 count 查询。
4. 目录读路径每请求读库；写操作直接落库，多实例立即可见。
5. 本期在同一 Service 调用内写审计；后续可用事务消息/本地事件表保证强一致。

## 4. 亲手改口子（可选）

- 用虚拟 Key 调 `/v1/admin/keys` 验证 403。
- 创建别名后立即 `GET /v1/admin/models`，确认新配置生效。

## 5. 管理台页面联调复核（2026-11-18）

- **鉴权口径变更**：控制面端点统一收口到 `/v1/admin/**`，会话 cookie 只在该路径生效；
  `/v1/admin/**` 接受 master key 或会话 cookie，虚拟 Key 仍 403。
- **页面级能力**：模型页支持新建/编辑/删除别名（`93d07c8`），别名/租户/描述/状态/Fallback 链 +
  可选部署；价格只读推导，未配置显示「未配置」。Key 页支持新建（一次性明文）、分页与封禁/解封。
- **MCP 健康列**：后端状态为 `ACTIVE`/`DOWN`，前端归一为 健康/异常/未知（`bcacb38`）。
- **实测**：主控真实登录后 11 页全 200；模型 CRUD 新建→列表→编辑→删除全 200；同名再建 409。
- **未覆盖**：Playground 流式（J-01）、公网/HTTPS。
