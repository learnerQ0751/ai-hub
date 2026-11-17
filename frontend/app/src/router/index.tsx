import { RequireAuth } from '@/components/RequireAuth';
import { AppShell } from '@/layouts/AppShell';
import LoginPage from '@/pages/login/LoginPage';
import { Spin } from 'antd';
import { lazy, Suspense } from 'react';
import { Navigate, Route, Routes } from 'react-router-dom';

const OverviewPage = lazy(() => import('@/pages/overview/OverviewPage'));
const KeysPage = lazy(() => import('@/pages/keys/KeysPage'));
const ModelsPage = lazy(() => import('@/pages/models/ModelsPage'));
const McpServersPage = lazy(() => import('@/pages/mcp/McpServersPage'));
const PromptsPage = lazy(() => import('@/pages/prompts/PromptsPage'));
const RagPage = lazy(() => import('@/pages/rag/RagPage'));
const LogsPage = lazy(() => import('@/pages/logs/LogsPage'));
const UsagePage = lazy(() => import('@/pages/usage/UsagePage'));
const AuditPage = lazy(() => import('@/pages/audit/AuditPage'));
const PlaygroundPage = lazy(() => import('@/pages/playground/PlaygroundPage'));
const SystemPage = lazy(() => import('@/pages/system/SystemPage'));
const BoardPage = import.meta.env.DEV ? lazy(() => import('@/pages/board/BoardPage')) : null;
const McpPreviewPage = import.meta.env.DEV
  ? lazy(() => import('@/pages/board/McpPreviewPage'))
  : null;
const KeysPreviewPage = import.meta.env.DEV
  ? lazy(() => import('@/pages/board/KeysPreviewPage'))
  : null;
const ModelsPreviewPage = import.meta.env.DEV
  ? lazy(() => import('@/pages/board/ModelsPreviewPage'))
  : null;
const PromptsPreviewPage = import.meta.env.DEV
  ? lazy(() => import('@/pages/board/PromptsPreviewPage'))
  : null;
const LogsPreviewPage = import.meta.env.DEV
  ? lazy(() => import('@/pages/board/LogsPreviewPage'))
  : null;
const AuditPreviewPage = import.meta.env.DEV
  ? lazy(() => import('@/pages/board/AuditPreviewPage'))
  : null;

export function AppRouter() {
  return (
    <Suspense
      fallback={
        <div style={{ padding: 48, textAlign: 'center' }}>
          <Spin size="large" />
        </div>
      }
    >
      <Routes>
        <Route path="/login" element={<LoginPage />} />
        {BoardPage &&
        McpPreviewPage &&
        KeysPreviewPage &&
        ModelsPreviewPage &&
        PromptsPreviewPage &&
        LogsPreviewPage &&
        AuditPreviewPage ? (
          <>
            <Route path="/board" element={<BoardPage />} />
            <Route
              path="/board/preview/mcp"
              element={<AppShell preview activeKey="/mcp-servers" />}
            >
              <Route index element={<McpPreviewPage />} />
            </Route>
            <Route path="/board/preview/keys" element={<AppShell preview activeKey="/keys" />}>
              <Route index element={<KeysPreviewPage />} />
            </Route>
            <Route path="/board/preview/models" element={<AppShell preview activeKey="/models" />}>
              <Route index element={<ModelsPreviewPage />} />
            </Route>
            <Route
              path="/board/preview/prompts"
              element={<AppShell preview activeKey="/prompts" />}
            >
              <Route index element={<PromptsPreviewPage />} />
            </Route>
            <Route path="/board/preview/logs" element={<AppShell preview activeKey="/logs" />}>
              <Route index element={<LogsPreviewPage />} />
            </Route>
            <Route path="/board/preview/audit" element={<AppShell preview activeKey="/audit" />}>
              <Route index element={<AuditPreviewPage />} />
            </Route>
          </>
        ) : null}
        <Route element={<RequireAuth />}>
          <Route element={<AppShell />}>
            <Route index element={<Navigate to="/overview" replace />} />
            <Route path="overview" element={<OverviewPage />} />
            <Route path="keys" element={<KeysPage />} />
            <Route path="models" element={<ModelsPage />} />
            <Route path="mcp-servers" element={<McpServersPage />} />
            <Route path="prompts" element={<PromptsPage />} />
            <Route path="rag" element={<RagPage />} />
            <Route path="logs" element={<LogsPage />} />
            <Route path="usage" element={<UsagePage />} />
            <Route path="audit" element={<AuditPage />} />
            <Route path="playground" element={<PlaygroundPage />} />
            <Route path="system" element={<SystemPage />} />
          </Route>
        </Route>
        <Route path="*" element={<Navigate to="/overview" replace />} />
      </Routes>
    </Suspense>
  );
}
