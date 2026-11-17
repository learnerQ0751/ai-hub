/** GET /v1/admin/projects 的列表行（API-PATHS §7.1）。 */
export interface ProjectSummary {
  project_id: string;
  tenant_id?: string | null;
  key_count: number;
  document_count: number;
  created_at?: string | null;
}

export interface ProjectPage {
  items: ProjectSummary[];
  count: number;
  has_more: boolean;
}

export interface ProjectListQuery {
  tenant_id?: string;
  limit?: number;
  offset?: number;
}
