/** GET /v1/admin/projects 的列表行（API-PATHS §4.9）。 */
export interface ProjectSummary {
  project_id: string;
  tenant_id?: string | null;
  /** 项目表未登记时回落为 project_id。 */
  name: string;
  /** ACTIVE / DISABLED；未登记时为 ACTIVE。 */
  status: string;
  created_at?: string | null;
}

export interface ProjectPage {
  items: ProjectSummary[];
  count: number;
  has_more: boolean;
}

export interface ProjectListQuery {
  tenant_id?: string;
  status?: 'ACTIVE' | 'DISABLED';
  limit?: number;
  offset?: number;
}
