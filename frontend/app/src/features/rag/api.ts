import { endpoints, withQuery } from '@/api/endpoints';
import { request } from '@/api/http';
import type { RagChunkPage, RagDocument, RagDocumentList } from '@/features/rag/types';

/** GET /v1/admin/rag/documents：project_id 必填，limit 可选（§4.7）。 */
export async function fetchRagDocuments(projectId: string): Promise<RagDocumentList> {
  return request<RagDocumentList>(withQuery(endpoints.rag.documents, { project_id: projectId }), {
    method: 'GET',
  });
}

/** POST /v1/admin/rag/documents：multipart 上传，命中重复返回 200。 */
export async function uploadRagDocument(file: File, projectId: string): Promise<RagDocument> {
  const form = new FormData();
  form.append('file', file);
  form.append('project_id', projectId);
  return request<RagDocument>(endpoints.rag.documents, { method: 'POST', body: form });
}

export async function reindexRagDocument(docId: string, projectId: string): Promise<RagDocument> {
  return request<RagDocument>(withQuery(endpoints.rag.reindex(docId), { project_id: projectId }), {
    method: 'POST',
  });
}

export async function deleteRagDocument(docId: string, projectId: string): Promise<void> {
  return request<void>(withQuery(endpoints.rag.remove(docId), { project_id: projectId }), {
    method: 'DELETE',
  });
}

/** 切片列表（§7.1 已实现）。 */
export async function fetchRagChunks(
  docId: string,
  projectId: string,
  limit = 50,
  offset = 0,
): Promise<RagChunkPage> {
  return request<RagChunkPage>(
    withQuery(endpoints.rag.chunks(docId), { project_id: projectId, limit, offset }),
    { method: 'GET' },
  );
}
