import { PageHeader } from '@/components/PageHeader';
import { Card, Empty } from 'antd';

interface PagePlaceholderProps {
  title: string;
  section?: string;
  description: string;
}

export function PagePlaceholder({ title, section, description }: PagePlaceholderProps) {
  return (
    <>
      <PageHeader title={title} section={section} description={description} />
      <Card>
        <Empty
          image={Empty.PRESENTED_IMAGE_SIMPLE}
          description="页面骨架已就绪，业务内容与视觉将在后续阶段实现"
        />
      </Card>
    </>
  );
}
