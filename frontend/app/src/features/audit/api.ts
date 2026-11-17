import { endpoints, withQuery } from '@/api/endpoints';
import { request } from '@/api/http';
import type { AuditListQuery, AuditLogPage } from '@/features/audit/types';

/**
 * 审计分页查询。
 * 契约路径是 /v1/audit-logs（API-PATHS §4.6），不是 /v1/admin/audit。
 */
export async function fetchAuditLogs(query: AuditListQuery): Promise<AuditLogPage> {
  return request<AuditLogPage>(withQuery(endpoints.audit.list, { ...query }), { method: 'GET' });
}
