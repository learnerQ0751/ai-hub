# 规范规则库索引（自动生成）

> 由 `standards/generator/generate.py` 生成；唯一真源为 `standards/rules/*.yaml`，请勿手改本文件。

## 统计

| 层 | 数量 |
|---|---|
| L0 | 10 |
| L1 | 25 |
| L2 | 10 |
| **合计** | **45** |

## L0

### rule-l0-collection-001 · 禁止返回 null 集合或数组

- **级别**：must
- **类别**：null-safety
- **执行**：manual / review
- **项目 7 适用**：是
- **规则**：方法返回集合或数组时，无数据必须返回空集合/空数组，禁止返回 null。

### rule-l0-commit-001 · 提交信息规范与作者纪律

- **级别**：must
- **类别**：process
- **执行**：manual / review
- **项目 7 适用**：是
- **规则**：提交信息使用 feat/fix/refactor/docs/test/chore: 简述；禁止包含模型署名； 代码作者统一为 qtua。

### rule-l0-config-001 · 配置外部化，禁止硬编码环境地址

- **级别**：must
- **类别**：config
- **执行**：manual / review
- **项目 7 适用**：是
- **规则**：环境地址、端口、超时、模型名等可变参数必须外部化到配置文件或环境变量； 禁止在代码中硬编码。

### rule-l0-exception-001 · 禁止静默吞异常

- **级别**：must
- **类别**：exception
- **执行**：auto / checkstyle
- **项目 7 适用**：是
- **规则**：捕获异常后必须处理或转换抛出；禁止空 catch、禁止仅 printStackTrace、 禁止捕获后不记录也不上抛。

### rule-l0-gate-001 · 提交前必须通过硬门禁

- **级别**：must
- **类别**：process
- **执行**：manual / review
- **项目 7 适用**：是
- **规则**：每次提交前必须运行 mvn -Pquality verify（或项目等价命令）， 门禁失败不得提交。

### rule-l0-log-001 · 日志不得打印敏感信息

- **级别**：must
- **类别**：logging
- **执行**：manual / review
- **项目 7 适用**：是
- **规则**：禁止在日志中打印 API Key、密码、完整令牌、身份证号、银行卡号等敏感字段； 必要时脱敏（如只保留后四位）。

### rule-l0-scope-001 · 不提交本地配置、密钥与临时文件

- **级别**：must
- **类别**：process
- **执行**：manual / review
- **项目 7 适用**：是
- **规则**：.gitignore 必须覆盖 application-local.yml、.env、日志、target/、 IDE 本地设置等；提交前检查 git status。

### rule-l0-secret-001 · 密钥与敏感凭据不入库、不进代码、不进日志

- **级别**：must
- **类别**：security
- **执行**：manual / review
- **项目 7 适用**：是
- **规则**：所有 API Key、数据库密码、令牌等敏感凭据只能通过环境变量或本地未提交配置文件注入； 禁止硬编码、禁止提交到 Git、禁止打印到日志或异常信息。

### rule-l0-sql-001 · 禁止拼接 SQL，必须参数化

- **级别**：must
- **类别**：sql
- **执行**：manual / review
- **项目 7 适用**：是
- **规则**：所有 SQL 必须使用参数化绑定（#{} / ? / JPA 参数），禁止字符串拼接用户输入。

### rule-l0-test-001 · 新增或修改 service 方法必须有单测

- **级别**：must
- **类别**：testing
- **执行**：auto / jacoco
- **项目 7 适用**：是
- **规则**：新增或修改 service 层方法时，必须同步新增单测，覆盖成功场景与关键异常场景 （参数校验、重复校验、状态校验等）。

## L1

### rule-db-charset-001 · 字符集统一

- **级别**：must
- **类别**：database
- **执行**：manual / review
- **项目 7 适用**：是
- **规则**：MySQL/OceanBase 使用 utf8mb4 + utf8mb4_bin；PostgreSQL 使用 UTF8； 禁止混用字符集。

### rule-db-table-001 · 表命名、注释与字段数限制

- **级别**：should
- **类别**：database
- **执行**：manual / review
- **项目 7 适用**：是
- **规则**：表名必须小写、必须有注释；单表字段数不超过 50；表名/字段名使用业务语义。

### rule-java-collection-001 · 集合初始化容量与遍历规范

- **级别**：should
- **类别**：collection
- **执行**：manual / review
- **项目 7 适用**：是
- **规则**：已知容量的集合初始化时指定容量；遍历时禁止在循环中调用 size()； 禁止在 foreach 中删除元素。

### rule-java-dto-001 · DTO 必须声明 serialVersionUID

- **级别**：must
- **类别**：dto
- **执行**：auto / pmd
- **项目 7 适用**：是
- **规则**：每个 DTO 必须声明 private static final long serialVersionUID。

### rule-java-equals-001 · 对象比较使用 equals / Objects.equals

- **级别**：must
- **类别**：correctness
- **执行**：auto / checkstyle
- **项目 7 适用**：是
- **规则**：对象相等比较必须用 equals 或 Objects.equals；禁止用 == 比较包装类型/字符串。

### rule-java-exception-flow-001 · 不要用异常控制流程

- **级别**：should
- **类别**：exception
- **执行**：manual / review
- **项目 7 适用**：是
- **规则**：禁止用异常做正常分支控制；异常必须转换为业务异常并保留原始 cause。

### rule-java-interface-comment-001 · Service 接口注释格式

- **级别**：should
- **类别**：comment
- **执行**：manual / review
- **项目 7 适用**：否
- **规则**：Service 接口类必须按 @api/@ClassName/@Author/@date/@desc 格式注释； @Author 填实际开发人。

### rule-java-jdbctype-001 · MyBatis XML 字符串字段必须加 jdbcType

- **级别**：must
- **类别**：sql
- **执行**：auto / custom
- **项目 7 适用**：否
- **规则**：result 与 if test 中的字符串字段必须加 jdbcType=VARCHAR； 时间字段 TIMESTAMP；id 字段 BIGINT。

### rule-java-layering-001 · 分层调用规则

- **级别**：must
- **类别**：layering
- **执行**：auto / archunit
- **项目 7 适用**：是
- **规则**：Controller → Service → Repository/DAL；禁止跨层调用、禁止反向依赖； 包依赖必须无环。

### rule-java-logging-001 · 使用 SLF4J，禁止 System.out

- **级别**：must
- **类别**：logging
- **执行**：auto / checkstyle
- **项目 7 适用**：是
- **规则**：统一使用 SLF4J 占位符日志；禁止 System.out/System.err、禁止字符串拼接日志。

### rule-java-logical-delete-001 · 逻辑删除方法命名与状态参数

- **级别**：should
- **类别**：sql
- **执行**：manual / review
- **项目 7 适用**：否
- **规则**：逻辑删除方法命名 updateStatusByXXX(code, operator, fromStatus, toStatus)； 状态值通过参数传入，禁止硬编码。

### rule-java-magic-001 · 禁止魔法值

- **级别**：must
- **类别**：naming
- **执行**：auto / checkstyle
- **项目 7 适用**：是
- **规则**：状态值使用枚举；其他常量定义在类级别 private static final；代码中禁止硬编码字符串/数字。

### rule-java-method-comment-001 · 方法注释位于注解上方

- **级别**：should
- **类别**：comment
- **执行**：manual / review
- **项目 7 适用**：是
- **规则**：方法注释必须放在 @Override、@MLog 等方法注解的上面，不放在注解与签名之间。

### rule-java-methodlen-001 · 单个方法不超过 70 行

- **级别**：must
- **类别**：maintainability
- **执行**：auto / checkstyle
- **项目 7 适用**：是
- **规则**：单个方法（含注释和空行）不超过 70 行；超出须拆分为子方法。

### rule-java-money-001 · 金额使用 BigDecimal

- **级别**：must
- **类别**：correctness
- **执行**：manual / review
- **项目 7 适用**：是
- **规则**：金额、费用、价格计算必须使用 BigDecimal；禁止 double/float； 除法必须指定精度与舍入模式。

### rule-java-naming-001 · 类/方法/常量命名规范

- **级别**：must
- **类别**：naming
- **执行**：auto / checkstyle
- **项目 7 适用**：是
- **规则**：类名 UpperCamelCase；方法/变量 lowerCamelCase；常量 UPPER_SNAKE_CASE； 包名小写；禁止拼音与无意义缩写。

### rule-java-null-001 · 空值判断统一使用 ObjectUtils/StringUtils

- **级别**：must
- **类别**：null-safety
- **执行**：auto / checkstyle
- **项目 7 适用**：是
- **规则**：禁止 == null / != null 判空；统一使用 ObjectUtils.isEmpty() / StringUtils.isBlank()。 MyBatis XML 的 if test 表达式除外。

### rule-java-sql-update-001 · UPDATE 使用业务主键定位

- **级别**：must
- **类别**：sql
- **执行**：manual / review
- **项目 7 适用**：是
- **规则**：UPDATE 操作必须使用业务主键（如 entity_code、key_id）定位， 不使用数据库自增 id；编辑入参必须做非空校验。

### rule-java-threadpool-001 · 禁止使用 Executors 创建线程池

- **级别**：must
- **类别**：concurrency
- **执行**：auto / pmd
- **项目 7 适用**：是
- **规则**：必须使用 ThreadPoolExecutor 显式指定核心参数；禁止 Executors.newFixedThreadPool / newCachedThreadPool 等无界队列工厂方法。

### rule-java-time-001 · 时间处理使用 java.time

- **级别**：should
- **类别**：correctness
- **执行**：manual / review
- **项目 7 适用**：是
- **规则**：使用 java.time（Instant/LocalDateTime/Duration）；禁止静态共享 SimpleDateFormat。

### rule-java-tx-001 · 事务注解位置与回滚规则

- **级别**：must
- **类别**：transaction
- **执行**：manual / review
- **项目 7 适用**：是
- **规则**：@Transactional 只放在事务边界；rollbackFor 必须指定 Throwable； 非事务方法不得标注。

### rule-java-validate-001 · 参数校验与 DTO 转换放 service 层

- **级别**：should
- **类别**：layering
- **执行**：manual / review
- **项目 7 适用**：是
- **规则**：参数非空/格式/存在性/唯一性校验与 DTO→BO 转换统一放 service 层； core/repository 层只做领域逻辑。

### rule-sql-index-001 · 查询必须有索引或分区依据

- **级别**：must
- **类别**：sql
- **执行**：manual / review
- **项目 7 适用**：是
- **规则**：高频查询必须走索引或分区键；上线前用 EXPLAIN 验证执行计划。

### rule-sql-select-001 · 禁止 SELECT *

- **级别**：must
- **类别**：sql
- **执行**：manual / review
- **项目 7 适用**：是
- **规则**：查询必须显式列出所需字段；禁止 SELECT *。

### rule-test-001 · 单测覆盖成功与关键异常场景

- **级别**：must
- **类别**：testing
- **执行**：auto / jacoco
- **项目 7 适用**：是
- **规则**：单测必须覆盖方法的成功场景与关键异常场景（参数校验、重复校验、状态校验）； 测试命名表达意图。

## L2

### rule-p7-alias-001 · 请求 model 字段是别名，不是上游模型

- **级别**：must
- **类别**：routing
- **执行**：auto / custom
- **项目 7 适用**：是
- **规则**：请求中的 model 是用户侧别名；网关按配置映射到部署列表； 别名与上游真实模型名必须分别存储、不得混用。

### rule-p7-cost-001 · 计价必须显式配置

- **级别**：must
- **类别**：cost
- **执行**：auto / custom
- **项目 7 适用**：是
- **规则**：模型价格必须在配置中显式声明（输入/输出单价，按 1M tokens 换算）； 禁止依赖内置价格表；spend 必须可按日志复算。

### rule-p7-error-001 · 错误体对齐 OpenAI 格式

- **级别**：must
- **类别**：api
- **执行**：auto / custom
- **项目 7 适用**：是
- **规则**：错误响应统一为 {"error":{"message","type","param","code"}}； HTTP 状态码与 LiteLLM 基线一致（400/401/403/429/502/504）。

### rule-p7-fallback-001 · fallback 语义与 model 字段策略必须显式决策

- **级别**：must
- **类别**：routing
- **执行**：manual / review
- **项目 7 适用**：是
- **规则**：fallback 成功时响应 model 字段保留上游名还是统一改写为别名， 必须在需求决策记录中显式选择并写测试。

### rule-p7-keyhash-001 · 虚拟 Key 只存哈希

- **级别**：must
- **类别**：security
- **执行**：manual / review
- **项目 7 适用**：是
- **规则**：虚拟 Key 明文只在创建时返回一次；数据库只存哈希，禁止存明文； Auth 校验使用哈希比对。

### rule-p7-master-001 · 无 master key 拒绝启动

- **级别**：must
- **类别**：security
- **执行**：auto / custom
- **项目 7 适用**：是
- **规则**：网关启动时必须校验 master key 已配置；缺失则拒绝启动并输出明确错误。

### rule-p7-models-001 · /models 只暴露别名

- **级别**：must
- **类别**：security
- **执行**：auto / custom
- **项目 7 适用**：是
- **规则**：GET /models 只返回配置中的模型别名，不暴露上游真实模型名与 api_base/api_key。

### rule-p7-openai-001 · 响应必须为 OpenAI ChatCompletion 结构且字段不丢

- **级别**：must
- **类别**：api
- **执行**：auto / custom
- **项目 7 适用**：是
- **规则**：/chat/completions 与 /v1/chat/completions 返回 OpenAI ChatCompletion 结构； 上游响应的 id/object/model/choices/usage 等字段必须原样保留，禁止因序列化丢字段。

### rule-p7-ratelimit-001 · 限流计数必须并发安全

- **级别**：must
- **类别**：concurrency
- **执行**：manual / review
- **项目 7 适用**：是
- **规则**：内存版 rpm/tpm 滑动窗口计数必须加锁或使用原子结构； 预留 Redis 接口供二期替换。

### rule-p7-sse-001 · SSE 流式 usage 在末块且断开释放连接

- **级别**：should
- **类别**：api
- **执行**：manual / review
- **项目 7 适用**：是
- **规则**：stream=true 时按 SSE 分块透传，usage 在最后一个数据块； 客户端断开时必须释放上游连接，日志/计量在流结束后补记。
