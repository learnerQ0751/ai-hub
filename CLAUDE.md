# ai-hub（项目上下文）

> 项目 7 Java 重实现——LLM 网关核心。工作流：0→1 spec-kit；1→100 OpenSpec + Superpowers；归因 git-ai。

<!-- GENERATED:START -->
> 本区块由 `standards/generator/generate.py` 生成，请勿手改。完整规则库见 `standards/index.md`。

## 强制规则（项目 7 适用）

### L0 个人底线

- **rule-l0-collection-001** 方法返回集合或数组时，无数据必须返回空集合/空数组，禁止返回 null。（P1）
- **rule-l0-commit-001** 提交信息使用 feat/fix/refactor/docs/test/chore: 简述；禁止包含模型署名； 代码作者统一为 qtua。（P1）
- **rule-l0-config-001** 环境地址、端口、超时、模型名等可变参数必须外部化到配置文件或环境变量； 禁止在代码中硬编码。（P1）
- **rule-l0-exception-001** 捕获异常后必须处理或转换抛出；禁止空 catch、禁止仅 printStackTrace、 禁止捕获后不记录也不上抛。（P0）
- **rule-l0-gate-001** 每次提交前必须运行 mvn -Pquality verify（或项目等价命令）， 门禁失败不得提交。（P1）
- **rule-l0-log-001** 禁止在日志中打印 API Key、密码、完整令牌、身份证号、银行卡号等敏感字段； 必要时脱敏（如只保留后四位）。（P0）
- **rule-l0-scope-001** .gitignore 必须覆盖 application-local.yml、.env、日志、target/、 IDE 本地设置等；提交前检查 git status。（P1）
- **rule-l0-secret-001** 所有 API Key、数据库密码、令牌等敏感凭据只能通过环境变量或本地未提交配置文件注入； 禁止硬编码、禁止提交到 Git、禁止打印到日志或异常信息。（P0）
- **rule-l0-sql-001** 所有 SQL 必须使用参数化绑定（#{} / ? / JPA 参数），禁止字符串拼接用户输入。（P0）
- **rule-l0-test-001** 新增或修改 service 层方法时，必须同步新增单测，覆盖成功场景与关键异常场景 （参数校验、重复校验、状态校验等）。（P1）

### L1 通用 Java/Spring

- **rule-db-charset-001** MySQL/OceanBase 使用 utf8mb4 + utf8mb4_bin；PostgreSQL 使用 UTF8； 禁止混用字符集。（P1）
- **rule-java-dto-001** 每个 DTO 必须声明 private static final long serialVersionUID。（P2）
- **rule-java-equals-001** 对象相等比较必须用 equals 或 Objects.equals；禁止用 == 比较包装类型/字符串。（P1）
- **rule-java-layering-001** Controller → Service → Repository/DAL；禁止跨层调用、禁止反向依赖； 包依赖必须无环。（P1）
- **rule-java-logging-001** 统一使用 SLF4J 占位符日志；禁止 System.out/System.err、禁止字符串拼接日志。（P1）
- **rule-java-magic-001** 状态值使用枚举；其他常量定义在类级别 private static final；代码中禁止硬编码字符串/数字。（P1）
- **rule-java-methodlen-001** 单个方法（含注释和空行）不超过 70 行；超出须拆分为子方法。（P1）
- **rule-java-money-001** 金额、费用、价格计算必须使用 BigDecimal；禁止 double/float； 除法必须指定精度与舍入模式。（P0）
- **rule-java-naming-001** 类名 UpperCamelCase；方法/变量 lowerCamelCase；常量 UPPER_SNAKE_CASE； 包名小写；禁止拼音与无意义缩写。（P1）
- **rule-java-null-001** 禁止 == null / != null 判空；统一使用 ObjectUtils.isEmpty() / StringUtils.isBlank()。 MyBatis XML 的 if test 表达式除外。（P1）
- **rule-java-sql-update-001** UPDATE 操作必须使用业务主键（如 entity_code、key_id）定位， 不使用数据库自增 id；编辑入参必须做非空校验。（P1）
- **rule-java-threadpool-001** 必须使用 ThreadPoolExecutor 显式指定核心参数；禁止 Executors.newFixedThreadPool / newCachedThreadPool 等无界队列工厂方法。（P1）
- **rule-java-tx-001** @Transactional 只放在事务边界；rollbackFor 必须指定 Throwable； 非事务方法不得标注。（P1）
- **rule-sql-index-001** 高频查询必须走索引或分区键；上线前用 EXPLAIN 验证执行计划。（P1）
- **rule-sql-select-001** 查询必须显式列出所需字段；禁止 SELECT *。（P2）
- **rule-test-001** 单测必须覆盖方法的成功场景与关键异常场景（参数校验、重复校验、状态校验）； 测试命名表达意图。（P1）

### L2 项目 7 专属

- **rule-p7-alias-001** 请求中的 model 是用户侧别名；网关按配置映射到部署列表； 别名与上游真实模型名必须分别存储、不得混用。（P0）
- **rule-p7-cost-001** 模型价格必须在配置中显式声明（输入/输出单价，按 1M tokens 换算）； 禁止依赖内置价格表；spend 必须可按日志复算。（P1）
- **rule-p7-error-001** 错误响应统一为 {"error":{"message","type","param","code"}}； HTTP 状态码与 LiteLLM 基线一致（400/401/403/429/502/504）。（P0）
- **rule-p7-fallback-001** fallback 成功时响应 model 字段保留上游名还是统一改写为别名， 必须在需求决策记录中显式选择并写测试。（P1）
- **rule-p7-keyhash-001** 虚拟 Key 明文只在创建时返回一次；数据库只存哈希，禁止存明文； Auth 校验使用哈希比对。（P0）
- **rule-p7-master-001** 网关启动时必须校验 master key 已配置；缺失则拒绝启动并输出明确错误。（P0）
- **rule-p7-models-001** GET /models 只返回配置中的模型别名，不暴露上游真实模型名与 api_base/api_key。（P1）
- **rule-p7-openai-001** /chat/completions 与 /v1/chat/completions 返回 OpenAI ChatCompletion 结构； 上游响应的 id/object/model/choices/usage 等字段必须原样保留，禁止因序列化丢字段。（P0）
- **rule-p7-ratelimit-001** 内存版 rpm/tpm 滑动窗口计数必须加锁或使用原子结构； 预留 Redis 接口供二期替换。（P1）

<!-- GENERATED:END -->

## 工作流入口

- **0→1（新模块）**：Claude Code 中 `/speckit-specify → /speckit-clarify → /speckit-plan → /speckit-tasks → /speckit-analyze → /speckit-implement`
- **1→100（存量迭代）**：Cursor 中 `/opsx:explore → /opsx:propose → /opsx:apply → /opsx:archive`
- **规范维护**：编辑 `standards/rules/*.yaml` → `python standards/generator/validate.py` → `python standards/generator/generate.py`
- **质量门禁**：`mvn -Pquality verify`
- **归因**：`git-ai stats --json` / `git-ai blame <file>`
- **详细指南**：`docs/WORKFLOW.md`

## 项目约定

- Java 21 / Spring Boot 3.3.11 / Maven；默认端口 18080
- 包结构：`config` / `gateway` / `filter` / `service`
- 密钥只放 `application-local.yml`（gitignored）或环境变量
- 提交信息：`feat/fix/refactor/docs/test/chore: 简述`；禁止模型署名
- 代码作者：qtua
- 硬门禁：`mvn -Pquality verify`
- 当前进度：需求 1.1 已完成；下一步需求 1.2（模型路由）
