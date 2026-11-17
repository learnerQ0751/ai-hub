import type { McpMockMode } from '@/features/mcp/mock';
import McpServersPage from '@/pages/mcp/McpServersPage';
import { useSearchParams } from 'react-router-dom';

const MODES: McpMockMode[] = ['ok', 'loading', 'empty', 'error'];

function parseMode(value: string | null): McpMockMode {
  return MODES.includes(value as McpMockMode) ? (value as McpMockMode) : 'ok';
}

export default function McpPreviewPage() {
  const [params] = useSearchParams();
  return <McpServersPage initialMockMode={parseMode(params.get('mode'))} hideMockControl />;
}
