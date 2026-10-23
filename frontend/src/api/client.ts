import type {
  ApplicationDetail,
  ApplicationSummary,
  CompanyProfile,
  DecisionResult,
  DocumentItem,
  DraftRequest,
  FeedCard,
  LoginResponse,
  Product,
  PurposeOption,
  RecalculateResponse,
  Recommendation,
  SendCodeResponse,
  TaskCard,
  TaskListItem,
  User,
} from './types';

const TOKEN_KEY = 'credit.token';

/** Ошибка API в формате problem+json. */
export class ApiError extends Error {
  status: number;
  code?: string;
  fields?: Record<string, string>;

  constructor(status: number, message: string, code?: string, fields?: Record<string, string>) {
    super(message);
    this.status = status;
    this.code = code;
    this.fields = fields;
  }
}

export function getToken(): string | null {
  try {
    return sessionStorage.getItem(TOKEN_KEY);
  } catch {
    return null;
  }
}

export function setToken(token: string | null) {
  try {
    if (token) {
      sessionStorage.setItem(TOKEN_KEY, token);
    } else {
      sessionStorage.removeItem(TOKEN_KEY);
    }
  } catch {
    // хранилище недоступно: токен живёт только до перезагрузки
  }
}

let onUnauthorized: () => void = () => {};

export function setUnauthorizedHandler(handler: () => void) {
  onUnauthorized = handler;
}

async function request<T>(method: string, url: string, body?: unknown, isForm = false): Promise<T> {
  const headers: Record<string, string> = {};
  const token = getToken();
  // Вход и сброс демо-данных не требуют токена; просроченный токен сервер отклонил бы даже здесь
  const isPublic = url.endsWith('/auth/login') || url.startsWith('/api/dev/');
  if (token && !isPublic) {
    headers.Authorization = `Bearer ${token}`;
  }
  if (body !== undefined && !isForm) {
    headers['Content-Type'] = 'application/json';
  }
  let response: Response;
  try {
    response = await fetch(url, {
      method,
      headers,
      body: body === undefined ? undefined : isForm ? (body as FormData) : JSON.stringify(body),
    });
  } catch {
    throw new ApiError(0, 'Нет связи с сервером. Проверьте подключение и повторите попытку');
  }
  if (response.status === 401 && !url.endsWith('/auth/login')) {
    onUnauthorized();
  }
  if (!response.ok) {
    let message = 'Не удалось выполнить действие';
    let code: string | undefined;
    let fields: Record<string, string> | undefined;
    try {
      const problem = await response.json();
      message = problem.detail || problem.title || message;
      code = problem.code;
      fields = problem.fields;
    } catch {
      // тело ошибки не JSON
    }
    throw new ApiError(response.status, message, code, fields);
  }
  if (response.status === 204) {
    return undefined as T;
  }
  return (await response.json()) as T;
}

export const api = {
  login: (login: string, password: string) =>
    request<LoginResponse>('POST', '/api/auth/login', { login, password }),
  me: () => request<User>('GET', '/api/auth/me'),

  profile: () => request<CompanyProfile>('GET', '/api/client/profile'),
  products: () => request<Product[]>('GET', '/api/products'),
  purposes: () => request<PurposeOption[]>('GET', '/api/products/purposes'),
  recommend: (purpose: string, amount: number, termMonths: number) =>
    request<Recommendation[]>('POST', '/api/products/recommend', { purpose, amount, termMonths }),

  applications: () => request<ApplicationSummary[]>('GET', '/api/applications'),
  application: (id: number) => request<ApplicationDetail>('GET', `/api/applications/${id}`),
  createDraft: (draft: DraftRequest) => request<ApplicationDetail>('POST', '/api/applications', draft),
  updateDraft: (id: number, draft: DraftRequest) =>
    request<ApplicationDetail>('PUT', `/api/applications/${id}`, draft),
  submit: (id: number) => request<{ status: string; message: string }>('POST', `/api/applications/${id}/submit`),
  uploadDocument: (id: number, file: File, docType: string) => {
    const form = new FormData();
    form.append('file', file);
    form.append('docType', docType);
    return request<DocumentItem>('POST', `/api/applications/${id}/documents`, form, true);
  },
  answer: (id: number, requestId: number, answer: string, collateralDescription?: string) =>
    request<ApplicationDetail>('POST', `/api/applications/${id}/info-requests/${requestId}/answer`, {
      answer,
      collateralDescription,
    }),
  acceptOffer: (id: number) => request<ApplicationDetail>('POST', `/api/applications/${id}/offer/accept`),
  declineOffer: (id: number) => request<ApplicationDetail>('POST', `/api/applications/${id}/offer/decline`),
  sendCode: (id: number) => request<SendCodeResponse>('POST', `/api/applications/${id}/signing/send-code`),
  confirmCode: (id: number, code: string) =>
    request<ApplicationDetail>('POST', `/api/applications/${id}/signing/confirm`, { code }),

  feed: () => request<FeedCard[]>('GET', '/api/feed'),
  markFeedRead: (id: number) => request<void>('POST', `/api/feed/${id}/read`),

  tasks: (filter: string) => request<TaskListItem[]>('GET', `/api/bank/tasks?filter=${encodeURIComponent(filter)}`),
  takeNext: () => request<TaskCard>('POST', '/api/bank/tasks/take-next'),
  takeTask: (id: number) => request<TaskCard>('POST', `/api/bank/tasks/${id}/take`),
  task: (id: number) => request<TaskCard>('GET', `/api/bank/tasks/${id}`),
  updateCheck: (id: number, code: string, status: string, comment: string) =>
    request<TaskCard>('PUT', `/api/bank/tasks/${id}/checks/${code}`, { status, comment }),
  recalculate: (id: number, body: { confirmedAnnualTurnover?: number; amount?: number; termMonths?: number }) =>
    request<RecalculateResponse>('POST', `/api/bank/tasks/${id}/recalculate`, body),
  decide: (
    id: number,
    body: {
      result: DecisionResult;
      comment?: string;
      requestText?: string;
      counterOffer?: { amount: number; termMonths: number };
    },
  ) => request<TaskCard>('POST', `/api/bank/tasks/${id}/decision`, body),

  resetDemo: () => request<{ message: string }>('POST', '/api/dev/reset-demo'),
};

/** Скачивание файла с токеном: браузер не передаёт заголовок авторизации по обычной ссылке. */
export async function downloadFile(url: string, fileName: string) {
  const token = getToken();
  const response = await fetch(url, { headers: token ? { Authorization: `Bearer ${token}` } : {} });
  if (!response.ok) {
    throw new ApiError(response.status, 'Не удалось скачать файл');
  }
  const blob = await response.blob();
  const link = document.createElement('a');
  link.href = URL.createObjectURL(blob);
  link.download = fileName;
  link.click();
  URL.revokeObjectURL(link.href);
}
