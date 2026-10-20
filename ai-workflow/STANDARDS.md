# 开发规范（通用）

> 适用：Java / Spring 项目。项目专属规则见 `PROJECT.md`。
> 级别：**MUST**（必须）、**SHOULD**（推荐）、**MAY**（可选）。
> 优先级：安全红线 > 项目规则 > 通用规则；强制 > 推荐 > 参考。

## 1. 安全底线（MUST）

- 密钥、密码、令牌只放环境变量或 gitignored 本地配置；禁止入库、禁止打印日志。
- 所有 SQL 使用参数化绑定；禁止拼接用户输入。
- 错误响应不得泄露上游密钥、内部堆栈或敏感配置。
- 日志不得打印 API Key、密码、完整令牌、身份证号、银行卡号等敏感字段。
- 提交前检查 `git status`，不提交本地配置、密钥、`target/`、日志和临时文件。

## 2. Java / Spring

### 2.1 强制（MUST）

- 空值判断用 `ObjectUtils.isEmpty()` / `StringUtils.isBlank()`；禁止 `== null` / `!= null` 判空。
- 禁止魔法值：状态用枚举，其他常量用 `private static final`。
- 单个方法 ≤ 70 行；超出拆分为子方法。
- 类名 UpperCamelCase；方法/变量 lowerCamelCase；常量 UPPER_SNAKE_CASE；包名小写。
- 分层：Controller → Service → Repository/DAL；禁止跨层调用、反向依赖、包循环。
- 参数校验与 DTO 转换放 service 层；repository/DAL 只做数据访问。
- `@Transactional` 只放事务边界；`rollbackFor = Throwable.class`；单 SQL 写操作不加事务。
- 日志用 SLF4J 占位符；禁止 `System.out/err`、`printStackTrace`、字符串拼接日志。
- 对象比较用 `equals` / `Objects.equals`；禁止用 `==` 比较包装类型/字符串。
- 金额、费用、价格用 `BigDecimal`；禁止 `double/float`；除法指定精度与舍入。
- 线程池用 `ThreadPoolExecutor` 显式参数；禁止 `Executors.newFixedThreadPool` / `newCachedThreadPool`。
- 返回集合/数组时，无数据返回空集合/空数组，禁止返回 null。

### 2.2 推荐（SHOULD）

- DTO 声明 `private static final long serialVersionUID`。
- 集合已知容量时指定初始容量；禁止在 foreach 中删除元素。
- 时间处理用 `java.time`；禁止静态共享 `SimpleDateFormat`。
- 方法注释放在注解上方；Service 接口注释说明用途、作者、日期。
- 不要用异常控制正常流程；异常转换为业务异常并保留 cause。
- 外部配置外部化；禁止硬编码环境地址、端口、模型名。

## 3. SQL / 数据库

### 3.1 强制（MUST）

- UPDATE 使用业务主键定位（如 `key_hash`、`entity_code`），不使用自增 id；编辑入参判空。
- 禁止 `SELECT *`；查询显式列出字段。
- 高频查询必须有索引或分区依据；上线前用 `EXPLAIN` 验证。
- 表名小写、必须有注释；单表字段数 ≤ 50；字符集统一。
- 使用 MyBatis 时，XML 中字符串字段加 `jdbcType=VARCHAR`，时间字段 `TIMESTAMP`，id 字段 `BIGINT`。

### 3.2 推荐（SHOULD）

- 逻辑删除方法命名 `updateStatusByXXX(code, operator, fromStatus, toStatus)`；状态值通过参数传入。
- 修改 SQL 时同步维护 MySQL/PostgreSQL 两套 mapper（如项目存在双写）。

## 4. API / HTTP

### 4.1 强制（MUST）

- 对外接口契约稳定；错误体统一：`{"error":{"message","type","param","code"}}`。
- 上游 JSON 原样透传，不得因序列化丢字段；如需改写，只改约定字段并写测试。
- 输入校验：必填字段、类型、范围；未实现的能力明确拒绝（如 `stream=true` 返回 400）。
- 每个请求生成 `X-Request-Id` 并写入日志，便于排障。
- 上游错误/超时不泄露上游密钥；状态码与错误类型对齐约定。

### 4.2 推荐（SHOULD）

- 对外暴露别名/稳定标识，不暴露上游供应商细节。
- 错误消息可操作：说明哪个字段、期望什么、下一步怎么做。

## 5. 测试

### 5.1 强制（MUST）

- 新增/修改 service 方法必须有单测，覆盖成功场景 + 关键异常场景。
- 先写失败测试，再写最小实现，测试通过后再重构（TDD）。
- 提交前运行项目质量门禁（如 `mvn -Pquality verify`）；失败不得提交。

### 5.2 推荐（SHOULD）

- Controller 用 MockMvc；外部依赖用 MockWebServer/WireMock；架构规则用 ArchUnit。
- 覆盖率门槛由项目决定；新项目建议首个功能后提升到 70%。

## 6. Git / 提交

### 6.1 强制（MUST）

- 提交信息：`feat/fix/refactor/docs/test/chore: 简述`。
- 禁止在提交信息中包含模型署名；作者按公司/项目要求。
- 一个需求/任务一个提交或逻辑组；提交前检查 diff，不提交无关文件。
- 代码与对应 `records/REQ-*.md` 一起提交。

### 6.2 推荐（SHOULD）

- 提交前跑一次 `git diff` 人工复核；避免全文件重写。
- 大改动先建分支；小改动可直接在当前分支完成。

## 7. 项目专属规则

见 `PROJECT.md`。项目规则优先于本文件中的通用规则。

## 8. 历史全量规则

`REFERENCE/legacy-standards/` 保留了此前的 45 条 YAML 规则卡与生成器，仅供查阅，**默认不加载**。
