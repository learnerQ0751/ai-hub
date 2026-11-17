import type { MockMode } from '@/features/shared/mock';
import KeysPage from '@/pages/keys/KeysPage';
import { useSearchParams } from 'react-router-dom';

const MODES: MockMode[] = ['ok', 'loading', 'empty', 'error'];

function parseMode(value: string | null): MockMode {
  return MODES.includes(value as MockMode) ? (value as MockMode) : 'ok';
}

export default function KeysPreviewPage() {
  const [params] = useSearchParams();
  return <KeysPage initialMockMode={parseMode(params.get('mode'))} hideMockControl />;
}
