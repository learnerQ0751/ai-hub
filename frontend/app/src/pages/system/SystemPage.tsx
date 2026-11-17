import { PagePlaceholder } from '@/components/PagePlaceholder';

export default function SystemPage() {
  return (
    <PagePlaceholder
      title="系统状态"
      section="系统"
      description="gateway 与 console 健康、MCP 运行时计数与部署信息。"
    />
  );
}
