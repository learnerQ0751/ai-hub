# REQ-2.10 验收：MCP 客户端资源治理

- 状态：已验收（2026-11-04）
- 验收人：主人
- 需求记录：`../records/REQ-2.10.md`
- 需求来源：验收反馈（探活资源泄漏探讨）

## 1. 需求验收标准

- [x] 探活/工具调用失败或超时后连接不增长（连接池上限 + 空闲回收）
- [x] stdio 读取使用专用有界线程池（不再用 commonPool）
- [x] 模拟"父进程已死"的 PID 记录 → 启动清理杀掉孤儿并删除记录
- [x] HTTP 探活请求带 `Connection: close`
- [x] `GET /v1/mcp/runtime/stats` 返回三个计数
- [x] 非功能验收：四条路径（正常/超时/进程崩溃/机器崩溃）已说明；本地资源指标每实例统计、未写 Redis（STANDARDS §2.7）
- [x] `mvn -Pquality verify` 全绿

**验收命令 / 示例**：

```bash
curl http://localhost:18080/v1/mcp/runtime/stats -H "Authorization: Bearer sk-local-master-key"
# {"activeStdioProcesses":0,"inFlightProbeHttp":0,"inFlightToolCalls":0}
```

## 2. 从哪看起

1. 本文 §1
2. `records/REQ-2.10.md` §1-3
3. 核心文件：`McpHttpClientFactory`、`McpIoExecutor`、`StdioProcessRegistry`、`McpServerProbe`

## 3. 实现要点与参考答案

### 3.1 三条关键实现要点

1. 显式连接池（64 连接、5s 获取、30s 空闲、5min 生命周期、30s 后台回收），关闭时 dispose；探活用 `Connection: close` 不占业务池
2. stdio 读取从 commonPool 移到专用 8 线程池；超时强杀进程后线程释放
3. 子进程 PID + parentPid 落盘；启动扫描只杀"子进程存活且父进程已死"的孤儿，避免滚动重启误杀

### 3.2 面试 1 分钟讲法

```text
MCP 客户端有三类资源：HTTP 连接、stdio 子进程、读线程。连接池显式配置并回收；
stdio 读用专用线程池；子进程崩溃场景靠 PID + parentPid 记录在下次启动清理；
探活用短连接；统计端点暴露活跃子进程与进行中请求。
进程崩溃时 OS 关闭 socket，连接不会泄漏；机器崩溃时对端靠 keepalive/空闲超时回收。
```

### 3.3 自测题与参考答案

1. JVM 被 kill -9 后，stdio 子进程怎么办？
2. 为什么孤儿清理要校验 parentPid？
3. 为什么探活要用短连接？
4. HTTP 连接池的 maxIdleTime 为什么设 30s？

### 参考答案

1. shutdown hook 不执行；下次启动扫描 PID 记录，杀掉父进程已死的孤儿；多数 MCP server 也会因 stdin EOF 退出。
2. 多实例同机/滚动重启时，另一个实例的子进程父进程仍存活，不能误杀。
3. 探活低频，池化收益低；短连接避免复用被服务端 keep-alive 关闭的旧连接。
4. 短于服务端 keep-alive（Tomcat 约 60s），防止拿到已被对端关闭的连接。

### 3.4 亲手改的口子（可选）

- 任务：把连接池参数（64/30s/5min）改为配置项。
- 验证方法：改配置后 `/v1/mcp/runtime/stats` 与连接行为符合预期。

## 4. 回归影响

- 需重跑：REQ-2.1/2.2/2.3 的探活、工具发现、调用测试（已覆盖）
- 真实链路：注册/工具列表/调用/统计端点正常

## 5. 验收记录

- 验收日期：2026-11-04
- 结论：理解验收默认通过
- 遗留问题：统计未接入 Micrometer（第 4 期）
