import { useEffect, useState } from 'react';

const QUERY = '(max-width: 991px)';

/** 与 antd Sider 的 lg 断点一致：992px 以下走窄屏布局。 */
export function useIsNarrow(): boolean {
  const [narrow, setNarrow] = useState(
    () => typeof window !== 'undefined' && window.matchMedia(QUERY).matches,
  );

  useEffect(() => {
    const media = window.matchMedia(QUERY);
    const onChange = () => setNarrow(media.matches);
    onChange();
    media.addEventListener('change', onChange);
    return () => media.removeEventListener('change', onChange);
  }, []);

  return narrow;
}
