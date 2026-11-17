import AuditPage from '@/pages/audit/AuditPage';
import { parsePreviewMode } from '@/pages/board/previewMode';
import { useSearchParams } from 'react-router-dom';

export default function AuditPreviewPage() {
  const [params] = useSearchParams();
  return <AuditPage initialDemoMode={parsePreviewMode(params.get('mode'))} />;
}
