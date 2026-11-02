# REQ-1.3：虚拟 Key

- 日期：2026-10-22
- 状态：已完成
- 关联规格：`specs/003-virtual-key/`
- 依赖需求：REQ-1.2
- 需求来源：Vault `05-项目7-第1期-LLM网关核心.md` §REQ-1.3
- 接口：POST /key/generate, GET /key/info, POST /key/block, POST /key/unblock
- 提交：881b844

## 1. 是什么

master key 只能本地全权使用；本需求提供可分发、可限权、可封禁的虚拟 Key——生成时返回一次明文 `sk-`，库中只存 SHA-256；调用时按 Key 的模型白名单授权；支持封禁/解封与 spend 查询。

## 2. 有什么用

- 业务价值：多租户与计量的基础；Key 是后续限流、预算、日志、审计的维度。
- 对应参照行为：B-05 生成（`sk-`、models 白名单、duration→expires）、B-06 授权调用 200、B-07 未授权模型 403 `key_model_access_denied`、B-08 `/key/info` spend、B-09 block 后 401 `auth_error`。
- 面试叙事点：为什么只存哈希、为什么白名单在 core、Key 泄露后的止损路径。

## 3. 怎么实现的

- 分层落点：`dal`（api_keys 表/MyBatis）→ `manager`（BO↔DO + JSON）→ `api`（KeyService + DTO）→ `service`（生成/鉴权/查询/封禁）→ `core`（白名单）→ `start`（AuthFilter + KeyController）。详细规格见 `specs/003-virtual-key/`。
- 核心类：`KeyServiceImpl`、`AuthFilter`、`ApiKeyMapper`。
- 请求链路：

```text
聊天：AuthFilter（master 或 SHA-256 查 api_keys；不存在/封禁/过期 → 401；/key/* 仅 master）
→ Controller（携带 KeyAuthDTO）→ Service → Core（白名单 403 → 选部署 → 改写 → 上游 → 回写别名）
Key 管理：KeyController → KeyService → ApiKeyManager → MyBatis Mapper（JSONB）
```

## 4. 影响面

```yaml
modules:
  add: [dal 数据访问实现, manager ApiKey 部分]
  change: [api, service, core, start, common]
interfaces:
  add: ["POST /key/generate", "GET /key/info", "POST /key/block", "POST /key/unblock"]
  change: ["POST /v1/chat/completions（鉴权扩展 + 白名单）", "POST /chat/completions（同上）"]
  remove: []
tables:
  add: [api_keys]
  change: []
config: [spring.datasource, spring.sql.init, gateway.master-key]
depends_on: [REQ-1.2]
compat: extension
gate: confirm        # 回溯标注：扩展了已有鉴权行为，按新规则本应事先确认
regression: [REQ-1.1 聊天链路, REQ-1.2 /models 与路由]
```

## 5. 关键决策

| 决策 | 理由 | 日期 |
|---|---|---|
| `dal` 用 MyBatis（`mybatis-spring-boot-starter` 4.1.0） | 遵守 `STANDARDS.md` §5/§6：持久层统一 MyBatis | 2026-10-22 |
| `models` 用 PostgreSQL `JSONB`；测试用 H2 PostgreSQL 模式 + `JSONB` 域 | 满足需求卡字段类型；质量门禁不依赖 Docker | 2026-10-22 |
| H2 中 `JSONB` 域映射为 `VARCHAR` | H2 的 JSON 类型会双重编码文本 | 2026-10-22 |
| 白名单校验放 core | 授权是业务规则；Controller 只做契约，Service 只做 DTO↔BO | 2026-10-22 |
| 先判授权、后判未知模型 | 虚拟 Key 越权统一 403，避免用 400/403 差异探测模型目录 | 2026-10-22 |
| `/key/*` 全部仅 master 可访问 | 管理面与数据面分离 | 2026-10-22 |
| `models` 为空表示不限制模型 | 对齐 LiteLLM | 2026-10-22 |
| 明文 Key 仅生成时返回一次，库中只存 SHA-256 | 降低泄露风险；高熵随机值无需加盐 | 2026-10-22 |
| 无 master key 时进入本地放行模式 | 保持 1.1/1.2 本地开发行为；生产用环境变量强制鉴权 | 2026-10-22 |
| `/key/info` 返回掩码 Key | 服务端不保存明文，只回显后 4 位 | 2026-10-22 |

## 6. 执行摘要

### 2026-10-22

- 实现 dal/manager/api/service/core/start 六层落点 + 端到端集成测试；总测试 88 个，`mvn -Pquality verify` 全绿（Checkstyle 0 / SpotBugs 0 / ArchUnit / JaCoCo ≥70%）。
- 按 `STANDARDS.md` 纠正持久层 JdbcClient → MyBatis；踩坑 H2 JSONB 双重编码（测试域改 VARCHAR）。
- 下一步：按 `acceptance/REQ-1.3.md` 完成理解验收并提交。

### 2026-10-29

- 引入 Flyway 统一数据库迁移：`db/migration/V1__api_keys.sql`；依赖用 Spring Boot 4 的 `spring-boot-starter-flyway` + `flyway-database-postgresql`（仅 flyway-core 不会触发 Spring Boot 4 自动配置）；生产 `spring.sql.init` 关闭、启动自动迁移（`baseline-on-migrate=true` 兼容已有本地库）；测试 H2 保持 `spring.sql.init`、`spring.flyway.enabled=false`。实测：应用启动自动创建 `api_keys` 与 `flyway_schema_history`，V1 success。
- `docs/DATA-MODEL.md` 成为表速查语义真源；STATUS 只显示已建表。

### 2026-10-30

- 模型目录收口：新增 `common/catalog`（`ModelCatalog` / `ModelDeployment`）与 `manager/catalog/YamlModelCatalog`；`core`/`service`/`integration` 不再依赖 `GatewayProperties`，未来切换数据库实现只改目录实现。
- 权重语义归一到 `ModelDeployment.weight`（YAML 映射时按 rpm→tpm→1 计算）；白名单仍按别名字符串校验，后续迁移关联表时再替换。
- 验证：`mvn -Pquality verify` 通过；真实链路 `/v1/models` 与 chat 正常（响应 model 回写别名）。
- 团队决策（2026-10-30）：rpm/tpm 分层（部署 weight / Key 限流）、fallback + 主备、api-key AES-256-GCM、prod master key fail-fast。已落地 `MasterKeyStartupValidator`：prod 空 master key 启动失败（实测 exit 1），local 放行 + WARN。

## 7. 复盘

- 做对了：先定管理面/数据面与授权顺序；H2 + MockWebServer 覆盖验收场景，不依赖 Docker/真实上游。
- 踩坑：H2 与 PostgreSQL 的 JSONB 文本语义不同；SpotBugs record 集合字段告警；Spring Boot 4 测试切片与 Jackson 2 混用。
- 下次改进：Testcontainers 真实 PostgreSQL 冒烟；REQ-1.5 的 spend 累加用原子 SQL 防并发丢更新。

## 8. 多实例检查

- [x] 无可变成员变量：`KeyServiceImpl`/`AuthFilter` 只持有不可变依赖
- [x] Key 状态外置 PostgreSQL，封禁/过期每次请求实时查询
- [x] 无本地锁；本期无分布式锁需求
- [x] 无定时任务；无本地缓存承载业务数据
- [x] 生产建表建议改用 Flyway/Liquibase（多实例并发 DDL 由迁移工具串行化）

## 归因（可选）

- 提交：待提交
- AI/人工行数：待统计
