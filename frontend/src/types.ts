export type MediaType = 'image' | 'video';

export type ThumbnailUrls = {
  gridSm: string;
  gridMd: string;
  previewLg: string;
};

export type PhotoSummary = {
  id: string;
  mediaType: MediaType;
  filename: string;
  takenAt: string;
  takenAtEpochMs: number;
  takenAtSource: string;
  indexedAt: string;
  indexedAtEpochMs: number;
  width?: number;
  height?: number;
  durationMs?: number;
  thumbnail: ThumbnailUrls;
};

export type PageInfo = {
  hasMore: boolean;
  nextCursor?: string;
  sort: string;
  order: string;
  seed?: string;
  limit: number;
  totalCount: number;
  libraryRevision: number;
};

export type PhotoListResponse = {
  items: PhotoSummary[];
  pageInfo: PageInfo;
};

export type PhotoDetail = {
  id: string;
  mediaType: MediaType;
  filename: string;
  takenAt: string;
  takenAtEpochMs: number;
  takenAtSource: string;
  width?: number;
  height?: number;
  durationMs?: number;
  gps?: {
    lat: number;
    lng: number;
    alt?: number;
    source: string;
  };
  camera?: {
    make?: string;
    model?: string;
  };
  thumbnail: {
    previewLg: string;
  };
  originalUrl: string;
};

export type AlbumSummary = {
  id: string;
  name: string;
  photoCount: number;
  coverPhoto?: {
    id: string;
    thumbnailUrl: string;
  };
  createdAt: string;
  updatedAt: string;
};

export type AlbumDetail = {
  id: string;
  name: string;
  photoCount: number;
  createdAt: string;
  updatedAt: string;
};

export type AlbumListResponse = {
  items: AlbumSummary[];
};

export type ScanJob = {
  id: string;
  mode: string;
  status: string;
  filesSeen: number;
  mediaFilesSeen: number;
  sidecarJsonSeen: number;
  photosInserted: number;
  photosUpdated: number;
  photosUnchanged: number;
  photosMarkedMissing: number;
  thumbnailTasksEnqueued: number;
  errorsCount: number;
  currentRelativePath?: string;
  startedAt?: string;
};

export type IndexStatus = {
  status: string;
  currentJob?: ScanJob;
  thumbnailQueue: {
    pending: number;
    ready: number;
    failed: number;
  };
  libraryRevision: number;
};

export type MutationCountResponse = {
  excluded?: number;
  restored?: number;
  notFound: string[];
};

export type AddAlbumPhotosResponse = {
  added: number;
  alreadyPresent: number;
  notFound: string[];
};
