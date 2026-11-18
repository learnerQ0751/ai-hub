import type { MockMode } from '@/features/shared/mock';
import { MOCK_MODE_OPTIONS } from '@/features/shared/mock';
import { Segmented, Space, Tag, Typography } from 'antd';
import { useState } from 'react';

interface ViewportSpec {
  key: string;
  label: string;
  width: number;
  height: number;
  scale: number;
  note: string;
}

const VIEWPORTS: ViewportSpec[] = [
  { key: '1440', label: '桌面', width: 1440, height: 900, scale: 0.42, note: '表格 + 冻结首列' },
  { key: '768', label: '平板', width: 768, height: 1280, scale: 0.65, note: '卡片列表' },
  {
    key: '375',
    label: '手机',
    width: 375,
    height: 1280,
    scale: 1,
    note: '单列卡片 · 筛选压一行',
  },
];

type BoardPageKey = 'mcp' | 'keys' | 'models' | 'prompts' | 'logs' | 'audit';

const PAGES: { value: BoardPageKey; label: string; title: string; realData?: boolean }[] = [
  { value: 'mcp', label: 'MCP Servers', title: 'MCP Servers · 三档四态' },
  { value: 'keys', label: '虚拟 Key', title: '虚拟 Key · 三档四态' },
  { value: 'models', label: '模型与部署', title: '模型与部署 · 三档四态' },
  { value: 'prompts', label: 'Prompt 模板', title: 'Prompt 模板 · 三档四态', realData: true },
  { value: 'logs', label: '调用日志', title: '调用日志 · 三档四态', realData: true },
  { value: 'audit', label: '审计日志', title: '审计日志 · 三档四态', realData: true },
];

export default function BoardPage() {
  const [page, setPage] = useState<BoardPageKey>('mcp');
  const [mode, setMode] = useState<MockMode>('ok');
  const current = PAGES.find((item) => item.value === page) ?? PAGES[0];

  return (
    <div className="board-page">
      <header className="board-head">
        <div>
          <div className="board-kicker">Design Board · L2 · D1 冷静工程台</div>
          <h1 className="board-title">{current.title}</h1>
          <p className="board-sub">
            真实可交互页面，不是截图。1440 / 768 / 375
            三档并排；切换状态即可查看加载、空数据与失败态。其余 5 页保持结构占位，本轮不展开。
          </p>
        </div>
        <Space direction="vertical" align="end" size={8}>
          <Segmented
            value={page}
            onChange={(value) => setPage(value as BoardPageKey)}
            options={PAGES.map(({ value, label }) => ({ value, label }))}
          />
          <Segmented
            value={mode}
            onChange={(value) => setMode(value as MockMode)}
            options={MOCK_MODE_OPTIONS}
          />
          <Space size={8}>
            <Tag color="default">仅开发环境</Tag>
            <Tag color={current.realData ? 'blue' : 'default'}>
              {current.realData ? '真实接口' : 'mock 数据'}
            </Tag>
            <Typography.Link href={`/${page === 'mcp' ? 'mcp-servers' : page}`} target="_blank">
              打开生产路由
            </Typography.Link>
          </Space>
        </Space>
      </header>

      <div className="board-strip">
        {VIEWPORTS.map((viewport) => (
          <section className="board-frame" key={viewport.key}>
            <div className="board-frame-head">
              <b>{viewport.width}</b>
              <span>{viewport.label}</span>
              <span className="board-frame-note">{viewport.note}</span>
              <a
                href={`/board/preview/${page}?mode=${mode}`}
                target="_blank"
                rel="noreferrer"
                className="board-frame-link"
              >
                全屏
              </a>
            </div>
            <div
              className="board-frame-box"
              style={{
                width: viewport.width * viewport.scale,
                height: viewport.height * viewport.scale,
              }}
            >
              <iframe
                title={`${current.label} ${viewport.width} ${mode}`}
                src={`/board/preview/${page}?mode=${mode}`}
                style={{
                  width: viewport.width,
                  height: viewport.height,
                  transform: `scale(${viewport.scale})`,
                }}
              />
            </div>
          </section>
        ))}
      </div>

      <footer className="board-notes">
        本轮只验证：Prompt 模板 / 调用日志 /
        审计日志三页是否与既有页面同一设计语言、三档降级是否成立、四态是否可读。
        {current.realData ? (
          <>
            <br />
            这三页接真实 console
            接口（/v1/admin/prompts、/v1/admin/logs、/v1/admin/audit-logs）；正常态是真实响应，加载
            / 空 / 失败由上方状态开关演示。
            <br />
            日志与审计的筛选、分页全部带查询参数打到后端，客户端不做截断式假分页。
          </>
        ) : (
          <>
            <br />
            这两页仍是 mock 数据，接口契约冻结后整体替换。
          </>
        )}
        <br />
        375 档筛选下拉与 MCP 页一致：选中值超长时在控件内省略，展开后下拉按内容自适应宽度。
      </footer>
    </div>
  );
}
