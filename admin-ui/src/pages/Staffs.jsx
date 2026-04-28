import { useEffect, useMemo, useState } from 'react';
import {
  Alert,
  Box,
  Button,
  CircularProgress,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  MenuItem,
  Paper,
  Snackbar,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  TextField,
  Typography,
} from '@mui/material';
import { api } from '../api/http.js';

function toErrorMessage(err) {
  const status = err?.response?.status;
  const data = err?.response?.data;
  if (typeof data === 'string' && data.trim()) return `${status ?? ''} ${data}`.trim();
  if (data && typeof data === 'object') {
    const msg = data.message || data.error;
    if (typeof msg === 'string' && msg.trim()) return `${status ?? ''} ${msg}`.trim();
  }
  return status ? `Request failed (${status})` : 'Request failed';
}

const ROLE_OPTIONS = [
  { value: 'SALES', label: 'Sales (Bán hàng)' },
  { value: 'WAREHOUSE', label: 'Warehouse (Kho)' },
  { value: 'CONTENT', label: 'Content (Nội dung)' },
];

function getPrimaryRole(user) {
  const roles = user?.roles;
  if (!Array.isArray(roles) || roles.length === 0) return '';
  return roles[0] || '';
}

function StaffDialog({ open, mode, initial, onClose, onSave }) {
  const isEdit = mode === 'edit';

  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [role, setRole] = useState('SALES');
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');

  useEffect(() => {
    setUsername(initial?.username ?? '');
    setPassword('');
    setRole(getPrimaryRole(initial) || 'SALES');
    setSaving(false);
    setError('');
  }, [initial, open]);

  const title = isEdit ? 'Sửa tài khoản' : 'Thêm tài khoản';

  const onSubmit = async () => {
    setError('');
    const trimmedUsername = String(username).trim();
    const trimmedPassword = String(password).trim();

    if (!trimmedUsername) {
      setError('Username là bắt buộc');
      return;
    }

    if (!role) {
      setError('Role là bắt buộc');
      return;
    }

    if (!isEdit && !trimmedPassword) {
      setError('Password là bắt buộc');
      return;
    }

    setSaving(true);
    try {
      await onSave({
        username: trimmedUsername,
        password: trimmedPassword,
        role,
      });
      onClose();
    } catch (err) {
      setError(toErrorMessage(err));
    } finally {
      setSaving(false);
    }
  };

  return (
    <Dialog open={open} onClose={onClose} fullWidth maxWidth="sm">
      <DialogTitle>{title}</DialogTitle>
      <DialogContent>
        <Stack spacing={2} sx={{ mt: 1 }}>
          {error ? <Alert severity="error">{error}</Alert> : null}
          <TextField
            label="Username"
            value={username}
            onChange={(e) => setUsername(e.target.value)}
            fullWidth
            autoFocus
            disabled={isEdit}
          />
          <TextField
            label={isEdit ? 'Password (để trống nếu không đổi)' : 'Password'}
            type="password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            fullWidth
          />
          <TextField
            label="Role"
            value={role}
            onChange={(e) => setRole(e.target.value)}
            fullWidth
            select
          >
            {ROLE_OPTIONS.map((opt) => (
              <MenuItem key={opt.value} value={opt.value}>
                {opt.label}
              </MenuItem>
            ))}
          </TextField>
          <Alert severity="info">
            Khóa tài khoản sẽ chặn đăng nhập (JWT mới không thể tạo).
          </Alert>
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose} disabled={saving}>
          Hủy
        </Button>
        <Button variant="contained" onClick={onSubmit} disabled={saving}>
          {saving ? 'Đang lưu...' : 'Lưu'}
        </Button>
      </DialogActions>
    </Dialog>
  );
}

export default function StaffsPage() {
  const [items, setItems] = useState([]);
  const [loading, setLoading] = useState(true);
  const [pageError, setPageError] = useState('');
  const [toast, setToast] = useState('');

  const [dialogOpen, setDialogOpen] = useState(false);
  const [dialogMode, setDialogMode] = useState('create');
  const [editing, setEditing] = useState(null);

  const visibleItems = useMemo(() => {
    return items.filter((u) => {
      const roles = Array.isArray(u?.roles) ? u.roles : [];
      return !roles.includes('CUSTOMER');
    });
  }, [items]);

  const refresh = async () => {
    setPageError('');
    setLoading(true);
    try {
      const res = await api.get('/users');
      setItems(Array.isArray(res.data) ? res.data : []);
    } catch (err) {
      setPageError(toErrorMessage(err));
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    refresh();
  }, []);

  const onAdd = () => {
    setDialogMode('create');
    setEditing(null);
    setDialogOpen(true);
  };

  const onEdit = (u) => {
    setDialogMode('edit');
    setEditing(u);
    setDialogOpen(true);
  };

  const onSave = async ({ username, password, role }) => {
    if (dialogMode === 'edit') {
      const body = { roles: [role] };
      if (password) body.password = password;
      await api.put(`/users/${encodeURIComponent(username)}`, body);
      setToast('Đã cập nhật');
    } else {
      await api.post('/users', {
        username,
        password,
        roles: [role],
      });
      setToast('Đã tạo');
    }
    await refresh();
  };

  const toggleLock = async (u) => {
    const enabled = u?.enabled !== false;
    try {
      await api.patch(`/users/${encodeURIComponent(u.username)}/status`, {
        enabled: !enabled,
      });
      setToast(!enabled ? 'Đã mở khóa' : 'Đã khóa');
      await refresh();
    } catch (err) {
      setToast(toErrorMessage(err));
    }
  };

  return (
    <Stack spacing={2}>
      <Box sx={{ display: 'flex', alignItems: 'center', gap: 2 }}>
        <Typography variant="h5" sx={{ flexGrow: 1 }}>
          Tài khoản nội bộ
        </Typography>
        <Button variant="contained" onClick={onAdd}>
          Thêm
        </Button>
        <Button onClick={refresh} disabled={loading}>
          Refresh
        </Button>
      </Box>

      {pageError ? <Alert severity="error">{pageError}</Alert> : null}

      <Paper variant="outlined">
        <TableContainer>
          <Table size="small">
            <TableHead>
              <TableRow>
                <TableCell>Username</TableCell>
                <TableCell width={200}>Role</TableCell>
                <TableCell width={140}>Trạng thái</TableCell>
                <TableCell width={240} align="right">
                  Actions
                </TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {loading ? (
                <TableRow>
                  <TableCell colSpan={4}>
                    <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                      <CircularProgress size={18} />
                      Loading...
                    </Box>
                  </TableCell>
                </TableRow>
              ) : visibleItems.length === 0 ? (
                <TableRow>
                  <TableCell colSpan={4}>Chưa có tài khoản</TableCell>
                </TableRow>
              ) : (
                visibleItems.map((u) => {
                  const enabled = u?.enabled !== false;
                  return (
                    <TableRow key={u.username} hover>
                      <TableCell>{u.username}</TableCell>
                      <TableCell>{getPrimaryRole(u) || '-'}</TableCell>
                      <TableCell>
                        <Typography
                          variant="body2"
                          sx={{
                            fontWeight: 500,
                            color: enabled ? 'success.main' : 'error.main',
                          }}
                        >
                          {enabled ? 'Hoạt động' : 'Đã khóa'}
                        </Typography>
                      </TableCell>
                      <TableCell align="right">
                        <Stack direction="row" spacing={1} justifyContent="flex-end">
                          <Button size="small" onClick={() => onEdit(u)}>
                            Sửa
                          </Button>
                          <Button
                            size="small"
                            color={enabled ? 'error' : 'primary'}
                            onClick={() => toggleLock(u)}
                          >
                            {enabled ? 'Khóa' : 'Mở khóa'}
                          </Button>
                        </Stack>
                      </TableCell>
                    </TableRow>
                  );
                })
              )}
            </TableBody>
          </Table>
        </TableContainer>
      </Paper>

      <StaffDialog
        open={dialogOpen}
        mode={dialogMode}
        initial={editing}
        onClose={() => setDialogOpen(false)}
        onSave={onSave}
      />

      <Snackbar
        open={Boolean(toast)}
        autoHideDuration={2500}
        onClose={() => setToast('')}
        message={toast}
      />
    </Stack>
  );
}
