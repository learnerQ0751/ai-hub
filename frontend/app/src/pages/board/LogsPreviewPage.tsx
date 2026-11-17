import LogsPage from '@/pages/logs/LogsPage';
import { parsePreviewMode } from '@/pages/board/previewMode';
import { useSearchParams } from 'react-router-dom';

export default function LogsPreviewPage() {
  const [params] = useSearchParams();
  return <LogsPage initialDemoMode={parsePreviewMode(params.get('mode'))} />;
}
