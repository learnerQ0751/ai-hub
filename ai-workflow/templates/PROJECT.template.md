# <项目名> 项目专属规则

> 本文件是项目专属规则；通用规则见 `STANDARDS.md`。**项目规则优先**。

## 基本信息

- 技术栈：JDK / Web/RPC / 校验 / 序列化 / 持久层
- 包名/目录：
- 默认端口：
- 当前进度：

## 分层结构（覆盖通用 §2）

- 采用：六层（start/task → service → core/trigger → manager/integration/api → dal → common）
- 或：Controller → Service → Repository
- 特殊说明：

## 接口契约（覆盖通用 §4）

- 采用：标准网关 `Result<T>` / OpenAI 兼容 / 其他
- 接口文档：
- 请求结构：
- 响应结构：
- 错误码：

## 业务/接口约束

- 
- 

## 质量门禁

```bash
# 填写项目实际使用的命令，例如：
# mvn -Pquality verify
# npm test
# pytest
```

## 配置与密钥

- 密钥只放：
- 本地启动注意：

## 记录

- 需求记录：`ai-workflow/records/REQ-*.md`
- 归因报告：
- 规格/变更：
