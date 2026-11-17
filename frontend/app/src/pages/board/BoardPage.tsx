import type { McpMockMode } from '@/features/mcp/mock';
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

const MODES: { value: McpMockMode; label: string }[] = [
  { value: 'ok', label: '正常' },
  { value: 'loading', label: '加载中' },
  { value: 'empty', label: '空数据' },
  { value: 'error', label: '加载失败' },
];

export default function BoardPage() {
  const [mode, setMode] = useState<McpMockMode>('ok');

  return (
    <div className="board-page">
      <header className="board-head">
        <div>
          <div className="board-kicker">Design Board · L2-1 · D1 冷静工程台</div>
          <h1 className="board-title">MCP Servers · 三档三态</h1>
          <p className="board-sub">
            真实可交互页面，不是截图。1440 / 768 / 375
            三档并排；切换状态即可查看加载、空数据与失败态。 其余 10 页保持结构占位，本轮不展开。
          </p>
        </div>
        <Space direction="vertical" align="end" size={8}>
          <Segmented
            value={mode}
            onChange={(value) => setMode(value as McpMockMode)}
            options={MODES}
          />
          <Space size={8}>
            <Tag color="default">仅开发环境</Tag>
            <Tag color="default">mock 数据</Tag>
            <Typography.Link href="/mcp-servers" target="_blank">
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
                href={`/board/preview/mcp?mode=${mode}`}
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
                title={`MCP Servers ${viewport.width} ${mode}`}
                src={`/board/preview/mcp?mode=${mode}`}
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
        本轮只验证：D1 视觉在 MCP 样板页的落点、窄屏降级方式、三态是否可读。强调色暂用电蓝
        #2457E6，L2 整体出稿时再定；数据与接口均为 mock。
        <br />
        375 档筛选下拉宽约
        110px：选中值超长时在控件内省略，悬停可见完整值；展开后下拉按内容自适应宽度（最小与控件同宽），触屏点开即可看到全值。
      </footer>
    </div>
  );
}
