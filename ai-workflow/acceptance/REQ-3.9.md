# REQ-3.9 验收：文档解析扩展

- 状态：待验收（2026-11-12，实现与自检完成）
- 验收人：主人
- 需求记录：`../records/REQ-3.9.md`
- 需求来源：生产化补强第 6 批简报（主人验收反馈）

## 1. 需求验收标准

> 实现前冻结，唯一勾选点；实现完成后只补 §2-4，不改本节标准。

- [x] `DocumentTextExtractor` 保持公开方法 `supports(fileName)` / `extract(fileName, content)` /
      `fileParam()` 不变，内部改为「格式 → 解析器」注册表；新增格式只增加解析器 Bean
- [x] txt / md / pdf 既有行为与错误文案不变（严格 UTF-8、PDFBox、空文本判空）
- [x] 支持 `.docx`：按文档顺序提取段落与表格单元格；页眉页脚可配置（默认包含）；
      嵌套表格有深度上限；加密文档返回中文错误
- [x] 支持 `.pptx`：按页提取文本框与表格文本，页间有分隔；组合形状递归且有上限；
      备注默认不提取（可配置）；加密文档返回中文错误
- [x] 支持 `.xlsx`：按工作表提取单元格文本（含表头），输出 TSV 风格；公式取缓存值，
      无缓存回退公式文本；隐藏工作表默认跳过；行/列/单元格数受配置上限约束
- [x] 支持 `.html` / `.htm`：jsoup 正文提取（article/main 优先，否则 body），移除
      script/style/noscript/iframe/svg 等；块级元素换行、列表符号、表格制表符保留；
      编码按 BOM/meta 自动探测
- [x] 老 `.doc` 明确拒绝，返回 400 中文错误「暂不支持 .doc 格式，请用 Word 另存为 .docx 后重新上传」；
      `.doc` 不配置上传类型上限
- [x] OOXML（docx/pptx/xlsx）解压炸弹防护：解析前校验 ZIP 条目数与声明解压大小，
      超限返回 400；POI 全局 `ZipSecureFile` 限制在解析前生效
- [x] 文件内容与扩展名不符（如 `.doc` 改名 `.docx`、损坏 ZIP、加密文档、PDF 伪装）返回
      明确中文错误，不泄露堆栈
- [x] 解析输出受 `gateway.rag.parse.max-chars` 约束：默认 `fail`（超限 400），可配置
      `truncate`（截断并追加标记、WARN 日志）
- [x] 上传类型上限新增 pptx / xlsx / html / htm；Spring multipart 硬上限仍覆盖最大类型；
      `resolveContentType` 为 docx/pptx/xlsx/html/htm 返回标准 MIME
- [x] 安全：解析器不执行任意代码；POI/jsoup 不解析外部实体；不记录正文到日志
- [x] 非功能：解析器无状态、线程安全；POI 资源 try-with-resources；临时文件 finally 删除；
      无新增本地业务状态（STANDARDS §2.7）
- [x] 对外错误 `error.message` 为中文，`type/param/code`/HTTP 状态码保持英文/标准值
- [x] `mvn -Pquality verify` 全绿（含既有基线测试）；ArchUnit 依赖方向通过；持久层仍为 MyBatis

**验收命令 / 示例**：

```bash
# 上传 docx（准备一个真实 Word 文档）
curl -X POST http://localhost:18080/v1/rag/documents \
  -H "Authorization: Bearer <master-key>" \
  -F "file=@demo.docx" -F "project_id=p-demo"

# 上传老 .doc：应返回 400，message 含「另存为 .docx」
curl -X POST http://localhost:18080/v1/rag/documents \
  -H "Authorization: Bearer <master-key>" \
  -F "file=@legacy.doc" -F "project_id=p-demo"
```

## 2. 从哪看起（固定 10 分钟）

1. 本文 §1（2 分钟）
2. `records/REQ-3.9.md` §1-3 人向说明（3 分钟）
3. 核心文件（5 分钟）：`DocumentTextExtractor`、`DocxTextParser`、`XlsxTextParser`

## 3. 实现要点与参考答案（按需阅读）

### 3.1 三条关键实现要点

1. **门面 + 注册表**：`DocumentTextExtractor` 构造时把 `List<DocumentTextParser>` 按扩展名建索引，
   重复扩展名启动失败；`extract` 统一做换行归一化、空文本判空与字符上限，调用方契约不变。
2. **双层解压防护**：`DocumentFormatDetector` 用临时文件 + `ZipFile` 读中央目录，前置拒绝条目数/
   单条目/总大小超限；`PoiSecurityConfig` 在任何解析前设置 `ZipSecureFile` 全局限制兜底。
3. **格式安全识别**：PDF 校验 `%PDF-`；OOXML 校验 ZIP + 入口文件；加密 OOXML 通过 OLE2 容器中的
   `EncryptionInfo` 识别；`.doc` 改名 `.docx` 返回「不是有效的 docx」，均不泄露堆栈。

### 3.2 面试 1 分钟讲法（骨架）

```text
文档解析用一个门面 + 每格式一个解析器 Bean，按扩展名路由，新增格式只加一个类。OOXML 本质是 ZIP，
所以在进 POI 之前先读 ZIP 中央目录做条目数和声明大小预检，再用 POI 的 ZipSecureFile 做实际解压兜底；
加密 OOXML 是 OLE2 容器，单独识别后给出中文提示。解析输出还有字符上限与 fail/truncate 策略，
避免一个上传把内存或切分链路打满。
```

### 3.3 自测题与参考答案

1. 为什么 ZIP 预检要读中央目录，而不是直接流式解压？
2. 加密 docx 为什么不能只按 ZIP 检测？
3. docx 表格为什么不能直接用 `getText()`？
4. xlsx 公式单元格直接取值会怎样？本期怎么处理？
5. 解析超限为什么默认 `fail` 而不是 `truncate`？

### 参考答案

1. 流式遍历会实际解压条目，zip 炸弹在预检阶段就能消耗 CPU；中央目录只含条目名与声明大小，
   可不解压完成条目数/大小校验。声明大小可伪造，所以仍需 POI 实际解压限制兜底。
2. 加密 OOXML 被包在 OLE2 复合文档里，不是 ZIP；只按 ZIP 检测会落到「不是有效 docx」，
   用户拿不到「先解除密码」的正确指引。
3. `getText()` 的行/单元格分隔不可控，合并单元格会重复文本；逐行逐单元格遍历能控制制表符、
   换行与嵌套表格深度，输出更稳定。
4. 公式单元格直接 `getStringCellValue()` 会抛异常；本期用 `DataFormatter` 取缓存显示值，
   无缓存时回退公式文本。
5. 知识库要的是完整可信的内容；截断会造成「上传成功但内容缺失」的隐性质量问题。确有需要时
   显式配置 `truncate`，并保留 WARN 与截断标记。

### 3.4 亲手改的口子（可选）

- 任务：把 `gateway.rag.parse.limit-policy` 改为 `truncate`，把 `max-chars` 改为 100，
  上传一段长文本，观察响应与 WARN 日志中的截断标记；再改回 `fail` 观察 400。
- 验证方法：查看 `document text truncated: extension=..., maxChars=100` 日志与文档列表状态。

## 4. 回归影响

- 需重跑的已有验收项：REQ-3.1 上传/解析/切分；REQ-3.2 索引；REQ-3.3 检索；
  REQ-3.4 回答；REQ-3.7 embedding；REQ-3.8 上传限制。

## 5. 验收记录

- 验收日期：2026-11-12（自检）
- 结论：实现完成，`mvn -Pquality verify` 全绿（432 测试，0 失败/0 错误/0 跳过；
  Checkstyle/SpotBugs/覆盖率/ArchUnit 通过）。
- 遗留问题：docx 文本框/SmartArt/OMML 公式、pptx 图表/SmartArt、xlsx 图表/图片/批注、
  扫描件 PDF OCR 不在本期范围；xlsx 仍为全量 DOM 解析，超大表依赖行/列上限，流式解析留二期。
