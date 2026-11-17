import { Breadcrumb, Space } from 'antd';
import type { ReactNode } from 'react';

interface PageHeaderProps {
  title: string;
  description?: string;
  section?: string;
  extra?: ReactNode;
}

export function PageHeader({ title, description, section, extra }: PageHeaderProps) {
  const items = [{ title: 'ai-hub' }, ...(section ? [{ title: section }] : []), { title }];

  return (
    <>
      <Breadcrumb items={items} style={{ marginBottom: 12 }} />
      <div className="page-header">
        <div>
          <h1 className="page-title">{title}</h1>
          {description ? <p className="page-desc">{description}</p> : null}
        </div>
        {extra ? <Space wrap>{extra}</Space> : null}
      </div>
    </>
  );
}
