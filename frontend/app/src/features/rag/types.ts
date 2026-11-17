/** 与后端 RagDocumentResDTO 一致（snake_case）。 */
export interface RagDocument {
  doc_id: string;
  project_id: string;
  source: string;
  content_type: string;
  size_bytes: number;
  content_hash: string;
  chunk_count: number;
  index_status: string;
  embedding_model: string;
  embedding_dimension?: number | null;
  embedding_duration_ms?: number | null;
  embedding_cost?: number | null;
  error_message?: string | null;
  duplicated: boolean;
  reindex_required: boolean;
  created_at: string;
  updated_at: string;
}

export interface RagDocumentList {
  documents: RagDocument[];
  count: number;
}

/** GET /v1/admin/rag/documents/{docId}/chunks 的切片行。 */
export interface RagChunk {
  chunk_id: string;
  doc_id: string;
  chunk_index: number;
  content: string;
  char_count?: number;
  index_status?: string;
  created_at?: string;
}

export interface RagChunkPage {
  items: RagChunk[];
  count: number;
  has_more: boolean;
}

/** gateway /v1/rag/search 的命中行（REQ-5.3）。 */
export interface RagSearchHit {
  chunk_id: string;
  doc_id: string;
  source: string;
  chunk_index: number;
  content: string;
  score: number;
  rerank_score?: number | null;
  match_type: string;
  vector_score?: number | null;
  keyword_score?: number | null;
  rrf_score?: number | null;
}

/** 与后端 RagSearchResDTO 一致。 */
export interface RagSearchResult {
  query: string;
  project_id: string;
  count: number;
  no_hit: boolean;
  reranked: boolean;
  results: RagSearchHit[];
}

/** 与后端 RagCitationResDTO 一致；index 从 1 开始，对应答案里的 [n]。 */
export interface RagAnswerCitation {
  index: number;
  doc_id: string;
  chunk_id: string;
  source: string;
  score: number;
}

export interface RagAnswerUsage {
  prompt_tokens: number;
  completion_tokens: number;
  total_tokens: number;
}

/** 与后端 RagAnswerResDTO 一致。 */
export interface RagAnswerResult {
  query: string;
  project_id: string;
  answer: string;
  no_hit: boolean;
  free_answer: boolean;
  citations: RagAnswerCitation[];
  usage: RagAnswerUsage;
  model: string;
}

export const INDEX_STATUS_LABEL: Record<string, string> = {
  PENDING: '待索引',
  PROCESSING: '索引中',
  READY: '已索引',
  FAILED: '失败',
};

export function indexStatusLabel(status: string): string {
  return INDEX_STATUS_LABEL[status] ?? status;
}

export function formatBytes(bytes?: number): string {
  if (bytes === undefined || bytes === null) {
    return '—';
  }
  if (bytes < 1024) {
    return `${bytes} B`;
  }
  if (bytes < 1024 * 1024) {
    return `${(bytes / 1024).toFixed(1)} KB`;
  }
  return `${(bytes / 1024 / 1024).toFixed(2)} MB`;
}
