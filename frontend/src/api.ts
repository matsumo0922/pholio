import type {
  AddAlbumPhotosResponse,
  AlbumDetail,
  AlbumListResponse,
  IndexStatus,
  MutationCountResponse,
  PhotoDetail,
  PhotoListResponse,
} from './types';

type PhotoPageParams = {
  albumId?: string;
  sort: string;
  order: string;
  cursor?: string;
  seed?: string;
  limit?: number;
};

type RequestOptions = {
  method?: string;
  body?: unknown;
};

async function requestJson<Response>(path: string, options: RequestOptions = {}): Promise<Response> {
  const response = await fetch(path, {
    method: options.method ?? 'GET',
    headers: options.body === undefined ? undefined : { 'Content-Type': 'application/json' },
    body: options.body === undefined ? undefined : JSON.stringify(options.body),
  });

  if (!response.ok) {
    const fallbackMessage = `API request failed: ${response.status}`;
    const errorBody = await response.json().catch(() => undefined) as { error?: { message?: string } } | undefined;

    throw new Error(errorBody?.error?.message ?? fallbackMessage);
  }

  if (response.status === 204) {
    return undefined as Response;
  }

  return response.json() as Promise<Response>;
}

function queryString(params: Record<string, string | number | undefined>): string {
  const searchParams = new URLSearchParams();

  Object.entries(params).forEach(([key, value]) => {
    if (value !== undefined && value !== '') {
      searchParams.set(key, String(value));
    }
  });

  const text = searchParams.toString();

  return text.length > 0 ? `?${text}` : '';
}

export function listPhotos(params: PhotoPageParams): Promise<PhotoListResponse> {
  const query = queryString({
    sort: params.sort,
    order: params.order,
    cursor: params.cursor,
    seed: params.seed,
    limit: params.limit ?? 120,
  });
  const path = params.albumId === undefined
    ? `/api/v1/photos${query}`
    : `/api/v1/albums/${params.albumId}/photos${query}`;

  return requestJson<PhotoListResponse>(path);
}

export function listExcludedPhotos(cursor?: string): Promise<PhotoListResponse> {
  return requestJson<PhotoListResponse>(`/api/v1/photos/excluded${queryString({ cursor, limit: 120 })}`);
}

export function getPhoto(photoId: string): Promise<PhotoDetail> {
  return requestJson<PhotoDetail>(`/api/v1/photos/${photoId}`);
}

export function excludePhotos(photoIds: string[]): Promise<MutationCountResponse> {
  return requestJson<MutationCountResponse>('/api/v1/photos:exclude', {
    method: 'POST',
    body: { photoIds },
  });
}

export function restorePhotos(photoIds: string[]): Promise<MutationCountResponse> {
  return requestJson<MutationCountResponse>('/api/v1/photos:restore', {
    method: 'POST',
    body: { photoIds },
  });
}

export function listAlbums(): Promise<AlbumListResponse> {
  return requestJson<AlbumListResponse>('/api/v1/albums');
}

export function getAlbum(albumId: string): Promise<AlbumDetail> {
  return requestJson<AlbumDetail>(`/api/v1/albums/${albumId}`);
}

export function createAlbum(name: string): Promise<AlbumDetail> {
  return requestJson<AlbumDetail>('/api/v1/albums', {
    method: 'POST',
    body: { name },
  });
}

export function addAlbumPhotos(albumId: string, photoIds: string[]): Promise<AddAlbumPhotosResponse> {
  return requestJson<AddAlbumPhotosResponse>(`/api/v1/albums/${albumId}/photos`, {
    method: 'POST',
    body: { photoIds },
  });
}

export function getIndexStatus(): Promise<IndexStatus> {
  return requestJson<IndexStatus>('/api/v1/index/status');
}

export function startScan(mode: 'full' | 'diff'): Promise<{ jobId: string; status: string }> {
  return requestJson<{ jobId: string; status: string }>('/api/v1/index/scan', {
    method: 'POST',
    body: { mode },
  });
}

export function cancelScan(jobId: string): Promise<void> {
  return requestJson<void>(`/api/v1/index/scan/${jobId}:cancel`, {
    method: 'POST',
  });
}
