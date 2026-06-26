import CollectionsIcon from '@mui/icons-material/Collections';
import HomeIcon from '@mui/icons-material/Home';
import SettingsIcon from '@mui/icons-material/Settings';
import {
  AppBar,
  Box,
  BottomNavigation,
  BottomNavigationAction,
  Container,
  Toolbar,
  Typography,
} from '@mui/material';
import { Navigate, Route, Routes, useLocation, useNavigate } from 'react-router-dom';

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
        <Toolbar>
          <Typography variant="h6" component="div" sx={{ fontWeight: 700 }}>
            Pholio
          </Typography>
        </Toolbar>
      </AppBar>

      <Container maxWidth="lg" sx={{ py: 4 }}>
        <Routes>
          <Route path="/" element={<Navigate to="/home" replace />} />
          <Route path="/home" element={<Placeholder title="ホーム" body="写真一覧は次の実装ステップで接続します。" />} />
          <Route path="/albums" element={<Placeholder title="アルバム" body="アルバム管理は backend API 実装後に接続します。" />} />
          <Route path="/settings" element={<Placeholder title="設定" body="スキャン状態と復元 UI をここに配置します。" />} />
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

type PlaceholderProps = {
  title: string;
  body: string;
};

function Placeholder({ title, body }: PlaceholderProps) {
  return (
    <Box sx={{ display: 'grid', gap: 1.5 }}>
      <Typography variant="h4" component="h1" sx={{ fontWeight: 800 }}>
        {title}
      </Typography>
      <Typography color="text.secondary">
        {body}
      </Typography>
    </Box>
  );
}
