# 开发规范（通用）

> 适用：Java / Spring 企业项目。项目专属规则见 `docs/PROJECT.md`。
> 级别：**MUST**（必须）、**SHOULD**（推荐）、**MAY**（可选）。
> 优先级：安全红线 > 项目规则 > 通用规则；强制 > 推荐 > 参考。
> 接口契约的完整字段与示例见项目接口文档（由 `docs/PROJECT.md` 指定）；本文件给出骨架与强制规则。

## 1. 安全底线（MUST）

- 密钥、密码、令牌只放环境变量或 gitignored 本地配置；禁止入库、禁止打印日志。
- 所有 SQL 使用参数化绑定；禁止拼接用户输入。
- 错误响应不得泄露上游密钥、内部堆栈或敏感配置。
- 日志不得打印 API Key、密码、完整令牌、身份证号、银行卡号等敏感字段。
- 提交前检查 `git status`，不提交本地配置、密钥、`target/`、日志和临时文件。

## 2. 分层架构（MUST）

### 2.1 六层结构（参考监管报送 / Drip 技术栈）

| 层 | 职责 | 允许依赖 | 禁止 |
|---|---|---|---|
| `start/task` | 启动、定时任务入口 | service | 写业务逻辑 |
| `service` | 参数校验、DTO↔BO、编排 core | core | 碰 dal；业务编排下沉；跳过 core 直调 manager |
| `core/trigger` | 业务编排、事务边界、事件 | manager | 碰 dal/api/trigger |
| `manager/integration/api` | BO↔DO、Mapper 封装、外部集成、接口契约 | dal / 外部 SDK | 调 core/service；业务编排 |
| `dal` | 纯 SQL / 数据访问 | — | 业务逻辑 |
| `common` | 枚举、异常、工具、常量底座 | — | 依赖业务模块 |

调用方向：`start/task → service → core/trigger → manager/integration/api → dal → common`。**只能上层依赖下层，禁止反向依赖、跨层调用和包循环。**

### 2.2 边界规则

- `api`：只放接口契约 + ReqDTO/ResDTO；DTO 不得引用 BO/DO；不依赖业务模块。
- `service`：参数非空/格式/存在性/唯一性校验 + DTO↔BO 转换 + 调用 Core；不碰 DAL。
- `core`：业务编排、状态流转、幂等、事务边界、事件发布；不碰 DAL/API/trigger。
- `manager`：薄 DAL 门面，只做 BO↔DO 与 Mapper 封装；不做业务编排。
- `dal`：单表 SQL / 数据访问；不写业务逻辑。
- `integration`：外部 SDK/HTTP/RPC 封装；外部异常转业务异常，禁止静默吞没。
- `common`：枚举、异常、工具、常量；不依赖任何业务模块。
- 异步：不得在事务内；必须 clone/清理上下文（如项目有 EventContext/ThreadLocal）。
- 事务：`@Transactional` 只在 core/service 的事务边界；`rollbackFor = Throwable.class`；单 SQL 写操作不加事务。

### 2.3 非 Drip 项目的映射

Spring Boot 新项目可映射为：`Controller/api → Service → Core/Domain → Repository/DAL → common`。

仍须遵守：

- Controller 只做契约、参数接收、响应包装；不直接调 DAL。
- 业务编排与事务在 Service/Core；Repository 不做业务。
- 数据访问只在一层；禁止 Controller/Service 混写 SQL。
- 分包按职责，不按技术层堆大文件；包依赖无环（ArchUnit 检查）。

### 2.4 多模块结构（MUST）

- 中大型 Java 项目按监管报送（data-submission）模式采用 **Maven 多模块**，模块即层：`start / service / core / manager / integration / dal / common`；对外契约单独 `api`；集成测试与架构校验放 `tests`。
- 模块依赖方向固定：`start → service → core → manager/integration/api → dal → common`；禁止反向依赖与模块循环，ArchUnit 检查。
- 模块内包结构：`com.<company>.<project>.<layer>`；feature 子包（如 `core/routing`）只允许在层内。
- `api` 放 Service 接口 + ReqDTO/ResDTO；`service` 放 ServiceImpl；`core` 类用 `@Component`（`@Service` 归 service 层）。
- `start` 收口入口层：启动类放根包（`com.<company>.<project>`），Controller / Filter / 异常处理 / 框架配置放 `start` 的子包。
- 可执行包只由 `start` 模块产出；主 artifact 保持普通 jar（`spring-boot-maven-plugin` 配置 `classifier`），便于 `tests` 模块依赖。
- `config` 作为跨层支撑放在 `start/config`（框架配置）；业务配置模型（如 `GatewayProperties`）下移 `common`，不得让 core/service 依赖 start。
- 禁止 `gateway` / `biz` / `utils` 这类无边界大包承载多种职责；新增类必须能回答“它属于哪一层”。
- 单模块项目按 §2.1 的包结构映射执行；模块化时按本结构拆分。
- 测试包与被测包镜像；包依赖必须无环（ArchUnit 检查）。

### 2.5 多实例与状态（MUST）

- **默认无状态**：Controller / Service / Integration 不得持有可变业务状态（成员变量 Map/List/计数器/缓存）。
- 允许的成员变量：不可变配置、线程安全且无业务语义的基础设施（如 `ObjectMapper`、`RestClient`、`Logger`）。
- 跨请求状态必须外置：
  - 计数器、限流、预算、路由状态、冷却、会话 → Redis（原子操作/Lua）或数据库。
  - 持久化数据 → PostgreSQL / MySQL。
  - 本地缓存只允许只读/配置/可容忍过期的数据，并注明失效策略。
- 锁：
  - 本地锁（`synchronized` / `ReentrantLock`）只能保护本机内存中的非共享状态，**不得用于跨实例互斥**。
  - 跨实例互斥用分布式锁（Redis/Redisson 或数据库锁），必须设置超时、防死锁、幂等。
- 负载均衡：
  - 多实例优先用**无状态算法**（加权随机）；如必须用有状态算法（平滑轮询），状态必须共享，或明确接受“每实例独立计数”并在 `docs/PROJECT.md` 记录限制。
- 定时任务：多实例下必须 leader 选举，或用分布式锁/幂等保证只执行一次。
- 本地缓存/本地队列不得承载不可丢失的业务数据。

**多实例检查清单（每个需求收尾必答）**

- [ ] 是否有可变成员变量？是否线程安全、是否跨实例一致？
- [ ] 计数器/限流/预算/冷却是否在 Redis/数据库？
- [ ] 锁是否分布式？是否有超时、防死锁、幂等？
- [ ] 定时任务是否只执行一次？
- [ ] 本地缓存是否可容忍过期/不一致？

### 2.6 职责能力归属（MUST，所有设计通用）

设计任何能力（新增需求、重构、接口扩展）前，先判断职责类型与落点，并在 REQ §4 `capability` 中声明：

| 判定 | 含义 | 落点 | 禁止 |
|---|---|---|---|
| 横切治理（cross-cutting） | 对多个接口/业务生效的通用能力：认证鉴权、限流、预算、计价、计量、访问日志、追踪、幂等、审计、灰度等 | 统一入口（Filter/Interceptor）或统一拦截（AOP/装饰器） | 放进 `core`；放进某个具体业务 `Service`；绑定具体接口/DTO |
| 业务能力（business） | 某个领域/接口特有的规则、流程、状态 | `service`（编排/校验）/ `core`（领域编排/事务） | 让 core 依赖治理组件（限流器/计价器/日志门面） |

必须回答的三个问题：

1. **新增第二个接口**（流式/文件/批量/其他业务）时，这段逻辑是否**自动复用**？需要复制 → 说明它被绑定到了具体接口，必须上移到统一入口/切面。
2. 这段代码是否依赖**具体接口的请求/响应 DTO**？横切治理只能依赖通用上下文（租户/Key/路径/用量），不能依赖业务 DTO。
3. `core` 是否依赖治理组件（如 `RedisRateLimiter`、`PricingCatalog`、`CallLogManager`）？依赖即错，治理应在 core 之外完成。

检查信号（出现任一即停下重新设计）：

- 新增接口要复制同一段限流/计价/日志代码；
- 治理代码里出现具体接口名或业务 DTO；
- `core` 依赖中间件/治理门面；
- 无法用一句话回答“这个能力属于横切治理还是业务能力”。

架构约束：ArchUnit 只保证依赖方向，不保证职责归属；职责归属由设计评审 + REQ §4 声明 + 本条规约共同保证。

### 2.7 资源生命周期与故障模式（MUST，涉及连接/线程/进程/缓存的设计）

任何会持有外部资源的代码（HTTP 客户端、连接池、线程池、子进程、文件句柄、缓存）必须在 REQ §4 `nfr` 声明，并在 acceptance 验收。

**四条路径（获取 → 释放）必须全部回答**：

1. **正常路径**：用完即释放/归还（连接回池、进程 kill、线程归还）
2. **超时/异常路径**：超时或异常时释放；坏连接不得回池
3. **进程崩溃（JVM 崩溃 / kill -9）**：shutdown hook 不执行时如何兜底（PID 记录 + 启动清理、stdin EOF 约定、Job Object/PDEATHSIG）
4. **机器崩溃 / 网络分区**：对端如何回收（服务端 idle timeout、TCP keepalive；客户端无法主动释放）

**连接池**：显式配置 `maxConnections / pendingAcquireTimeout / maxIdleTime（短于服务端 keep-alive）/ maxLifeTime / 后台回收`；应用关闭时 dispose。禁止依赖未配置的默认池。

**线程池**：禁止用 `ForkJoinPool.commonPool` 执行可能阻塞的 IO；使用有界专用池并说明饱和策略。

**子进程**：统一登记；正常停止强杀；记录 `{pid, parentPid}` 供启动清理（只杀父进程已死的孤儿，避免多实例误杀）；约定子进程支持 stdin EOF 退出。

**指标归属**：
- 业务计数（限流/预算/冷却/会话/配额）→ Redis/DB，跨实例一致；
- 本地资源指标（连接数/线程数/本机子进程数）→ 每实例本地指标（Micrometer/统计端点），由监控端聚合；**禁止写 Redis 伪造全局状态**。

**检查信号**（出现任一即停下重新设计）：默认连接池/默认线程池；超时不释放；子进程不记录 PID；观测指标未声明归属。

## 3. Java / Spring 编码规范

### 3.1 基类与 DTO（MUST，标准网关类项目）

- **请求 DTO**：`*ReqDTO extends BaseReqDTO`（新增/删除/编辑/不分页查询）。
- **分页查询 DTO**：`*QueryDTO extends PageReqDTO`；不分页查询可继承 `BaseReqDTO`。
- **`BaseReqDTO` 公共字段**：`operator`、`tntId`、`remark`、`ext`。
- **`PageReqDTO` 公共字段**：`pageNum`（Long，默认 1）、`pageSize`（Long，默认 20，服务端上限 100）+ operator 相关字段。
- **响应 DTO**：`*ResDTO extends BaseResDTO`；`BaseResDTO` 承载平台公共字段：`id`、`createdAt`、`createdBy`、`updatedAt`、`updatedBy`、`tntId`、`ext`、`env`、`shadow`、`remark`（`status` 已废弃则按项目约定）。
- **分页响应**：统一 `PageResDTO<T>`：`pageNum` / `pageSize` / `totalPages` / `totalRecords` / `results(List<T>)`。
- **统一响应**：`Result<T>`；`T` 是业务响应参数 DTO；无业务数据时 `result.t` 返回空对象 `{}`，**禁止返回 null**。
- 每个 DTO 声明 `private static final long serialVersionUID`。
- DTO 不引用 BO/DO；api 层只放契约与 DTO。

> OpenAI 兼容等非标准网关契约的项目，由 `docs/PROJECT.md` 覆盖本节（例如 ai-hub 使用 OpenAI ChatCompletion 结构）。

### 3.2 命名规范

**方法名（MUST）**

- 查询：`query` 开头（`queryByPage`、`queryByCondition`、`queryById`）。
- 删除：`remove`；编辑：`modify`；新增：`save`。
- 启停/状态：`enable` / `disable` / `updateStatusByXXX`（按项目语义）。
- Service 方法名与接口路径最后一段保持一致（如 `queryReportSpecByPage`）。

**请求参数名（MUST）**

- 字段 lowerCamelCase；DTO 后缀 `*ReqDTO` / `*QueryDTO`。
- 分页：`pageNum` / `pageSize`；身份：`operator` / `tntId`。
- 布尔：`xxxFlag`（如 `businessSourceFlag`、`executionStrategyFlag`）。
- 业务唯一键：`businessCode` / `versionNo` / `frequency`（项目按实际命名）。
- 枚举值 UPPER_SNAKE_CASE（`DAILY` / `MONTHLY` / `COMPLETED`）。

**响应参数名（MUST）**

- 字段 lowerCamelCase；DTO 后缀 `*ResDTO`。
- 布尔：`xxxFlag` / `hasXxx`；列表：`xxxList` / `results`。
- 平台时间字段：`createdAt` / `updatedAt` 用毫秒时间戳；业务日期用 `yyyy-MM-dd`。
- 金额/费用：`BigDecimal`；禁止 `double/float`。
- `status` 已废弃时不得新增使用；新状态用明确枚举字段。

**接口路径（MUST）**

- 标准网关格式：`/系统名/service/方法名`，例如 `/api/ReportSpecService/queryReportSpecByPage`。
- 路径最后一段与 Service 方法名一致；变更路径必须同步接口文档与 Apifox。
- 网关「当前生效路径」与代码路径不一致时，文档同时给出两套，实际调用以网关为准。

### 3.3 编码强制（MUST）

- 空值判断用 `ObjectUtils.isEmpty()` / `StringUtils.isBlank()`；禁止 `== null` / `!= null` 判空。
- 禁止魔法值：状态用枚举，其他常量用 `private static final`。
- 单个方法 ≤ 70 行；超出拆分为子方法。
- 类名 UpperCamelCase；方法/变量 lowerCamelCase；常量 UPPER_SNAKE_CASE；包名小写。
- 参数校验与 DTO 转换放 service 层；repository/DAL 只做数据访问。
- 日志用 SLF4J 占位符；禁止 `System.out/err`、`printStackTrace`、字符串拼接日志。
- 对象比较用 `equals` / `Objects.equals`；禁止用 `==` 比较包装类型/字符串。
- 金额、费用、价格用 `BigDecimal`；禁止 `double/float`；除法指定精度与舍入。
- 线程池用 `ThreadPoolExecutor` 显式参数；禁止 `Executors.newFixedThreadPool` / `newCachedThreadPool`。
- 返回集合/数组时，无数据返回空集合/空数组，禁止返回 null。

### 3.4 编码推荐（SHOULD）

- 集合已知容量时指定初始容量；禁止在 foreach 中删除元素。
- 时间处理用 `java.time`；禁止静态共享 `SimpleDateFormat`。
- 方法注释放在注解上方；Service 接口注释说明用途、作者、日期。
- 不要用异常控制正常流程；异常转换为业务异常并保留 cause。
- 外部配置外部化；禁止硬编码环境地址、端口、模型名。
- 外部 SDK/HTTP/RPC 调用封装在 integration；异常转业务异常，禁止静默吞没。

### 3.5 注释（MUST）

- **类级注释**：说明职责、所属层、关键约束（Controller / Service / Integration / Selector 等）。
- **方法注释**：public / service / integration 方法必须有 Javadoc 或注释，说明用途、参数、返回、异常；简单 getter/setter 除外。
- **行级注释**：复杂逻辑必须注释“为什么”，至少覆盖：
  - 路由、权重、负载均衡算法；
  - 锁、并发、状态、缓存；
  - 重试、fallback、超时、幂等；
  - 计价、预算、限流；
  - 协议兼容、字段改写、特殊分支。
- 注释使用中文，与代码同步更新；禁止注释掉的死代码、禁止与实现矛盾的注释。
- AI 生成代码必须带注释；reviewer 把“关键逻辑无注释”视为 Important。

### 3.6 用户文案语言（MUST）

- **面向最终用户的文案使用项目客群语言**：本项目客群为中文使用者，对外错误 `error.message`、提示语、校验说明必须使用中文；其他项目按 `docs/PROJECT.md` 声明的客群语言执行。
- **结构化协议字段保持英文/标准值，禁止翻译**：`error.type`、`error.param`、`error.code`、JSON 字段名、HTTP header、状态码、日志 key、枚举值。
- **技术专有名词保留原文**：API Key、Redis、SSE、JSON、Token、HTTP 等；句中用中文组织，不强行翻译。
- **文案集中维护**：常规错误收敛到统一异常工厂/文案常量；调用点只传字段名、状态码等参数，禁止各接口自行拼整句。
- **日志语言与用户文案分离**：日志面向运维，保持英文即可；不得把日志原文直接作为对外 message。
- **验收要求**：涉及对外错误/提示的需求，acceptance 必须包含「message 中文 + type/param/code 不变」的验收项。

### 3.7 文档流程表达（MUST）

- **禁止 ASCII 箭头流程**：流程/链路/状态转换不得用 `A → B → C` 的纯文本箭头块表达（可读性差、无渲染、无法对照）。
- **简单流程用 Mermaid**：单主链路或 ≤5 个参与者/分支的流程，使用 Mermaid `flowchart`（流程图）或 `sequenceDiagram`（时序图）；写在代码块 ```mermaid 中。
- **复杂流程用 draw.io**：跨 3+ 子系统、多状态机、需要人工布局的架构级总图，使用 `.drawio` 源文件并导出 `.svg`（同目录），文档引用 svg；源文件与导出件一起提交。
- **判定口径**：单需求内部链路（≤5 参与者/单主链路）→ Mermaid；跨系统/多链路汇总总图 → draw.io。
- **存量文档**：新写或修改时按本规范迁移；历史文档按需迁移，不强制一次性重写。

## 4. 请求 / 响应契约（标准网关类项目，MUST）

> 适用于采用标准网关报文的项目；OpenAI 兼容等其它契约由 `docs/PROJECT.md` 覆盖。
> 完整字段与示例见项目接口文档（由 `docs/PROJECT.md` 指定）。

### 4.1 请求结构

- 外层为公共上下文，业务参数统一放在 `data` 字段内。
- 业务参数不得放在顶层；`data` 内同时携带随链路传入的身份/链路字段。
- 公共上下文字段（按项目实际报文为准）：

| 字段 | 说明 |
|---|---|
| `timestamp` | 请求时间戳 |
| `app_id` / `tnt_id` / `env` | 应用、租户、环境 |
| `operator_app_code` / `operatorAppCode` | 操作人应用编码 |
| `target_type` / `target_code` | 操作目标类型/编码 |
| `target_org_code` / `target_org_name` / `target_org_node` | 目标机构 |
| `ybyh_app_name` | 调用应用名 |
| `user_id` / `sessionToken` | 用户与会话 |
| `page_path` | 来源页面路径 |

请求骨架：

```json
{
  "data": {
    "operator": "zhangsan",
    "tntId": "0101",
    "pageNum": 1,
    "pageSize": 20
  },
  "timestamp": "2026-09-20 10:00:00",
  "app_id": "98001000000000304",
  "tnt_id": "0101",
  "env": "DEV_xxx",
  "operator_app_code": "INTERNAL001",
  "target_type": "DEPT",
  "target_code": "0010010009",
  "target_org_code": "8900000001",
  "target_org_name": "中国电信集团",
  "ybyh_app_name": "starsyufeng",
  "user_id": "admin",
  "sessionToken": "xxx",
  "page_path": "/xxx"
}
```

### 4.2 响应结构（Result<T>）

- 统一包装：`Result<T>`；`T` 是业务响应参数 DTO。
- 顶层字段：`code`、`success`、`message`、`result`、`traceId`、`sign`。
- `result` 内：`ok`（业务是否成功）、`t`（业务数据区，即 `T`）、错误字段（`errCd` / `errMsg` / `errLocation` / `errCdTrace` 等，按项目基类）。
- **两层成功判断**：第一层 `success`（网关转发成功）+ 第二层 `result.ok`（业务处理成功）；两层均成功时 `result.t` 才是有效业务数据。
- 无业务数据时：`result.t` 返回空对象 `{}`，**禁止 null**。
- 业务失败：`success=false`，`code` = 网关前缀 + 业务错误码（如 `98438259P002`），`message` 为描述；`result` 可能是处置策略标识（如 `"ABANDON"`）。
- 网关校验层失败：`code` 为 `9843xxx` 系列（如时间戳/上下文校验），`result` 为提示文本。

响应骨架：

```json
{
  "code": "10000",
  "success": true,
  "message": "SUCCESS",
  "result": {
    "ok": true,
    "t": {},
    "errCd": null,
    "errMsg": null
  },
  "traceId": "xxx",
  "sign": null
}
```

### 4.3 错误码

- 错误码项目级统一维护，不随单个接口变动；维护以 `ErrorCodeEnum` 为准。
- 返回码 = 网关前缀 `98438259` + 业务错误码（如 `98438259P002`）。
- 分类：`P` 参数类、`B` 业务类、`4xx` 采集执行类、`R` RPC 类、`E` 系统类。
- 新增错误码必须同步接口文档；不得复用已有码承载新语义。

### 4.4 接口路径与兼容

- 标准网关路径：`/系统名/service/方法名`。
- 代码路径与网关当前生效路径不一致时：文档同时给出，实际调用以网关为准；切换完成后同步更新。
- 所有接口按项目约定使用 `POST` / `application/json`（监管报送网关当前全部 POST）。
- 对外接口只暴露稳定别名/编码，不暴露上游供应商细节。

### 4.5 校验与序列化

- 校验框架：公司网关类项目用 **OVAL**（`@NotNull`、`@NotNegative`、`@Length` 等）；Spring Boot 新项目可用 Jakarta Validation；**同一项目只用一套**。
- 序列化框架：公司网关类项目用 **fastjson**；Spring Boot 新项目用 Jackson；**同一项目只用一套**，不得混用。
- 统一处理 null/空集合/时间格式；`Result<T>` 与业务 DTO 的序列化规则在项目中保持一致。
- 外部接口报文变更必须同步：DTO、校验注解、接口文档、Apifox/测试用例。

## 5. 技术栈约定（MUST）

> **每个项目必须在 `docs/PROJECT.md` 写明每类技术栈的唯一选型。**
> 实现阶段发现选型缺失/不明确时：**停止实现 → 询问用户 → 写入 `docs/PROJECT.md` → 再动手**。
> **禁止**以"本期简单/单表/临时"为由自行替换或降级框架（例：用 JdbcClient/JdbcTemplate 替代 MyBatis）；禁止同类框架混用。

| 领域 | 默认选型 | 约束 |
|---|---|---|
| JDK / 构建 | Java 8/11/17/21、Maven | 按公司项目版本；新项目参考 Java 21 + Spring Boot 4.1.1（ai-coder-help 同版） |
| Web / RPC | Spring MVC、RestClient、WebClient、Dubbo | 接口层不写业务；网关转发保持原始 JSON；RPC 契约与 DTO 分离 |
| 校验 | OVAL、Jakarta Validation | 网关类项目 OVAL；新项目 Jakarta Validation |
| 序列化 | Jackson 3、Jackson 2、fastjson | Spring Boot 4 默认 Jackson 3；若业务/LangChain4j 依赖 Jackson 2，显式提供 Jackson 2 Bean，避免注入歧义；网关类项目可用 fastjson；同一项目明确选型 |
| 持久层 | **MyBatis（默认）**、MyBatis-Plus | **MUST 使用 `docs/PROJECT.md` 指定的持久层框架**；SQL 只在 dal；**禁止未确认使用 JdbcTemplate/JdbcClient/Spring Data JPA**（Checkstyle `IllegalImport` 强制：`org.springframework.jdbc` / `jakarta.persistence` / `org.hibernate` 直接构建失败）；版本在根 pom 管理 |
| 缓存 / 共享状态 | Redis（Lettuce）、Redisson | 限流/预算/冷却/共享路由状态/分布式锁；禁止本地 Map 承载跨实例业务状态 |
| AI 应用 | LangChain4j | MCP/AiServices/RAG/Memory；网关透传层保持原生 HTTP，避免字段丢失 |
| Agent 编排 | LangGraph4j（可选） | 仅在有状态多步骤/多 Agent 图编排时引入；网关阶段不强制 |
| 配置 | Nacos、application.yml | 配置外部化；同 key 只放一处 |
| 日志 | SLF4J + Log4j2 | 占位符日志；不打印敏感信息 |
| 测试 | JUnit、MockMvc、MockWebServer、ArchUnit | service 方法必须有单测；架构规则用 ArchUnit |

## 6. SQL / 数据库

### 6.1 强制（MUST）

- UPDATE 使用业务主键定位（如 `key_hash`、`entity_code`），不使用自增 id；编辑入参判空。
- 禁止 `SELECT *`；查询显式列出字段。
- 高频查询必须有索引或分区依据；上线前用 `EXPLAIN` 验证。
- 表名小写、必须有注释；单表字段数 ≤ 50；字符集统一。
- 数据访问实现必须使用 `docs/PROJECT.md` 指定的持久层框架；新增表/查询/更新按该框架落位，不得临时改用其他数据访问方式。
- 使用 MyBatis 时，XML 中字符串字段加 `jdbcType=VARCHAR`，时间字段 `TIMESTAMP`，id 字段 `BIGINT`。
- 多数据源：SQL 语法必须兼容目标数据库；无法兼容时写两套并保证语义一致。

### 6.2 推荐（SHOULD）

- 逻辑删除方法命名 `updateStatusByXXX(code, operator, fromStatus, toStatus)`；状态值通过参数传入。
- 如项目存在多数据源适配，SQL 语法要兼容/写两套（如 MySQL/PostgreSQL 的 mapper 双写），并保证两边语义一致；修改一侧必须同步另一侧。

## 7. 测试

### 7.1 强制（MUST）

- 新增/修改 service 方法必须有单测，覆盖成功场景 + 关键异常场景。
- 先写失败测试，再写最小实现，测试通过后再重构（TDD）。
- 提交前运行项目质量门禁（如 `mvn -Pquality verify`）；失败不得提交。

### 7.2 推荐（SHOULD）

- Controller 用 MockMvc；外部依赖用 MockWebServer/WireMock；架构规则用 ArchUnit。
- 覆盖率门槛由项目决定；新项目建议首个功能后提升到 70%。
- 单测命名表达意图；异常路径与成功路径同等重要。

## 8. Git / 提交

### 8.1 强制（MUST）

- 提交信息：`<type>: <中文描述>`。
  - `type` 保留英文约定：`feat` / `fix` / `refactor` / `docs` / `test` / `chore`、`merge` / `revert` 等。
  - **描述必须使用中文**；技术专有名词保留原文（Redis、MyBatis、Flyway、MCP、Prompt、JWT 等）。
  - 示例：`feat: 新增虚拟 Key 生成与封禁接口`、`fix: 修复流式请求未拒绝的问题`、`refactor: 模型目录收口为 ModelCatalog 端口`。
  - 禁止纯英文描述（`feat: add virtual key API` 不合规）。
- 禁止在提交信息中包含模型署名；作者按公司/项目要求。
- 一个需求/任务一个提交或逻辑组；提交前检查 diff，不提交无关文件。
- 代码与对应 `records/REQ-*.md` 一起提交。

### 8.2 推荐（SHOULD）

- 提交前跑一次 `git diff` 人工复核；避免全文件重写。
- 大改动先建分支；小改动可直接在当前分支完成。

## 9. 设计原则（摘要，SHOULD）

- **SOLID**：单一职责、开闭、里氏替换、接口隔离、依赖倒置。
- **高内聚低耦合**：按职责分包；依赖指向抽象；包依赖无环；稳定依赖。
- **为变化预留空间但不过度设计**：新功能优先通过“增加”而不是“修改”实现（开闭原则）。
- **质量属性**：可维护、可测试、可观测、安全；架构评审时逐项检查。
- **TDD**：先测试后实现；小步提交，持续集成。
- **代码审查**：安全 → 性能 → 正确性 → 可维护性 → 测试（可参考仓库/知识库的 code-reviewer 规则）。

> 理论参考：`20-Learning/编程与开发/系统设计/06-软件架构设计与实现/`（4+1 视图、CBSE、ABSD、DSSA、质量属性、架构评估等）。这些是设计理论，不逐条搬进编码规范；需要时查阅。

## 10. 项目专属规则

见 `docs/PROJECT.md`。项目规则优先于本文件中的通用规则。

## 11. 参考来源与历史规则

- 监管报送编码规约（data-submission-standards，17 节）
- 阿里巴巴 Java 开发手册（黄山版）
- 数据库研发规范
- 金融科技开发技术规范（技术红线）
- 监管报送接口文档（02-接口文档（对外版））——请求/响应/错误码完整示例
- `ai-workflow/REFERENCE/legacy-standards/`：历史 45 条 YAML 规则库，仅供查阅，默认不加载
