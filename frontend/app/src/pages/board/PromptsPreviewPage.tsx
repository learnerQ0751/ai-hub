import PromptsPage from '@/pages/prompts/PromptsPage';
import { parsePreviewMode } from '@/pages/board/previewMode';
import { useSearchParams } from 'react-router-dom';

export default function PromptsPreviewPage() {
  const [params] = useSearchParams();
  return <PromptsPage initialDemoMode={parsePreviewMode(params.get('mode'))} />;
}
