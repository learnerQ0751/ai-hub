import { endpoints, withQuery } from '@/api/endpoints';
import { request } from '@/api/http';
import type { ProjectListQuery, ProjectPage } from '@/features/projects/types';

export async function fetchProjects(query: ProjectListQuery = {}): Promise<ProjectPage> {
  return request<ProjectPage>(withQuery(endpoints.projects.list, { ...query }), { method: 'GET' });
}
