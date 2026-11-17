# LiteLLM Admin UI · 参考解剖

> 用途：L0 审美锚点的原始证据。对应结论见 `../../L0-视觉方向卡.md`。
> 抓取日期：2026-11-16。界面版本：截图水印为 v1.185.0。

## 这是什么

主人指定 LiteLLM 作为产品与审美锚点。本目录保存**官方文档内嵌的真实界面截图**，以及从截图像素取样的风格参数（`profile.json`）。

## 来源与真实性

- 截图来自 `https://docs.litellm.ai` 官方文档页面，不是本人伪造，也不是本地实例。
- 当前网络下 GitHub / jsDelivr 不可达，未能读取 LiteLLM 开源前端源码。
- **没有可登录的 LiteLLM 实例**，因此无法实测 hover、键盘操作、响应式断点与移动端表现。

## 截图清单

| 文件 | 官方来源 | 内容 |
|---|---|---|
| `screens/login.png` | `/docs/proxy/docker_quick_start` | 登录页 |
| `screens/models-list.png` | `/docs/proxy/docker_quick_start` | 模型与端点列表，含完整侧栏 |
| `screens/virtual-keys.png` | `/docs/simple_proxy`（Virtual keys 分栏） | 虚拟 Key 列表与创建入口 |
| `screens/mcp-servers.png` | `/docs/simple_proxy`（MCP servers 分栏） | MCP Server 卡片列表与健康状态 |
| `screens/usage.png` | `/docs/simple_proxy`（Usage 分栏） | KPI + 每日成本柱状图 |
| `screens/logs.png` | `/docs/simple_proxy`（Logs 分栏） | 高密度请求日志表 |
| `screens/playground.png` | `/docs/proxy/docker_quick_start` | Playground 对话与指标条 |
| `screens/create-key-modal.png` | `/docs/proxy/docker_quick_start` | 一次性 Key 弹窗（背景模糊） |

## 已知局限

1. 截图尺寸为 1280×720 或 640×360，非 1440/768/375 三档。
2. 颜色与尺寸是像素采样/目测，非 DOM 计算样式；次级文字与语义色置信度低。
3. 创建 Key 表单被模糊，字段布局不可读。
4. 响应式与交互状态未知，`profile.json` 中标记为 unknown。

## 后续补强（需要主人或丘工）

- 若能在本机跑一个 LiteLLM 实例，补 1440/768/375 三档截图与 DOM 样式导出。
- 若网络可达 GitHub，读取 `ui/litellm-dashboard` 的 Tailwind/CSS 配置，替换估测 token。
- 在拿到更高置信度证据前，L0 的视觉结论只作为方向参考，不当作设计基线。
