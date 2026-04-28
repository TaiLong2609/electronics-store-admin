import { useMemo, useState } from 'react';
import {
  Alert,
  Box,
  Button,
  Paper,
  Stack,
  TextField,
  Typography,
} from '@mui/material';
import { useLocation, useNavigate } from 'react-router-dom';
import { api } from '../api/http.js';
import { clearAuth, setSessionFromMe, setToken } from '../auth/auth.js';

export default function LoginPage() {
  const navigate = useNavigate();
  const location = useLocation();

  const fromPath = useMemo(() => {
    const state = location.state;
    if (state && typeof state === 'object' && state.from?.pathname) {
      return state.from.pathname;
    }
    return '/dashboard';
  }, [location.state]);

  const [username, setUsername] = useState('superadmin');
  const [password, setPassword] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState('');

  const onSubmit = async (e) => {
    e.preventDefault();
    setError('');

    if (!username || !password) {
      setError('Nhập username và password');
      return;
    }

    setSubmitting(true);
    try {
      const res = await api.post('/auth/login', { username, password });
      const token = res.data?.token;
      if (!token) {
        throw new Error('Missing token');
      }
      setToken(token);
      const me = await api.get('/me');
      setSessionFromMe(me.data);
      navigate(fromPath, { replace: true });
    } catch {
      clearAuth();
      setError('Sai username/password hoặc backend chưa chạy');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Box
      sx={{
        minHeight: '100%',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        py: 6,
      }}
    >
      <Paper sx={{ p: 4, width: '100%', maxWidth: 420 }} elevation={3}>
        <Stack spacing={2} component="form" onSubmit={onSubmit}>
          <Typography variant="h5">Admin Login</Typography>
          <Typography variant="body2" color="text.secondary">
            Dùng JWT Bearer token.
          </Typography>

          {error ? <Alert severity="error">{error}</Alert> : null}

          <TextField
            label="Username"
            value={username}
            onChange={(e) => setUsername(e.target.value)}
            autoComplete="username"
            required
          />
          <TextField
            label="Password"
            type="password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            autoComplete="current-password"
            required
          />

          <Button
            type="submit"
            variant="contained"
            disabled={submitting}
          >
            {submitting ? 'Logging in...' : 'Login'}
          </Button>
        </Stack>
      </Paper>
    </Box>
  );
}
