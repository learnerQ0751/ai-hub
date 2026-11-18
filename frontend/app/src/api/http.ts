import { API_BASE_URL } from '@/api/endpoints';

/**
 * 控制台唯一的 HTTP 出口。
 * 会话凭证只存在于后端下发的 httpOnly cookie 中：这里只开 credentials: 'include'，
 * 不读、不写、不转发任何 token，也不碰 localStorage / sessionStorage。
 */
export class ApiError extends Error {
  readonly status: number;

  constructor(status: number, message: string) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
  }
}

type UnauthorizedHandler = () => void;

let unauthorizedHandler: UnauthorizedHandler | null = null;

export function setUnauthorizedHandler(handler: UnauthorizedHandler | null): void {
  unauthorizedHandler = handler;
}

export function handleApiError(error: unknown): void {
  if (error instanceof ApiError && (error.status === 401 || error.status === 403)) {
    unauthorizedHandler?.();
  }
}

/** 后端错误体为 { error: { message, type, param, code } }；读不到就返回 null。 */
async function readErrorMessage(response: Response): Promise<string | null> {
  try {
    const body = (await response.json()) as { error?: { message?: unknown } };
    const message = body?.error?.message;
    return typeof message === 'string' && message.trim() ? message : null;
  } catch {
    return null;
  }
}

export async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const headers = new Headers(init?.headers);
  // FormData 由浏览器自动生成 multipart 边界，手动设置 Content-Type 会破坏上传。
  if (init?.body && !(init.body instanceof FormData) && !headers.has('Content-Type')) {
    headers.set('Content-Type', 'application/json');
  }

  let response: Response;
  try {
    response = await fetch(`${API_BASE_URL}${path}`, {
      ...init,
      headers,
      credentials: 'include',
    });
  } catch {
    throw new ApiError(0, '无法连接控制台服务，请检查网络后重试');
  }

  if (response.status === 401 || response.status === 403) {
    const error = new ApiError(response.status, '登录状态已失效，请重新登录');
    handleApiError(error);
    throw error;
  }

  if (!response.ok) {
    const message = await readErrorMessage(response);
    throw new ApiError(response.status, message ?? `请求失败（HTTP ${response.status}）`);
  }

  if (response.status === 204) {
    return undefined as T;
  }

  // DELETE 等端点成功时可能返回空响应体，不能直接按 JSON 解析。
  const text = await response.text();
  if (!text) {
    return undefined as T;
  }

  try {
    return JSON.parse(text) as T;
  } catch {
    // 反代把 200 变成登录页 HTML 之类的情况，避免把 SyntaxError 抛给页面。
    throw new ApiError(response.status, '响应格式不正确，请稍后重试');
  }
}
