# Spec 003：虚拟 Key（REQ-1.3）

## 用户故事

作为网关管理员，我希望用 master key 生成可分发的虚拟 key，使调用方只能访问被授权的模型，并能随时封禁/解封，
从而在不暴露上游密钥的前提下支持多租户与后续计量。

## 功能需求

1. **生成**：`POST /key/generate`，仅 master key。入参 `models`、`duration`、`metadata`、
   `max_budget`、`rpm_limit`、`tpm_limit`；返回一次性明文 `sk-`，服务端只存 SHA-256。
2. **鉴权**：`Authorization: Bearer <key>` 接受 master key 或虚拟 key。虚拟 key 校验存在、未封禁、未过期。
3. **模型白名单**：虚拟 key 只能调用 `models` 内的别名；为空视为全部。越权返回 403
   `key_model_access_denied`，`param=model`。
4. **查询**：`GET /key/info?key=<明文>`，仅 master key，返回元信息与 `spend`。
5. **封禁/解封**：`POST /key/block`、`POST /key/unblock`，仅 master key，入参 `{"key":"sk-..."}`。
6. **安全**：master key 来自 `GATEWAY_MASTER_KEY`，不落库、不打日志；明文虚拟 key 不落库、不回显完整值。

## 验收场景

- 生成后返回 `sk-` 明文；按明文查库为 0 行，按 SHA-256 查库为 1 行。
- 虚拟 key 调授权模型 200；调未授权模型 403（type/param/code 与基线一致）。
- block 后 401 `auth_error`；unblock 恢复 200。
- 过期 key 401；`/key/info` 返回 `key`（掩码）、`info.spend`、`info.models`、`info.expires`。

## 非目标

限流/预算执行、spend 累加、调用日志、fallback、流式。
