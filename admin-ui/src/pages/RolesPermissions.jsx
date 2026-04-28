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

function AssignRoleDialog({ open, user, onClose, onAssign }) {
  const [role, setRole] = useState('SALES');
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');

  useEffect(() => {
    setRole(getPrimaryRole(user) || 'SALES');
    setSaving(false);
    setError('');
  }, [user, open]);

  const onSubmit = async () => {
    setError('');
    if (!user?.username) {
      setError('User không hợp lệ');
      return;
    }
    if (!role) {
      setError('Role là bắt buộc');
      return;
    }
    setSaving(true);
    try {
      await onAssign({ username: user.username, role });
      onClose();
    } catch (err) {
      setError(toErrorMessage(err));
    } finally {
      setSaving(false);
    }
  };

  return (
    <Dialog open={open} onClose={onClose} fullWidth maxWidth="sm">
      <DialogTitle>Gán role</DialogTitle>
      <DialogContent>
        <Stack spacing={2} sx={{ mt: 1 }}>
          {error ? <Alert severity="error">{error}</Alert> : null}
          <TextField label="Username" value={user?.username ?? ''} fullWidth disabled />
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

export default function RolesPermissionsPage() {
  const [items, setItems] = useState([]);
  const [loading, setLoading] = useState(true);
  const [pageError, setPageError] = useState('');
  const [toast, setToast] = useState('');

  const [dialogOpen, setDialogOpen] = useState(false);
  const [selectedUser, setSelectedUser] = useState(null);

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

  const openAssign = (u) => {
    setSelectedUser(u);
    setDialogOpen(true);
  };

  const onAssign = async ({ username, role }) => {
    await api.put(`/users/${encodeURIComponent(username)}`, {
      roles: [role],
    });
    setToast('Đã gán role');
    await refresh();
  };

  return (
    <Stack spacing={2}>
      <Box sx={{ display: 'flex', alignItems: 'center', gap: 2 }}>
        <Typography variant="h5" sx={{ flexGrow: 1 }}>
          Phân quyền
        </Typography>
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
                <TableCell width={220}>Role</TableCell>
                <TableCell width={140}>Trạng thái</TableCell>
                <TableCell width={160} align="right">
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
                        <Button size="small" onClick={() => openAssign(u)}>
                          Gán role
                        </Button>
                      </TableCell>
                    </TableRow>
                  );
                })
              )}
            </TableBody>
          </Table>
        </TableContainer>
      </Paper>

      <AssignRoleDialog
        open={dialogOpen}
        user={selectedUser}
        onClose={() => setDialogOpen(false)}
        onAssign={onAssign}
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
