import AddIcon from '@mui/icons-material/Add';
import AddPhotoAlternateIcon from '@mui/icons-material/AddPhotoAlternate';
import ArrowBackIcon from '@mui/icons-material/ArrowBack';
import ArrowBackIosNewIcon from '@mui/icons-material/ArrowBackIosNew';
import ArrowForwardIosIcon from '@mui/icons-material/ArrowForwardIos';
import CheckCircleIcon from '@mui/icons-material/CheckCircle';
import CollectionsIcon from '@mui/icons-material/Collections';
import DeleteOutlineIcon from '@mui/icons-material/DeleteOutline';
import HomeIcon from '@mui/icons-material/Home';
import PlayCircleIcon from '@mui/icons-material/PlayCircle';
import RefreshIcon from '@mui/icons-material/Refresh';
import RestoreIcon from '@mui/icons-material/Restore';
import SettingsIcon from '@mui/icons-material/Settings';
import ShuffleIcon from '@mui/icons-material/Shuffle';
import {
  Alert,
  AppBar,
  Box,
  BottomNavigation,
  BottomNavigationAction,
  Button,
  Card,
  CardActionArea,
  Chip,
  CircularProgress,
  Container,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Divider,
  FormControl,
  IconButton,
  InputLabel,
  LinearProgress,
  List,
  ListItemButton,
  ListItemText,
  MenuItem,
  Select,
  Stack,
  TextField,
  Toolbar,
  Tooltip,
  Typography,
} from '@mui/material';
import type { ReactNode } from 'react';
import { useEffect, useMemo, useState } from 'react';
import { Navigate, Route, Routes, useLocation, useNavigate, useParams } from 'react-router-dom';
import { useInfiniteQuery, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  addAlbumPhotos,
  cancelScan,
  createAlbum,
  excludePhotos,
  getAlbum,
  getIndexStatus,
  getPhoto,
  listAlbums,
  listExcludedPhotos,
  listPhotos,
  restorePhotos,
  startScan,
} from './api';
import type { AlbumSummary, PhotoDetail, PhotoListResponse, PhotoSummary } from './types';

const navigationItems = [
  {
    label: 'ホーム',
    value: '/home',
    icon: <HomeIcon />,
  },
  {
    label: 'アルバム',
    value: '/albums',
    icon: <CollectionsIcon />,
  },
  {
    label: '設定',
    value: '/settings',
    icon: <SettingsIcon />,
  },
];

const dateFormatter = new Intl.DateTimeFormat('ja-JP', {
  year: 'numeric',
  month: 'short',
  day: 'numeric',
  hour: '2-digit',
  minute: '2-digit',
});

export function App() {
  const location = useLocation();
  const navigate = useNavigate();
  const currentPath = navigationItems.find((item) => location.pathname.startsWith(item.value))?.value ?? '/home';

  return (
    <Box sx={{ minHeight: '100vh', bgcolor: 'background.default', pb: 8 }}>
      <AppBar
        position="sticky"
        color="transparent"
        elevation={0}
        sx={{ borderBottom: 1, borderColor: 'divider', backdropFilter: 'blur(16px)' }}
      >
        <Toolbar sx={{ gap: 2 }}>
          <Typography variant="h6" component="div" sx={{ fontWeight: 800 }}>
            Pholio
          </Typography>
        </Toolbar>
      </AppBar>

      <Container maxWidth="xl" sx={{ py: { xs: 2, sm: 3 } }}>
        <Routes>
          <Route path="/" element={<Navigate to="/home" replace />} />
          <Route path="/home" element={<GalleryScreen />} />
          <Route path="/albums" element={<AlbumsScreen />} />
          <Route path="/albums/:albumId" element={<AlbumDetailScreen />} />
          <Route path="/settings" element={<SettingsScreen />} />
        </Routes>
      </Container>

      <BottomNavigation
        value={currentPath}
        onChange={(_, value: string) => navigate(value)}
        sx={{
          position: 'fixed',
          right: 0,
          bottom: 0,
          left: 0,
          borderTop: 1,
          borderColor: 'divider',
          zIndex: (theme) => theme.zIndex.appBar,
        }}
      >
        {navigationItems.map((item) => (
          <BottomNavigationAction
            key={item.value}
            label={item.label}
            value={item.value}
            icon={item.icon}
          />
        ))}
      </BottomNavigation>
    </Box>
  );
}

function GalleryScreen() {
  return <GalleryView title="ホーム" />;
}

function AlbumDetailScreen() {
  const navigate = useNavigate();
  const { albumId } = useParams();
  const albumQuery = useQuery({
    queryKey: ['album', albumId],
    queryFn: () => getAlbum(albumId ?? ''),
    enabled: albumId !== undefined,
  });

  if (albumId === undefined) {
    return <Navigate to="/albums" replace />;
  }

  return (
    <Stack spacing={2.5}>
      <Stack direction="row" alignItems="center" spacing={1}>
        <Tooltip title="戻る">
          <IconButton onClick={() => navigate('/albums')}>
            <ArrowBackIcon />
          </IconButton>
        </Tooltip>
        <Box>
          <Typography variant="h5" component="h1" sx={{ fontWeight: 800 }}>
            {albumQuery.data?.name ?? 'アルバム'}
          </Typography>
          <Typography variant="body2" color="text.secondary">
            {albumQuery.data === undefined ? '' : `${albumQuery.data.photoCount.toLocaleString()} 件`}
          </Typography>
        </Box>
      </Stack>

      <GalleryView
        albumId={albumId}
        title={albumQuery.data?.name ?? 'アルバム'}
        compactHeader
      />
    </Stack>
  );
}

type GalleryViewProps = {
  albumId?: string;
  title: string;
  compactHeader?: boolean;
};

function GalleryView({ albumId, title, compactHeader = false }: GalleryViewProps) {
  const queryClient = useQueryClient();
  const [sort, setSort] = useState('takenAt');
  const [order, setOrder] = useState('desc');
  const [randomSeed, setRandomSeed] = useState(() => createSeed());
  const [selectedIds, setSelectedIds] = useState<Set<string>>(() => new Set());
  const [lastSelectedIndex, setLastSelectedIndex] = useState<number | null>(null);
  const [detailIndex, setDetailIndex] = useState<number | null>(null);
  const [albumDialogOpen, setAlbumDialogOpen] = useState(false);
  const seed = sort === 'random' ? randomSeed : undefined;

  const photosQuery = useInfiniteQuery({
    queryKey: ['photos', albumId ?? 'home', sort, order, seed],
    queryFn: ({ pageParam }) => listPhotos({
      albumId,
      sort,
      order,
      seed,
      cursor: typeof pageParam === 'string' ? pageParam : undefined,
    }),
    initialPageParam: undefined,
    getNextPageParam: (lastPage: PhotoListResponse) => lastPage.pageInfo.nextCursor,
  });
  const photos = useMemo(
    () => photosQuery.data?.pages.flatMap((page) => page.items) ?? [],
    [photosQuery.data],
  );
  const selectedPhotos = photos.filter((photo) => selectedIds.has(photo.id));
  const hasSelection = selectedIds.size > 0;

  const excludeMutation = useMutation({
    mutationFn: () => excludePhotos([...selectedIds]),
    onSuccess: async () => {
      setSelectedIds(new Set());
      await queryClient.invalidateQueries({ queryKey: ['photos'] });
      await queryClient.invalidateQueries({ queryKey: ['excludedPhotos'] });
      await queryClient.invalidateQueries({ queryKey: ['albums'] });
    },
  });

  const handleSelect = (photoId: string, index: number, event: { shiftKey: boolean }) => {
    setSelectedIds((current) => {
      const next = new Set(current);

      if (event.shiftKey && lastSelectedIndex !== null) {
        const startIndex = Math.min(lastSelectedIndex, index);
        const endIndex = Math.max(lastSelectedIndex, index);

        photos.slice(startIndex, endIndex + 1).forEach((photo) => next.add(photo.id));
      } else if (next.has(photoId)) {
        next.delete(photoId);
      } else {
        next.add(photoId);
      }

      return next;
    });
    setLastSelectedIndex(index);
  };

  const handleTileClick = (photo: PhotoSummary, index: number, event: { shiftKey: boolean }) => {
    if (hasSelection) {
      handleSelect(photo.id, index, event);

      return;
    }

    setDetailIndex(index);
  };

  return (
    <Stack spacing={2}>
      {!compactHeader && (
        <SectionHeader
          title={title}
          meta={photosQuery.data?.pages.at(-1)?.pageInfo.totalCount}
        />
      )}

      <Stack
        direction={{ xs: 'column', sm: 'row' }}
        spacing={1.5}
        alignItems={{ xs: 'stretch', sm: 'center' }}
        justifyContent="space-between"
      >
        <Stack direction="row" spacing={1} useFlexGap flexWrap="wrap">
          <FormControl size="small" sx={{ minWidth: 128 }}>
            <InputLabel id="sort-label">並び順</InputLabel>
            <Select
              labelId="sort-label"
              label="並び順"
              value={sort}
              onChange={(event) => setSort(event.target.value)}
            >
              <MenuItem value="takenAt">撮影日時</MenuItem>
              <MenuItem value="indexedAt">登録日時</MenuItem>
              <MenuItem value="name">名前</MenuItem>
              <MenuItem value="random">ランダム</MenuItem>
            </Select>
          </FormControl>
          <FormControl size="small" sx={{ minWidth: 112 }}>
            <InputLabel id="order-label">方向</InputLabel>
            <Select
              labelId="order-label"
              label="方向"
              value={order}
              disabled={sort === 'random'}
              onChange={(event) => setOrder(event.target.value)}
            >
              <MenuItem value="desc">降順</MenuItem>
              <MenuItem value="asc">昇順</MenuItem>
            </Select>
          </FormControl>
          {sort === 'random' && (
            <Tooltip title="ランダムを更新">
              <IconButton onClick={() => setRandomSeed(createSeed())}>
                <ShuffleIcon />
              </IconButton>
            </Tooltip>
          )}
        </Stack>

        {hasSelection && (
          <Stack direction="row" spacing={1} alignItems="center" useFlexGap flexWrap="wrap">
            <Chip label={`${selectedIds.size.toLocaleString()} 件選択`} color="primary" />
            <Button
              variant="outlined"
              startIcon={<AddPhotoAlternateIcon />}
              onClick={() => setAlbumDialogOpen(true)}
            >
              アルバム
            </Button>
            <Button
              variant="outlined"
              color="error"
              startIcon={<DeleteOutlineIcon />}
              disabled={excludeMutation.isPending}
              onClick={() => excludeMutation.mutate()}
            >
              ライブラリから除外
            </Button>
            <Button onClick={() => setSelectedIds(new Set())}>
              解除
            </Button>
          </Stack>
        )}
      </Stack>

      {photosQuery.isLoading && <LoadingState />}
      {photosQuery.isError && <Alert severity="error">{photosQuery.error.message}</Alert>}
      {excludeMutation.isError && <Alert severity="error">{excludeMutation.error.message}</Alert>}

      <PhotoGrid
        photos={photos}
        selectedIds={selectedIds}
        onTileClicked={handleTileClick}
        onSelectClicked={handleSelect}
      />

      <Stack alignItems="center" sx={{ py: 1 }}>
        {photosQuery.hasNextPage ? (
          <Button
            variant="outlined"
            disabled={photosQuery.isFetchingNextPage}
            onClick={() => photosQuery.fetchNextPage()}
          >
            {photosQuery.isFetchingNextPage ? '読み込み中' : 'さらに読み込む'}
          </Button>
        ) : (
          photos.length > 0 && <Typography variant="body2" color="text.secondary">最後まで表示しました</Typography>
        )}
      </Stack>

      <PhotoDetailDialog
        photos={photos}
        index={detailIndex}
        onIndexChanged={setDetailIndex}
        onClosed={() => setDetailIndex(null)}
      />

      <AlbumPickerDialog
        open={albumDialogOpen}
        photoIds={selectedPhotos.map((photo) => photo.id)}
        onClosed={() => setAlbumDialogOpen(false)}
        onCompleted={() => {
          setSelectedIds(new Set());
          setAlbumDialogOpen(false);
        }}
      />
    </Stack>
  );
}

type PhotoGridProps = {
  photos: PhotoSummary[];
  selectedIds: Set<string>;
  onTileClicked: (photo: PhotoSummary, index: number, event: { shiftKey: boolean }) => void;
  onSelectClicked: (photoId: string, index: number, event: { shiftKey: boolean }) => void;
};

function PhotoGrid({ photos, selectedIds, onTileClicked, onSelectClicked }: PhotoGridProps) {
  if (photos.length === 0) {
    return <EmptyState title="写真がありません" />;
  }

  return (
    <Box
      data-testid="photo-grid"
      sx={{
        display: 'grid',
        gridTemplateColumns: 'repeat(auto-fill, minmax(132px, 1fr))',
        gap: { xs: 0.75, sm: 1 },
      }}
    >
      {photos.map((photo, index) => {
        const selected = selectedIds.has(photo.id);

        return (
          <Box
            key={photo.id}
            role="button"
            tabIndex={0}
            onClick={(event) => onTileClicked(photo, index, event)}
            onKeyDown={(event) => {
              if (event.key === 'Enter') {
                onTileClicked(photo, index, event);
              }
            }}
            sx={{
              position: 'relative',
              aspectRatio: '1 / 1',
              overflow: 'hidden',
              borderRadius: 1,
              bgcolor: 'grey.200',
              outline: selected ? '3px solid' : '1px solid',
              outlineColor: selected ? 'primary.main' : 'divider',
              cursor: 'pointer',
            }}
          >
            <Box
              component="img"
              src={photo.thumbnail.gridMd}
              alt={photo.filename}
              loading="lazy"
              sx={{
                width: '100%',
                height: '100%',
                display: 'block',
                objectFit: 'cover',
              }}
            />
            {photo.mediaType === 'video' && (
              <PlayCircleIcon
                sx={{
                  position: 'absolute',
                  right: 8,
                  bottom: 8,
                  color: 'common.white',
                  filter: 'drop-shadow(0 1px 4px rgba(0,0,0,0.5))',
                }}
              />
            )}
            <Tooltip title={selected ? '選択解除' : '選択'}>
              <IconButton
                size="small"
                aria-label={selected ? '選択解除' : '選択'}
                data-testid="select-photo"
                onClick={(event) => {
                  event.stopPropagation();
                  onSelectClicked(photo.id, index, event);
                }}
                sx={{
                  position: 'absolute',
                  top: 6,
                  left: 6,
                  width: 32,
                  height: 32,
                  color: selected ? 'primary.main' : 'common.white',
                  bgcolor: selected ? 'background.paper' : 'rgba(0,0,0,0.38)',
                  '&:hover': { bgcolor: selected ? 'background.paper' : 'rgba(0,0,0,0.54)' },
                }}
              >
                <CheckCircleIcon fontSize="small" />
              </IconButton>
            </Tooltip>
          </Box>
        );
      })}
    </Box>
  );
}

type PhotoDetailDialogProps = {
  photos: PhotoSummary[];
  index: number | null;
  onIndexChanged: (index: number) => void;
  onClosed: () => void;
};

function PhotoDetailDialog({ photos, index, onIndexChanged, onClosed }: PhotoDetailDialogProps) {
  const photo = index === null ? undefined : photos[index];
  const [zoomed, setZoomed] = useState(false);
  const detailQuery = useQuery({
    queryKey: ['photo', photo?.id],
    queryFn: () => getPhoto(photo?.id ?? ''),
    enabled: photo !== undefined,
  });
  const detail = detailQuery.data;
  const media = detail ?? photo;
  const imageSrc = useProgressiveImage(detail);
  const hasPrevious = index !== null && index > 0;
  const hasNext = index !== null && index < photos.length - 1;

  useEffect(() => {
    setZoomed(false);
  }, [photo?.id]);

  return (
    <Dialog open={photo !== undefined} onClose={onClosed} fullScreen>
      <AppBar position="static" color="transparent" elevation={0}>
        <Toolbar sx={{ gap: 1 }}>
          <Tooltip title="閉じる">
            <IconButton edge="start" onClick={onClosed}>
              <ArrowBackIcon />
            </IconButton>
          </Tooltip>
          <Box sx={{ minWidth: 0 }}>
            <Typography variant="subtitle1" sx={{ fontWeight: 700 }} noWrap>
              {media?.filename ?? ''}
            </Typography>
            {media !== undefined && (
              <Typography variant="caption" color="text.secondary">
                {formatDate(media.takenAt)}
              </Typography>
            )}
          </Box>
        </Toolbar>
      </AppBar>
      <DialogContent sx={{ p: 0, bgcolor: 'grey.950', color: 'common.white' }}>
        <Box sx={{ position: 'relative', minHeight: 'calc(100vh - 64px)' }}>
          {detailQuery.isLoading && <LoadingState dark />}
          {detailQuery.isError && <Alert severity="error">{detailQuery.error.message}</Alert>}
          {media?.mediaType === 'video' ? (
            <Box
              component="video"
              src={(detail as PhotoDetail | undefined)?.originalUrl}
              poster={media.thumbnail.previewLg}
              controls
              sx={{ width: '100%', height: 'calc(100vh - 64px)', objectFit: 'contain' }}
            />
          ) : (
            <Box
              component="img"
              src={imageSrc ?? media?.thumbnail.previewLg}
              alt={media?.filename ?? ''}
              onClick={() => setZoomed((current) => !current)}
              sx={{
                width: '100%',
                height: 'calc(100vh - 64px)',
                objectFit: zoomed ? 'none' : 'contain',
                cursor: 'zoom-in',
                display: 'block',
              }}
            />
          )}

          {hasPrevious && (
            <NavButton side="left" onClicked={() => onIndexChanged((index ?? 0) - 1)}>
              <ArrowBackIosNewIcon />
            </NavButton>
          )}
          {hasNext && (
            <NavButton side="right" onClicked={() => onIndexChanged((index ?? 0) + 1)}>
              <ArrowForwardIosIcon />
            </NavButton>
          )}
        </Box>
      </DialogContent>
    </Dialog>
  );
}

type NavButtonProps = {
  side: 'left' | 'right';
  children: ReactNode;
  onClicked: () => void;
};

function NavButton({ side, children, onClicked }: NavButtonProps) {
  return (
    <IconButton
      onClick={onClicked}
      sx={{
        position: 'absolute',
        top: '50%',
        [side]: 16,
        transform: 'translateY(-50%)',
        color: 'common.white',
        bgcolor: 'rgba(0,0,0,0.42)',
        '&:hover': { bgcolor: 'rgba(0,0,0,0.62)' },
      }}
    >
      {children}
    </IconButton>
  );
}

function AlbumsScreen() {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const [name, setName] = useState('');
  const albumsQuery = useQuery({
    queryKey: ['albums'],
    queryFn: listAlbums,
  });
  const createMutation = useMutation({
    mutationFn: createAlbum,
    onSuccess: async () => {
      setName('');
      await queryClient.invalidateQueries({ queryKey: ['albums'] });
    },
  });
  const albums = albumsQuery.data?.items ?? [];

  return (
    <Stack spacing={2.5}>
      <SectionHeader title="アルバム" meta={albums.length} />

      <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1.5}>
        <TextField
          size="small"
          label="新しいアルバム"
          value={name}
          onChange={(event) => setName(event.target.value)}
          sx={{ maxWidth: 360 }}
        />
        <Button
          variant="contained"
          startIcon={<AddIcon />}
          disabled={name.trim().length === 0 || createMutation.isPending}
          onClick={() => createMutation.mutate(name)}
        >
          作成
        </Button>
      </Stack>

      {createMutation.isError && <Alert severity="error">{createMutation.error.message}</Alert>}
      {albumsQuery.isLoading && <LoadingState />}
      {albumsQuery.isError && <Alert severity="error">{albumsQuery.error.message}</Alert>}

      <Box
        sx={{
          display: 'grid',
          gridTemplateColumns: 'repeat(auto-fill, minmax(190px, 1fr))',
          gap: 1.5,
        }}
      >
        {albums.map((album) => (
          <AlbumCard
            key={album.id}
            album={album}
            onClicked={() => navigate(`/albums/${album.id}`)}
          />
        ))}
      </Box>

      {albums.length === 0 && !albumsQuery.isLoading && <EmptyState title="アルバムがありません" />}
    </Stack>
  );
}

type AlbumCardProps = {
  album: AlbumSummary;
  onClicked: () => void;
};

function AlbumCard({ album, onClicked }: AlbumCardProps) {
  return (
    <Card variant="outlined" sx={{ borderRadius: 1, overflow: 'hidden' }}>
      <CardActionArea onClick={onClicked}>
        <Box sx={{ aspectRatio: '4 / 3', bgcolor: 'grey.200' }}>
          {album.coverPhoto === undefined ? (
            <Stack alignItems="center" justifyContent="center" sx={{ height: '100%' }}>
              <CollectionsIcon color="disabled" />
            </Stack>
          ) : (
            <Box
              component="img"
              src={album.coverPhoto.thumbnailUrl}
              alt={album.name}
              loading="lazy"
              sx={{ width: '100%', height: '100%', display: 'block', objectFit: 'cover' }}
            />
          )}
        </Box>
        <Box sx={{ p: 1.5 }}>
          <Typography sx={{ fontWeight: 700 }} noWrap>
            {album.name}
          </Typography>
          <Typography variant="body2" color="text.secondary">
            {album.photoCount.toLocaleString()} 件
          </Typography>
        </Box>
      </CardActionArea>
    </Card>
  );
}

type AlbumPickerDialogProps = {
  open: boolean;
  photoIds: string[];
  onClosed: () => void;
  onCompleted: () => void;
};

function AlbumPickerDialog({ open, photoIds, onClosed, onCompleted }: AlbumPickerDialogProps) {
  const queryClient = useQueryClient();
  const albumsQuery = useQuery({
    queryKey: ['albums'],
    queryFn: listAlbums,
    enabled: open,
  });
  const addMutation = useMutation({
    mutationFn: (albumId: string) => addAlbumPhotos(albumId, photoIds),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['albums'] });
      onCompleted();
    },
  });

  return (
    <Dialog open={open} onClose={onClosed} fullWidth maxWidth="xs">
      <DialogTitle>アルバムに追加</DialogTitle>
      <DialogContent>
        {albumsQuery.isLoading && <LoadingState />}
        {albumsQuery.isError && <Alert severity="error">{albumsQuery.error.message}</Alert>}
        {addMutation.isError && <Alert severity="error">{addMutation.error.message}</Alert>}
        <List>
          {(albumsQuery.data?.items ?? []).map((album) => (
            <ListItemButton
              key={album.id}
              disabled={addMutation.isPending}
              onClick={() => addMutation.mutate(album.id)}
            >
              <ListItemText primary={album.name} secondary={`${album.photoCount.toLocaleString()} 件`} />
            </ListItemButton>
          ))}
        </List>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClosed}>閉じる</Button>
      </DialogActions>
    </Dialog>
  );
}

function SettingsScreen() {
  const queryClient = useQueryClient();
  const statusQuery = useQuery({
    queryKey: ['indexStatus'],
    queryFn: getIndexStatus,
    refetchInterval: 2500,
  });
  const scanMutation = useMutation({
    mutationFn: startScan,
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['indexStatus'] });
    },
  });
  const cancelMutation = useMutation({
    mutationFn: cancelScan,
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['indexStatus'] });
    },
  });
  const status = statusQuery.data;
  const currentJob = status?.currentJob;
  const isRunning = currentJob?.status === 'running' || currentJob?.status === 'queued';

  return (
    <Stack spacing={3}>
      <SectionHeader title="設定" />

      <Stack spacing={1.5}>
        <Typography variant="h6" sx={{ fontWeight: 800 }}>インデックス</Typography>
        {statusQuery.isLoading && <LinearProgress />}
        {statusQuery.isError && <Alert severity="error">{statusQuery.error.message}</Alert>}
        {scanMutation.isError && <Alert severity="error">{scanMutation.error.message}</Alert>}
        {cancelMutation.isError && <Alert severity="error">{cancelMutation.error.message}</Alert>}
        {status !== undefined && (
          <Stack direction="row" spacing={1} useFlexGap flexWrap="wrap">
            <Chip label={`状態: ${status.status}`} />
            <Chip label={`revision: ${status.libraryRevision}`} />
            <Chip label={`thumbnail pending: ${status.thumbnailQueue.pending}`} />
            <Chip label={`thumbnail ready: ${status.thumbnailQueue.ready}`} />
            <Chip label={`thumbnail failed: ${status.thumbnailQueue.failed}`} />
          </Stack>
        )}
        {currentJob !== undefined && (
          <Stack spacing={0.5}>
            <Typography variant="body2" color="text.secondary">
              {currentJob.mode} / {currentJob.status} / media {currentJob.mediaFilesSeen.toLocaleString()} / errors {currentJob.errorsCount.toLocaleString()}
            </Typography>
            {currentJob.currentRelativePath !== undefined && (
              <Typography variant="caption" color="text.secondary" noWrap>
                {currentJob.currentRelativePath}
              </Typography>
            )}
          </Stack>
        )}
        <Stack direction="row" spacing={1} useFlexGap flexWrap="wrap">
          <Button
            variant="contained"
            startIcon={<RefreshIcon />}
            disabled={scanMutation.isPending || isRunning}
            onClick={() => scanMutation.mutate('diff')}
          >
            差分 scan
          </Button>
          <Button
            variant="outlined"
            disabled={scanMutation.isPending || isRunning}
            onClick={() => scanMutation.mutate('full')}
          >
            full scan
          </Button>
          <Button
            color="warning"
            disabled={!isRunning || currentJob === undefined || cancelMutation.isPending}
            onClick={() => {
              if (currentJob !== undefined) {
                cancelMutation.mutate(currentJob.id);
              }
            }}
          >
            cancel
          </Button>
        </Stack>
      </Stack>

      <Divider />

      <ExcludedPhotos />
    </Stack>
  );
}

function ExcludedPhotos() {
  const queryClient = useQueryClient();
  const excludedQuery = useInfiniteQuery({
    queryKey: ['excludedPhotos'],
    queryFn: ({ pageParam }) => listExcludedPhotos(typeof pageParam === 'string' ? pageParam : undefined),
    initialPageParam: undefined,
    getNextPageParam: (lastPage: PhotoListResponse) => lastPage.pageInfo.nextCursor,
  });
  const restoreMutation = useMutation({
    mutationFn: restorePhotos,
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['excludedPhotos'] });
      await queryClient.invalidateQueries({ queryKey: ['photos'] });
      await queryClient.invalidateQueries({ queryKey: ['albums'] });
    },
  });
  const photos = excludedQuery.data?.pages.flatMap((page) => page.items) ?? [];

  return (
    <Stack spacing={1.5}>
      <Typography variant="h6" sx={{ fontWeight: 800 }}>除外済み</Typography>
      {excludedQuery.isLoading && <LoadingState />}
      {excludedQuery.isError && <Alert severity="error">{excludedQuery.error.message}</Alert>}
      {restoreMutation.isError && <Alert severity="error">{restoreMutation.error.message}</Alert>}
      <Box
        sx={{
          display: 'grid',
          gridTemplateColumns: 'repeat(auto-fill, minmax(180px, 1fr))',
          gap: 1,
        }}
      >
        {photos.map((photo) => (
          <Card key={photo.id} variant="outlined" sx={{ borderRadius: 1, overflow: 'hidden' }}>
            <Box sx={{ aspectRatio: '1 / 1', bgcolor: 'grey.200' }}>
              <Box
                component="img"
                src={photo.thumbnail.gridMd}
                alt={photo.filename}
                loading="lazy"
                sx={{ width: '100%', height: '100%', objectFit: 'cover', display: 'block' }}
              />
            </Box>
            <Stack spacing={1} sx={{ p: 1.25 }}>
              <Typography variant="body2" noWrap>{photo.filename}</Typography>
              <Button
                size="small"
                startIcon={<RestoreIcon />}
                disabled={restoreMutation.isPending}
                onClick={() => restoreMutation.mutate([photo.id])}
              >
                復元
              </Button>
            </Stack>
          </Card>
        ))}
      </Box>
      {photos.length === 0 && !excludedQuery.isLoading && <EmptyState title="除外済み写真はありません" />}
      {excludedQuery.hasNextPage && (
        <Stack alignItems="center">
          <Button
            variant="outlined"
            disabled={excludedQuery.isFetchingNextPage}
            onClick={() => excludedQuery.fetchNextPage()}
          >
            {excludedQuery.isFetchingNextPage ? '読み込み中' : 'さらに読み込む'}
          </Button>
        </Stack>
      )}
    </Stack>
  );
}

type SectionHeaderProps = {
  title: string;
  meta?: number;
};

function SectionHeader({ title, meta }: SectionHeaderProps) {
  return (
    <Stack direction="row" alignItems="baseline" spacing={1.5}>
      <Typography variant="h4" component="h1" sx={{ fontWeight: 900, letterSpacing: 0 }}>
        {title}
      </Typography>
      {meta !== undefined && (
        <Typography color="text.secondary">
          {meta.toLocaleString()} 件
        </Typography>
      )}
    </Stack>
  );
}

type EmptyStateProps = {
  title: string;
};

function EmptyState({ title }: EmptyStateProps) {
  return (
    <Stack alignItems="center" justifyContent="center" sx={{ minHeight: 180, color: 'text.secondary' }}>
      <Typography>{title}</Typography>
    </Stack>
  );
}

type LoadingStateProps = {
  dark?: boolean;
};

function LoadingState({ dark = false }: LoadingStateProps) {
  return (
    <Stack alignItems="center" justifyContent="center" sx={{ minHeight: 160 }}>
      <CircularProgress sx={{ color: dark ? 'common.white' : undefined }} />
    </Stack>
  );
}

function useProgressiveImage(detail: PhotoDetail | undefined): string | undefined {
  const [source, setSource] = useState<string | undefined>(undefined);

  useEffect(() => {
    if (detail === undefined) {
      setSource(undefined);

      return;
    }

    if (detail.mediaType === 'video') {
      setSource(detail.thumbnail.previewLg);

      return;
    }

    setSource(detail.thumbnail.previewLg);
    const timer = window.setTimeout(() => setSource(detail.originalUrl), 80);

    return () => window.clearTimeout(timer);
  }, [detail]);

  return source;
}

function formatDate(isoText: string): string {
  return dateFormatter.format(new Date(isoText));
}

function createSeed(): string {
  const values = new Uint32Array(2);
  window.crypto.getRandomValues(values);

  return Array.from(values).map((value) => value.toString(16).padStart(8, '0')).join('');
}
