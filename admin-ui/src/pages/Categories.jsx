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
  Divider,
  FormControl,
  FormControlLabel,
  InputLabel,
  MenuItem,
  Paper,
  Select,
  Snackbar,
  Stack,
  Switch,
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
import { getPermissions } from '../auth/auth.js';

function toErrorMessage(err) {
  const status = err?.response?.status;
  const data = err?.response?.data;
  if (typeof err?.message === 'string' && err.message.trim()) return err.message.trim();
  if (typeof data === 'string' && data.trim()) return `${status ?? ''} ${data}`.trim();
  if (data && typeof data === 'object') {
    if (typeof data.error === 'string' && data.error.trim()) return `${status ?? ''} ${data.error}`.trim();
    if (typeof data.message === 'string' && data.message.trim()) return `${status ?? ''} ${data.message}`.trim();
  }
  return status ? `Request failed (${status})` : 'Request failed';
}

function CategoryDialog({ open, title, initial, options, onClose, onSave }) {
  const [name, setName] = useState('');
  const [parentId, setParentId] = useState('');
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');

  useEffect(() => {
    setName(initial?.name ?? '');
    setParentId(initial?.parentId != null ? String(initial.parentId) : '');
    setSaving(false);
    setError('');
  }, [initial, open]);

  const onSubmit = async () => {
    setError('');
    const trimmedName = String(name || '').trim();
    if (!trimmedName) {
      setError('Tên danh mục là bắt buộc');
      return;
    }

    const payload = {
      name: trimmedName,
      parentId: parentId ? Number(parentId) : null,
    };

    setSaving(true);
    try {
      await onSave(payload);
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
            label="Tên danh mục"
            value={name}
            onChange={(e) => setName(e.target.value)}
            autoFocus
            fullWidth
          />
          <FormControl fullWidth>
            <InputLabel id="category-parent-label">Danh mục cha</InputLabel>
            <Select
              labelId="category-parent-label"
              label="Danh mục cha"
              value={parentId}
              onChange={(e) => setParentId(e.target.value)}
            >
              <MenuItem value="">
                <em>(Danh mục gốc)</em>
              </MenuItem>
              {options.map((item) => (
                <MenuItem key={item.id} value={String(item.id)}>
                  {item.path || item.name}
                </MenuItem>
              ))}
            </Select>
          </FormControl>
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose} disabled={saving}>Hủy</Button>
        <Button variant="contained" onClick={onSubmit} disabled={saving}>
          {saving ? 'Đang lưu...' : 'Lưu'}
        </Button>
      </DialogActions>
    </Dialog>
  );
}

function CategoryAttributeDialog({ open, title, initial, onClose, onSave }) {
  const [code, setCode] = useState('');
  const [name, setName] = useState('');
  const [type, setType] = useState('STRING');
  const [unit, setUnit] = useState('');
  const [required, setRequired] = useState(false);
  const [sortOrder, setSortOrder] = useState('0');
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');

  useEffect(() => {
    setCode(initial?.code ?? '');
    setName(initial?.name ?? '');
    setType(initial?.type ?? 'STRING');
    setUnit(initial?.unit ?? '');
    setRequired(Boolean(initial?.required));
    setSortOrder(String(initial?.sortOrder ?? 0));
    setSaving(false);
    setError('');
  }, [initial, open]);

  const onSubmit = async () => {
    setError('');
    const trimmedName = String(name || '').trim();
    if (!trimmedName) {
      setError('Tên thuộc tính là bắt buộc');
      return;
    }
    const parsedSortOrder = Number(sortOrder);
    if (!Number.isFinite(parsedSortOrder)) {
      setError('Thứ tự phải là số');
      return;
    }

    setSaving(true);
    try {
      await onSave({
        code: String(code || '').trim() || null,
        name: trimmedName,
        type: String(type || 'STRING'),
        unit: unit ?? '',
        required: Boolean(required),
        sortOrder: Math.trunc(parsedSortOrder),
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
          <Box sx={{ display: 'grid', gap: 2, gridTemplateColumns: { xs: '1fr', md: '1fr 1fr' } }}>
            <TextField
              label="Mã thuộc tính (tuỳ chọn)"
              value={code}
              onChange={(e) => setCode(e.target.value)}
            />
            <TextField
              label="Tên thuộc tính"
              value={name}
              onChange={(e) => setName(e.target.value)}
            />
            <FormControl fullWidth>
              <InputLabel id="category-attribute-type-label">Kiểu dữ liệu</InputLabel>
              <Select
                labelId="category-attribute-type-label"
                label="Kiểu dữ liệu"
                value={type}
                onChange={(e) => setType(e.target.value)}
              >
                <MenuItem value="STRING">STRING</MenuItem>
                <MenuItem value="NUMBER">NUMBER</MenuItem>
                <MenuItem value="BOOLEAN">BOOLEAN</MenuItem>
              </Select>
            </FormControl>
            <TextField
              label="Đơn vị (tuỳ chọn)"
              value={unit}
              onChange={(e) => setUnit(e.target.value)}
            />
            <TextField
              label="Thứ tự"
              value={sortOrder}
              onChange={(e) => setSortOrder(e.target.value)}
            />
            <FormControlLabel
              control={<Switch checked={required} onChange={(e) => setRequired(e.target.checked)} />}
              label="Bắt buộc"
            />
          </Box>
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose} disabled={saving}>Hủy</Button>
        <Button variant="contained" onClick={onSubmit} disabled={saving}>
          {saving ? 'Đang lưu...' : 'Lưu'}
        </Button>
      </DialogActions>
    </Dialog>
  );
}

export default function CategoriesPage() {
  const permissions = getPermissions();
  const canCreate = permissions.includes('PRODUCT_CREATE');
  const canUpdate = permissions.includes('PRODUCT_UPDATE');
  const canDelete = permissions.includes('PRODUCT_DELETE');
  const canManage = canCreate || canUpdate || canDelete;

  const [items, setItems] = useState([]);
  const [loading, setLoading] = useState(true);
  const [pageError, setPageError] = useState('');
  const [toast, setToast] = useState('');

  const [dialogOpen, setDialogOpen] = useState(false);
  const [dialogMode, setDialogMode] = useState('create');
  const [editing, setEditing] = useState(null);
  const [categoryFilterId, setCategoryFilterId] = useState('');

  const [selectedAttributeCategoryId, setSelectedAttributeCategoryId] = useState('');
  const [attributeItems, setAttributeItems] = useState([]);
  const [loadingAttributes, setLoadingAttributes] = useState(false);
  const [attributeError, setAttributeError] = useState('');
  const [attributeDialogOpen, setAttributeDialogOpen] = useState(false);
  const [attributeDialogMode, setAttributeDialogMode] = useState('create');
  const [editingAttribute, setEditingAttribute] = useState(null);

  const showActions = canUpdate || canDelete;
  const columnCount = showActions ? 8 : 7;
  const attributeColumnCount = showActions ? 8 : 7;

  const refresh = async () => {
    setPageError('');
    setLoading(true);
    try {
      const response = await api.get('/categories');
      setItems(Array.isArray(response.data) ? response.data : []);
    } catch (err) {
      setPageError(toErrorMessage(err));
    } finally {
      setLoading(false);
    }
  };

  const refreshAttributes = async (categoryIdRaw = selectedAttributeCategoryId) => {
    setAttributeError('');
    const categoryId = Number(categoryIdRaw);
    if (!Number.isFinite(categoryId) || categoryId <= 0) {
      setAttributeItems([]);
      return;
    }
    setLoadingAttributes(true);
    try {
      const response = await api.get(`/categories/${categoryId}/attributes`);
      setAttributeItems(Array.isArray(response.data) ? response.data : []);
    } catch (err) {
      setAttributeError(toErrorMessage(err));
      setAttributeItems([]);
    } finally {
      setLoadingAttributes(false);
    }
  };

  useEffect(() => {
    refresh();
  }, []);

  useEffect(() => {
    if (items.length === 0) {
      setSelectedAttributeCategoryId('');
      setAttributeItems([]);
      return;
    }
    const exists = items.some((item) => String(item.id) === String(selectedAttributeCategoryId));
    if (!exists) {
      setSelectedAttributeCategoryId(String(items[0].id));
    }
  }, [items, selectedAttributeCategoryId]);

  useEffect(() => {
    if (!selectedAttributeCategoryId) return;
    refreshAttributes(selectedAttributeCategoryId);
  }, [selectedAttributeCategoryId]);

  const descendantsById = useMemo(() => {
    const childrenMap = new Map();
    for (const item of items) {
      if (item.parentId == null) continue;
      const list = childrenMap.get(item.parentId) ?? [];
      list.push(item.id);
      childrenMap.set(item.parentId, list);
    }

    const cache = new Map();
    const collect = (id) => {
      if (cache.has(id)) return cache.get(id);
      const result = new Set();
      const stack = [...(childrenMap.get(id) ?? [])];
      while (stack.length > 0) {
        const childId = stack.pop();
        if (childId == null || result.has(childId)) continue;
        result.add(childId);
        stack.push(...(childrenMap.get(childId) ?? []));
      }
      cache.set(id, result);
      return result;
    };

    const result = new Map();
    for (const item of items) {
      result.set(item.id, collect(item.id));
    }
    return result;
  }, [items]);

  const parentOptions = useMemo(() => {
    if (!editing?.id) {
      return items;
    }
    const blocked = new Set(descendantsById.get(editing.id) ?? []);
    blocked.add(editing.id);
    return items.filter((item) => !blocked.has(item.id));
  }, [items, editing, descendantsById]);

  const filteredItems = useMemo(() => {
    if (!categoryFilterId) {
      return items;
    }
    const selectedId = Number(categoryFilterId);
    if (!Number.isFinite(selectedId)) {
      return items;
    }
    const visible = new Set([selectedId, ...(descendantsById.get(selectedId) ?? [])]);
    return items.filter((item) => visible.has(item.id));
  }, [items, categoryFilterId, descendantsById]);

  const selectedAttributeCategory = useMemo(
    () => items.find((item) => String(item.id) === String(selectedAttributeCategoryId)) ?? null,
    [items, selectedAttributeCategoryId],
  );

  const dialogTitle = dialogMode === 'edit' ? 'Sửa danh mục' : 'Thêm danh mục';
  const attributeDialogTitle = attributeDialogMode === 'edit' ? 'Sửa thuộc tính danh mục' : 'Thêm thuộc tính danh mục';

  const onAdd = () => {
    setDialogMode('create');
    setEditing(null);
    setDialogOpen(true);
  };

  const onEdit = (item) => {
    setDialogMode('edit');
    setEditing(item);
    setDialogOpen(true);
  };

  const onSave = async (payload) => {
    if (dialogMode === 'edit' && editing?.id != null) {
      await api.put(`/categories/${editing.id}`, payload);
      setToast('Đã cập nhật danh mục');
    } else {
      await api.post('/categories', payload);
      setToast('Đã tạo danh mục');
    }
    await refresh();
  };

  const onDelete = async (item) => {
    const label = item?.path || item?.name || `#${item?.id}`;
    const ok = window.confirm(`Xóa danh mục "${label}"?`);
    if (!ok) return;

    try {
      await api.delete(`/categories/${item.id}`);
      setToast('Đã xóa danh mục');
      await refresh();
    } catch (err) {
      setToast(toErrorMessage(err));
    }
  };

  const onAddAttribute = () => {
    setAttributeDialogMode('create');
    setEditingAttribute(null);
    setAttributeDialogOpen(true);
  };

  const onEditAttribute = (item) => {
    setAttributeDialogMode('edit');
    setEditingAttribute(item);
    setAttributeDialogOpen(true);
  };

  const onSaveAttribute = async (payload) => {
    const categoryId = Number(selectedAttributeCategoryId);
    if (!Number.isFinite(categoryId) || categoryId <= 0) {
      throw new Error('Chọn danh mục trước khi lưu thuộc tính');
    }

    if (attributeDialogMode === 'edit' && editingAttribute?.id != null) {
      await api.put(`/categories/${categoryId}/attributes/${editingAttribute.id}`, {
        attributeId: editingAttribute.attributeId ?? null,
        ...payload,
      });
      setToast('Đã cập nhật thuộc tính danh mục');
    } else {
      await api.post(`/categories/${categoryId}/attributes`, payload);
      setToast('Đã thêm thuộc tính danh mục');
    }
    await Promise.all([refreshAttributes(categoryId), refresh()]);
  };

  const onDeleteAttribute = async (item) => {
    const categoryId = Number(selectedAttributeCategoryId);
    if (!Number.isFinite(categoryId) || categoryId <= 0) return;
    const label = item?.name || item?.code || `#${item?.id}`;
    const ok = window.confirm(`Xóa thuộc tính "${label}" khỏi danh mục này?`);
    if (!ok) return;

    try {
      await api.delete(`/categories/${categoryId}/attributes/${item.id}`);
      setToast('Đã xóa thuộc tính danh mục');
      await Promise.all([refreshAttributes(categoryId), refresh()]);
    } catch (err) {
      setToast(toErrorMessage(err));
    }
  };

  if (!canManage) {
    return <Alert severity="warning">Bạn không có quyền quản lý danh mục.</Alert>;
  }

  return (
    <Stack spacing={2}>
      <Box sx={{ display: 'flex', alignItems: 'center', gap: 2, flexWrap: 'wrap' }}>
        <Typography variant="h5" sx={{ flexGrow: 1 }}>
          Danh mục
        </Typography>
        <FormControl size="small" sx={{ minWidth: 300 }}>
          <InputLabel id="category-filter-label">Lọc theo danh mục</InputLabel>
          <Select
            labelId="category-filter-label"
            label="Lọc theo danh mục"
            value={categoryFilterId}
            onChange={(e) => setCategoryFilterId(e.target.value)}
          >
            <MenuItem value="">
              <em>Tất cả danh mục</em>
            </MenuItem>
            {items.map((item) => (
              <MenuItem key={`filter-${item.id}`} value={String(item.id)}>
                {item.path || item.name}
              </MenuItem>
            ))}
          </Select>
        </FormControl>
        {canCreate ? (
          <Button variant="contained" onClick={onAdd}>
            Thêm danh mục
          </Button>
        ) : null}
        <Button onClick={refresh} disabled={loading}>
          Làm mới
        </Button>
      </Box>

      {pageError ? <Alert severity="error">{pageError}</Alert> : null}

      <Paper variant="outlined">
        <TableContainer>
          <Table size="small">
            <TableHead>
              <TableRow>
                <TableCell width={70}>STT</TableCell>
                <TableCell>Tên danh mục</TableCell>
                <TableCell>Danh mục cha</TableCell>
                <TableCell width={120}>Sản phẩm</TableCell>
                <TableCell width={120}>Danh mục con</TableCell>
                <TableCell width={120}>Thuộc tính</TableCell>
                <TableCell>Đường dẫn</TableCell>
                {showActions ? <TableCell width={170} align="right">Thao tác</TableCell> : null}
              </TableRow>
            </TableHead>
            <TableBody>
              {loading ? (
                <TableRow>
                  <TableCell colSpan={columnCount}>
                    <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                      <CircularProgress size={18} />
                      Đang tải...
                    </Box>
                  </TableCell>
                </TableRow>
              ) : filteredItems.length === 0 ? (
                <TableRow>
                  <TableCell colSpan={columnCount}>Không có danh mục phù hợp bộ lọc</TableCell>
                </TableRow>
              ) : (
                filteredItems.map((item, index) => (
                  <TableRow key={item.id} hover>
                    <TableCell>{index + 1}</TableCell>
                    <TableCell>{item.name}</TableCell>
                    <TableCell>{item.parentName || '-'}</TableCell>
                    <TableCell>{item.productCount ?? 0}</TableCell>
                    <TableCell>{item.childCount ?? 0}</TableCell>
                    <TableCell>{item.attributeCount ?? 0}</TableCell>
                    <TableCell>{item.path || item.name}</TableCell>
                    {showActions ? (
                      <TableCell align="right">
                        <Stack direction="row" spacing={1} justifyContent="flex-end">
                          {canUpdate ? (
                            <Button size="small" onClick={() => onEdit(item)}>
                              Sửa
                            </Button>
                          ) : null}
                          {canDelete ? (
                            <Button size="small" color="error" onClick={() => onDelete(item)}>
                              Xóa
                            </Button>
                          ) : null}
                        </Stack>
                      </TableCell>
                    ) : null}
                  </TableRow>
                ))
              )}
            </TableBody>
          </Table>
        </TableContainer>
      </Paper>

      <Paper variant="outlined" sx={{ p: 2 }}>
        <Stack spacing={2}>
          <Box sx={{ display: 'flex', alignItems: 'center', gap: 2, flexWrap: 'wrap' }}>
            <Typography variant="h6" sx={{ flexGrow: 1 }}>
              Thuộc tính theo danh mục
            </Typography>
            <FormControl size="small" sx={{ minWidth: 320 }}>
              <InputLabel id="attribute-category-label">Danh mục</InputLabel>
              <Select
                labelId="attribute-category-label"
                label="Danh mục"
                value={selectedAttributeCategoryId}
                onChange={(e) => setSelectedAttributeCategoryId(e.target.value)}
              >
                {items.map((item) => (
                  <MenuItem key={`attr-category-${item.id}`} value={String(item.id)}>
                    {item.path || item.name}
                  </MenuItem>
                ))}
              </Select>
            </FormControl>
            {canCreate ? (
              <Button
                variant="contained"
                onClick={onAddAttribute}
                disabled={!selectedAttributeCategoryId}
              >
                Thêm thuộc tính
              </Button>
            ) : null}
            <Button
              onClick={() => refreshAttributes(selectedAttributeCategoryId)}
              disabled={!selectedAttributeCategoryId || loadingAttributes}
            >
              Làm mới
            </Button>
          </Box>

          <Divider />

          {selectedAttributeCategory ? (
            <Typography variant="body2" color="text.secondary">
              Đang quản lý thuộc tính cho: <strong>{selectedAttributeCategory.path || selectedAttributeCategory.name}</strong>
            </Typography>
          ) : null}

          {attributeError ? <Alert severity="error">{attributeError}</Alert> : null}

          <TableContainer>
            <Table size="small">
              <TableHead>
                <TableRow>
                  <TableCell width={70}>STT</TableCell>
                  <TableCell>Mã</TableCell>
                  <TableCell>Tên thuộc tính</TableCell>
                  <TableCell width={120}>Kiểu</TableCell>
                  <TableCell width={120}>Đơn vị</TableCell>
                  <TableCell width={120}>Bắt buộc</TableCell>
                  <TableCell width={120}>Thứ tự</TableCell>
                  {showActions ? <TableCell width={170} align="right">Thao tác</TableCell> : null}
                </TableRow>
              </TableHead>
              <TableBody>
                {loadingAttributes ? (
                  <TableRow>
                    <TableCell colSpan={attributeColumnCount}>
                      <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                        <CircularProgress size={18} />
                        Đang tải thuộc tính...
                      </Box>
                    </TableCell>
                  </TableRow>
                ) : attributeItems.length === 0 ? (
                  <TableRow>
                    <TableCell colSpan={attributeColumnCount}>Chưa có thuộc tính cho danh mục này</TableCell>
                  </TableRow>
                ) : (
                  attributeItems.map((item, index) => (
                    <TableRow key={item.id} hover>
                      <TableCell>{index + 1}</TableCell>
                      <TableCell>{item.code || '-'}</TableCell>
                      <TableCell>{item.name}</TableCell>
                      <TableCell>{item.type}</TableCell>
                      <TableCell>{item.unit || '-'}</TableCell>
                      <TableCell>{item.required ? 'Có' : 'Không'}</TableCell>
                      <TableCell>{item.sortOrder ?? 0}</TableCell>
                      {showActions ? (
                        <TableCell align="right">
                          <Stack direction="row" spacing={1} justifyContent="flex-end">
                            {canUpdate ? (
                              <Button size="small" onClick={() => onEditAttribute(item)}>
                                Sửa
                              </Button>
                            ) : null}
                            {canDelete ? (
                              <Button size="small" color="error" onClick={() => onDeleteAttribute(item)}>
                                Xóa
                              </Button>
                            ) : null}
                          </Stack>
                        </TableCell>
                      ) : null}
                    </TableRow>
                  ))
                )}
              </TableBody>
            </Table>
          </TableContainer>
        </Stack>
      </Paper>

      <CategoryDialog
        open={dialogOpen}
        title={dialogTitle}
        initial={editing}
        options={parentOptions}
        onClose={() => setDialogOpen(false)}
        onSave={onSave}
      />

      <CategoryAttributeDialog
        open={attributeDialogOpen}
        title={attributeDialogTitle}
        initial={editingAttribute}
        onClose={() => setAttributeDialogOpen(false)}
        onSave={onSaveAttribute}
      />

      <Snackbar
        open={Boolean(toast)}
        autoHideDuration={2600}
        onClose={() => setToast('')}
        message={toast}
      />
    </Stack>
  );
}
