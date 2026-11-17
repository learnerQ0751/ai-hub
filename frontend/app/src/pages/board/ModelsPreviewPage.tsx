import type { MockMode } from '@/features/shared/mock';
import ModelsPage from '@/pages/models/ModelsPage';
import { useSearchParams } from 'react-router-dom';

const MODES: MockMode[] = ['ok', 'loading', 'empty', 'error'];

function parseMode(value: string | null): MockMode {
  return MODES.includes(value as MockMode) ? (value as MockMode) : 'ok';
}

export default function ModelsPreviewPage() {
  const [params] = useSearchParams();
  return <ModelsPage initialMockMode={parseMode(params.get('mode'))} hideMockControl />;
}
