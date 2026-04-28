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
  FormControl,
  InputLabel,
  MenuItem,
  Paper,
  Select,
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
import { getPermissions } from '../auth/auth.js';

function toErrorMessage(err) {
  const status = err?.response?.status;
  const data = err?.response?.data;
  if (typeof data === 'string' && data.trim()) return `${status ?? ''} ${data}`.trim();
  if (data && typeof data === 'object') {
    if (typeof data.error === 'string' && data.error.trim()) return `${status ?? ''} ${data.error}`.trim();
    if (typeof data.message === 'string' && data.message.trim()) return `${status ?? ''} ${data.message}`.trim();
  }
  return status ? `Request failed (${status})` : 'Request failed';
}

function hasAttributeValue(value) {
  if (!value || typeof value !== 'object') return false;
  if (typeof value.valueText === 'string' && value.valueText.trim()) return true;
  if (typeof value.valueNumber === 'number' && Number.isFinite(value.valueNumber)) return true;
  if (typeof value.valueBoolean === 'boolean') return true;
  return false;
}

function ProductDialog({ open, title, initial, categories, onClose, onSave }) {
  const [name, setName] = useState('');
  const [price, setPrice] = useState('');
  const [description, setDescription] = useState('');
  const [categoryId, setCategoryId] = useState('');
  const [attributeDefinitions, setAttributeDefinitions] = useState([]);
  const [attributeValues, setAttributeValues] = useState({});
  const [loadingAttributes, setLoadingAttributes] = useState(false);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');

  useEffect(() => {
    setName(initial?.name ?? '');
    setPrice(initial?.price ?? '');
    setDescription(initial?.description ?? '');
    setCategoryId(initial?.categoryId != null ? String(initial.categoryId) : '');
    const map = {};
    for (const item of initial?.attributeValues ?? []) {
      if (!item?.attributeId) continue;
      map[item.attributeId] = {
        valueText: item.valueText ?? '',
        valueNumber: item.valueNumber ?? '',
        valueBoolean: typeof item.valueBoolean === 'boolean' ? item.valueBoolean : '',
      };
    }
    setAttributeValues(map);
    setAttributeDefinitions([]);
    setLoadingAttributes(false);
    setError('');
    setSaving(false);
  }, [initial, open]);

  useEffect(() => {
    const loadAttributeDefinitions = async () => {
      const cid = Number(categoryId);
      if (!Number.isFinite(cid) || cid <= 0) {
        setAttributeDefinitions([]);
        return;
      }
      setLoadingAttributes(true);
      try {
        const response = await api.get(`/categories/${cid}/attributes`);
        setAttributeDefinitions(Array.isArray(response.data) ? response.data : []);
      } catch (err) {
        setAttributeDefinitions([]);
        setError(toErrorMessage(err));
      } finally {
        setLoadingAttributes(false);
      }
    };

    if (!open) return;
    loadAttributeDefinitions();
  }, [categoryId, open]);

  const setAttributeField = (attributeId, nextValue) => {
    setAttributeValues((prev) => ({ ...prev, [attributeId]: { ...(prev[attributeId] ?? {}), ...nextValue } }));
  };

  const onSubmit = async () => {
    setError('');

    const trimmedName = String(name || '').trim();
    const parsedPrice = Number(price);
    const parsedCategoryId = Number(categoryId);

    if (!trimmedName) {
      setError('Tên sản phẩm là bắt buộc');
      return;
    }
    if (!Number.isFinite(parsedPrice) || parsedPrice <= 0) {
      setError('Giá phải là số > 0');
      return;
    }
    if (!Number.isFinite(parsedCategoryId) || parsedCategoryId <= 0) {
      setError('Danh mục là bắt buộc');
      return;
    }

    const payloadAttributes = attributeDefinitions
      .map((item) => {
        const raw = attributeValues[item.attributeId] ?? {};
        const normalized = {
          attributeId: item.attributeId,
          valueText: typeof raw.valueText === 'string' ? raw.valueText : null,
          valueNumber: raw.valueNumber === '' || raw.valueNumber == null ? null : Number(raw.valueNumber),
          valueBoolean: raw.valueBoolean === '' || raw.valueBoolean == null ? null : Boolean(raw.valueBoolean),
        };
        if (item.type === 'NUMBER' && normalized.valueNumber != null && !Number.isFinite(normalized.valueNumber)) {
          return { ...normalized, _invalid: true };
        }
        return normalized;
      })
      .filter((item) => hasAttributeValue(item) || item._invalid);

    const invalidItem = payloadAttributes.find((item) => item._invalid);
    if (invalidItem) {
      setError('Có thuộc tính kiểu số không hợp lệ');
      return;
    }

    setSaving(true);
    try {
      await onSave({
        name: trimmedName,
        price: parsedPrice,
        description: String(description ?? ''),
        categoryId: parsedCategoryId,
        attributeValues: payloadAttributes,
      });
      onClose();
    } catch (err) {
      setError(toErrorMessage(err));
    } finally {
      setSaving(false);
    }
  };

  return (
    <Dialog open={open} onClose={onClose} fullWidth maxWidth="md">
      <DialogTitle>{title}</DialogTitle>
      <DialogContent>
        <Stack spacing={2} sx={{ mt: 1 }}>
          {error ? <Alert severity="error">{error}</Alert> : null}

          <Box sx={{ display: 'grid', gap: 2, gridTemplateColumns: { xs: '1fr', md: '1fr 1fr' } }}>
            <TextField
              label="Tên sản phẩm"
              value={name}
              onChange={(e) => setName(e.target.value)}
              fullWidth
              autoFocus
            />
            <TextField
              label="Giá"
              value={price}
              onChange={(e) => setPrice(e.target.value)}
              fullWidth
              inputMode="decimal"
            />
            <FormControl fullWidth>
              <InputLabel id="product-category-label">Danh mục</InputLabel>
              <Select
                labelId="product-category-label"
                label="Danh mục"
                value={categoryId}
                onChange={(e) => setCategoryId(e.target.value)}
              >
                <MenuItem value="">
                  <em>Chọn danh mục</em>
                </MenuItem>
                {categories.map((item) => (
                  <MenuItem key={item.id} value={String(item.id)}>
                    {item.parentName ? `${item.parentName} / ${item.name}` : item.name}
                  </MenuItem>
                ))}
              </Select>
            </FormControl>
            <TextField
              label="Mô tả"
              value={description}
              onChange={(e) => setDescription(e.target.value)}
              fullWidth
              multiline
              minRows={2}
            />
          </Box>

          <Typography variant="subtitle1">Thuộc tính theo danh mục</Typography>
          {loadingAttributes ? (
            <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
              <CircularProgress size={18} />
              Đang tải thuộc tính...
            </Box>
          ) : attributeDefinitions.length === 0 ? (
            <Alert severity="info">Danh mục này chưa có thuộc tính.</Alert>
          ) : (
            <Box sx={{ display: 'grid', gap: 2, gridTemplateColumns: { xs: '1fr', md: '1fr 1fr' } }}>
              {attributeDefinitions.map((item) => {
                const current = attributeValues[item.attributeId] ?? {};
                const label = `${item.name}${item.unit ? ` (${item.unit})` : ''}${item.required ? ' *' : ''}`;

                if (item.type === 'NUMBER') {
                  return (
                    <TextField
                      key={item.id}
                      label={label}
                      value={current.valueNumber ?? ''}
                      onChange={(e) => setAttributeField(item.attributeId, { valueNumber: e.target.value, valueText: null, valueBoolean: null })}
                      inputMode="decimal"
                    />
                  );
                }

                if (item.type === 'BOOLEAN') {
                  return (
                    <FormControl key={item.id} fullWidth>
                      <InputLabel id={`attr-boolean-${item.id}`}>{label}</InputLabel>
                      <Select
                        labelId={`attr-boolean-${item.id}`}
                        label={label}
                        value={current.valueBoolean === '' || current.valueBoolean == null ? '' : String(Boolean(current.valueBoolean))}
                        onChange={(e) => {
                          const value = e.target.value;
                          setAttributeField(item.attributeId, {
                            valueBoolean: value === '' ? '' : value === 'true',
                            valueText: null,
                            valueNumber: null,
                          });
                        }}
                      >
                        <MenuItem value="">
                          <em>(Trống)</em>
                        </MenuItem>
                        <MenuItem value="true">true</MenuItem>
                        <MenuItem value="false">false</MenuItem>
                      </Select>
                    </FormControl>
                  );
                }

                return (
                  <TextField
                    key={item.id}
                    label={label}
                    value={current.valueText ?? ''}
                    onChange={(e) => setAttributeField(item.attributeId, { valueText: e.target.value, valueNumber: null, valueBoolean: null })}
                  />
                );
              })}
            </Box>
          )}
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

export default function ProductsPage() {
  const permissions = getPermissions();
  const canCreate = permissions.includes('PRODUCT_CREATE');
  const canUpdate = permissions.includes('PRODUCT_UPDATE');
  const canDelete = permissions.includes('PRODUCT_DELETE');
  const showActions = canUpdate || canDelete;
  const columnCount = showActions ? 7 : 6;

  const [items, setItems] = useState([]);
  const [categories, setCategories] = useState([]);
  const [loading, setLoading] = useState(true);
  const [pageError, setPageError] = useState('');
  const [keyword, setKeyword] = useState('');
  const [attributeKeyword, setAttributeKeyword] = useState('');
  const [categoryFilterId, setCategoryFilterId] = useState('');

  const [toast, setToast] = useState('');
  const [dialogOpen, setDialogOpen] = useState(false);
  const [dialogMode, setDialogMode] = useState('create');
  const [editing, setEditing] = useState(null);
  const [backfilling, setBackfilling] = useState(false);

  const dialogTitle = useMemo(() => {
    return dialogMode === 'edit' ? 'Sửa sản phẩm' : 'Thêm sản phẩm';
  }, [dialogMode]);

  const refresh = async (nextFilters) => {
    setPageError('');
    setLoading(true);
    try {
      const filters = nextFilters ?? {
        keyword,
        attributeKeyword,
        categoryId: categoryFilterId,
      };
      const params = {};
      if (filters.keyword && String(filters.keyword).trim()) {
        params.keyword = String(filters.keyword).trim();
      }
      if (filters.attributeKeyword && String(filters.attributeKeyword).trim()) {
        params.attributeKeyword = String(filters.attributeKeyword).trim();
      }
      if (filters.categoryId && String(filters.categoryId).trim()) {
        params.categoryId = Number(filters.categoryId);
      }
      const res = await api.get('/products', { params });
      setItems(Array.isArray(res.data) ? res.data : []);
    } catch (err) {
      setPageError(toErrorMessage(err));
    } finally {
      setLoading(false);
    }
  };

  const refreshCategories = async () => {
    try {
      const res = await api.get('/inventory/categories');
      setCategories(Array.isArray(res.data) ? res.data : []);
    } catch (err) {
      setCategories([]);
      setPageError(toErrorMessage(err));
    }
  };

  useEffect(() => {
    refresh();
    refreshCategories();
  }, []);

  const onAdd = () => {
    setDialogMode('create');
    setEditing(null);
    setDialogOpen(true);
  };

  const onEdit = (p) => {
    setDialogMode('edit');
    setEditing(p);
    setDialogOpen(true);
  };

  const onSave = async (payload) => {
    if (dialogMode === 'edit' && editing?.id != null) {
      await api.put(`/products/${editing.id}`, payload);
      setToast('Đã cập nhật sản phẩm');
    } else {
      await api.post('/products', payload);
      setToast('Đã tạo sản phẩm');
    }
    await refresh();
  };

  const onDelete = async (p) => {
    const label = p?.name ? `"${p.name}"` : `#${p.id}`;
    const ok = window.confirm(`Xóa sản phẩm ${label}?`);
    if (!ok) return;

    try {
      await api.delete(`/products/${p.id}`);
      setToast('Đã xóa sản phẩm');
      await refresh();
    } catch (err) {
      setToast(toErrorMessage(err));
    }
  };

  const onBackfill = async () => {
    const ok = window.confirm('Điền dữ liệu trống cho vị trí và thuộc tính của tất cả sản phẩm hiện có?');
    if (!ok) return;

    setBackfilling(true);
    try {
      const res = await api.post('/products/backfill-metadata');
      const data = res?.data ?? {};
      const locationsFilled = Number(data.locationsFilled ?? 0);
      const attributesFilled = Number(data.attributesFilled ?? 0);
      setToast(`Đã điền dữ liệu: vị trí ${locationsFilled}, thuộc tính ${attributesFilled}`);
      await refresh();
    } catch (err) {
      setToast(toErrorMessage(err));
    } finally {
      setBackfilling(false);
    }
  };

  return (
    <Stack spacing={2}>
      <Box sx={{ display: 'flex', alignItems: 'center', gap: 2 }}>
        <Typography variant="h5" sx={{ flexGrow: 1 }}>
          Sản phẩm
        </Typography>
        {canUpdate ? (
          <Button variant="outlined" onClick={onBackfill} disabled={loading || backfilling}>
            {backfilling ? 'Đang điền dữ liệu...' : 'Điền dữ liệu trống'}
          </Button>
        ) : null}
        {canCreate ? (
          <Button variant="contained" onClick={onAdd}>
            Thêm sản phẩm
          </Button>
        ) : null}
        <Button onClick={() => refresh()} disabled={loading}>
          Làm mới
        </Button>
      </Box>

      <Box sx={{ display: 'grid', gap: 2, gridTemplateColumns: { xs: '1fr', md: '2fr 2fr 2fr auto auto' } }}>
        <TextField
          label="Từ khóa (tên / SKU / MPN)"
          value={keyword}
          onChange={(e) => setKeyword(e.target.value)}
        />
        <TextField
          label="Từ khóa thuộc tính"
          value={attributeKeyword}
          onChange={(e) => setAttributeKeyword(e.target.value)}
        />
        <FormControl fullWidth>
          <InputLabel id="products-filter-category-label">Danh mục</InputLabel>
          <Select
            labelId="products-filter-category-label"
            label="Danh mục"
            value={categoryFilterId}
            onChange={(e) => setCategoryFilterId(e.target.value)}
          >
            <MenuItem value="">
              <em>Tất cả</em>
            </MenuItem>
            {categories.map((item) => (
              <MenuItem key={item.id} value={String(item.id)}>
                {item.parentName ? `${item.parentName} / ${item.name}` : item.name}
              </MenuItem>
            ))}
          </Select>
        </FormControl>
        <Button variant="outlined" onClick={() => refresh()} disabled={loading}>
          Tìm kiếm
        </Button>
        <Button
          variant="outlined"
          onClick={() => {
            const reset = { keyword: '', attributeKeyword: '', categoryId: '' };
            setKeyword('');
            setAttributeKeyword('');
            setCategoryFilterId('');
            refresh(reset);
          }}
        >
          Xóa lọc
        </Button>
      </Box>

      {pageError ? <Alert severity="error">{pageError}</Alert> : null}

      <Paper variant="outlined">
        <TableContainer>
          <Table size="small">
            <TableHead>
              <TableRow>
                <TableCell width={70}>STT</TableCell>
                <TableCell>Tên</TableCell>
                <TableCell>Danh mục</TableCell>
                <TableCell width={120}>Giá</TableCell>
                <TableCell>Thuộc tính</TableCell>
                <TableCell>Mô tả</TableCell>
                {showActions ? (
                  <TableCell width={180} align="right">
                    Thao tác
                  </TableCell>
                ) : null}
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
              ) : items.length === 0 ? (
                <TableRow>
                  <TableCell colSpan={columnCount}>Không có sản phẩm</TableCell>
                </TableRow>
              ) : (
                items.map((p, index) => (
                  <TableRow key={p.id} hover>
                    <TableCell>{index + 1}</TableCell>
                    <TableCell>{p.name}</TableCell>
                    <TableCell>{p.categoryName || '-'}</TableCell>
                    <TableCell>{p.price}</TableCell>
                    <TableCell>
                      {(p.attributeValues ?? []).length === 0
                        ? '-'
                        : p.attributeValues.map((item) => `${item.name}: ${item.displayValue || '-'}`).join(' | ')}
                    </TableCell>
                    <TableCell>{p.description || '-'}</TableCell>
                    {showActions ? (
                      <TableCell align="right">
                        <Stack direction="row" spacing={1} justifyContent="flex-end">
                          {canUpdate ? (
                            <Button size="small" onClick={() => onEdit(p)}>
                              Sửa
                            </Button>
                          ) : null}
                          {canDelete ? (
                            <Button size="small" color="error" onClick={() => onDelete(p)}>
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

      <ProductDialog
        open={dialogOpen}
        title={dialogTitle}
        initial={editing}
        categories={categories}
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
