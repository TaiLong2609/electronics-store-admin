import { useEffect, useMemo, useState } from 'react'
import {
  Alert,
  Box,
  Button,
  Chip,
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
} from '@mui/material'
import { api } from '../api/http.js'

function toErrorMessage(err) {
  const status = err?.response?.status
  const data = err?.response?.data
  if (typeof data === 'string' && data.trim()) return `${status ?? ''} ${data}`.trim()
  if (data && typeof data === 'object') {
    if (typeof data.error === 'string' && data.error.trim()) return `${status ?? ''} ${data.error}`.trim()
    if (typeof data.message === 'string' && data.message.trim()) return `${status ?? ''} ${data.message}`.trim()
  }
  return status ? `Yeu cau that bai (${status})` : 'Yeu cau that bai'
}

function toMoney(value) {
  const amount = Number(value)
  if (!Number.isFinite(amount)) return '-'
  return amount.toLocaleString('vi-VN', { minimumFractionDigits: 0, maximumFractionDigits: 2 })
}

function toDateTimeInput(value) {
  if (!value) return ''
  const text = String(value).trim()
  if (!text) return ''
  if (text.length >= 16) return text.slice(0, 16)
  return text
}

function productOptionLabel(option) {
  if (!option) return ''
  if (option.sku && option.name) return `${option.sku} - ${option.name}`
  if (option.name) return option.name
  if (option.sku) return option.sku
  return `#${option.id ?? ''}`
}

function VoucherDialog({ open, editingVoucher, onClose, onSaved }) {
  const [form, setForm] = useState({
    code: '',
    title: '',
    description: '',
    discountType: 'PERCENT',
    discountValue: '',
    minOrderAmount: '',
    maxDiscountAmount: '',
    totalQuota: '',
    perUserLimit: '',
    startAt: '',
    endAt: '',
    active: true,
  })
  const [error, setError] = useState('')
  const [saving, setSaving] = useState(false)
  const [productKeyword, setProductKeyword] = useState('')
  const [productOptions, setProductOptions] = useState([])
  const [selectedProducts, setSelectedProducts] = useState([])
  const [loadingProducts, setLoadingProducts] = useState(false)

  useEffect(() => {
    if (!open) return
    if (!editingVoucher) {
      setForm({
        code: '',
        title: '',
        description: '',
        discountType: 'PERCENT',
        discountValue: '',
        minOrderAmount: '',
        maxDiscountAmount: '',
        totalQuota: '',
        perUserLimit: '',
        startAt: '',
        endAt: '',
        active: true,
      })
      setError('')
      setSaving(false)
      setSelectedProducts([])
      setProductOptions([])
      setProductKeyword('')
      return
    }
    setForm({
      code: editingVoucher.code ?? '',
      title: editingVoucher.title ?? '',
      description: editingVoucher.description ?? '',
      discountType: editingVoucher.discountType ?? 'PERCENT',
      discountValue: editingVoucher.discountValue ?? '',
      minOrderAmount: editingVoucher.minOrderAmount ?? '',
      maxDiscountAmount: editingVoucher.maxDiscountAmount ?? '',
      totalQuota: editingVoucher.totalQuota ?? '',
      perUserLimit: editingVoucher.perUserLimit ?? '',
      startAt: toDateTimeInput(editingVoucher.startAt),
      endAt: toDateTimeInput(editingVoucher.endAt),
      active: Boolean(editingVoucher.active),
    })
    setError('')
    setSaving(false)
    const selected = Array.isArray(editingVoucher.eligibleProducts) ? editingVoucher.eligibleProducts : []
    setSelectedProducts(selected)
    setProductOptions(selected)
    setProductKeyword('')
  }, [open, editingVoucher])

  useEffect(() => {
    if (!open) return
    let cancelled = false
    const timerId = window.setTimeout(async () => {
      setLoadingProducts(true)
      try {
        const response = await api.get('/marketing/vouchers/product-options', {
          params: {
            keyword: String(productKeyword || '').trim() || undefined,
          },
        })
        if (!cancelled) {
          const list = Array.isArray(response.data) ? response.data : []
          setProductOptions(list)
        }
      } catch {
        if (!cancelled) {
          setProductOptions([])
        }
      } finally {
        if (!cancelled) {
          setLoadingProducts(false)
        }
      }
    }, 250)

    return () => {
      cancelled = true
      window.clearTimeout(timerId)
    }
  }, [open, productKeyword])

  const mergedProductOptions = useMemo(() => {
    const byId = new Map()
    for (const item of productOptions) {
      if (!item?.id) continue
      byId.set(item.id, item)
    }
    for (const item of selectedProducts) {
      if (!item?.id) continue
      byId.set(item.id, item)
    }
    return Array.from(byId.values())
  }, [productOptions, selectedProducts])

  const onSelectProduct = (option) => {
    if (!option?.id) return
    setSelectedProducts((prev) => {
      if (prev.some((item) => item?.id === option.id)) return prev
      return [...prev, option]
    })
  }

  const onRemoveProduct = (productId) => {
    if (!productId) return
    setSelectedProducts((prev) => prev.filter((item) => item?.id !== productId))
  }

  const onSubmit = async () => {
    const payload = {
      code: String(form.code || '').trim(),
      title: String(form.title || '').trim(),
      description: String(form.description || '').trim() || null,
      discountType: String(form.discountType || '').trim(),
      discountValue: form.discountValue === '' ? null : Number(form.discountValue),
      minOrderAmount: form.minOrderAmount === '' ? null : Number(form.minOrderAmount),
      maxDiscountAmount: form.maxDiscountAmount === '' ? null : Number(form.maxDiscountAmount),
      totalQuota: form.totalQuota === '' ? null : Number(form.totalQuota),
      perUserLimit: form.perUserLimit === '' ? null : Number(form.perUserLimit),
      eligibleProductIds: selectedProducts
        .map((item) => Number(item?.id))
        .filter((id) => Number.isInteger(id) && id > 0),
      startAt: form.startAt ? `${form.startAt}:00` : null,
      endAt: form.endAt ? `${form.endAt}:00` : null,
      active: Boolean(form.active),
    }

    if (!payload.code || !payload.title || !payload.discountType || !Number.isFinite(payload.discountValue) || payload.discountValue <= 0) {
      setError('Can nhap du thong tin bat buoc: code, title, loai va gia tri giam')
      return
    }

    if (payload.minOrderAmount != null && (!Number.isFinite(payload.minOrderAmount) || payload.minOrderAmount < 0)) {
      setError('Min order amount khong hop le')
      return
    }
    if (payload.maxDiscountAmount != null && (!Number.isFinite(payload.maxDiscountAmount) || payload.maxDiscountAmount <= 0)) {
      setError('Max discount amount phai > 0')
      return
    }
    if (payload.totalQuota != null && (!Number.isInteger(payload.totalQuota) || payload.totalQuota <= 0)) {
      setError('Total quota phai la so nguyen > 0')
      return
    }
    if (payload.perUserLimit != null && (!Number.isInteger(payload.perUserLimit) || payload.perUserLimit <= 0)) {
      setError('Per-user limit phai la so nguyen > 0')
      return
    }

    setSaving(true)
    setError('')
    try {
      if (editingVoucher?.id) {
        await api.put(`/marketing/vouchers/${editingVoucher.id}`, payload)
      } else {
        await api.post('/marketing/vouchers', payload)
      }
      onSaved()
      onClose()
    } catch (err) {
      setError(toErrorMessage(err))
    } finally {
      setSaving(false)
    }
  }

  return (
    <Dialog open={open} onClose={onClose} fullWidth maxWidth="md">
      <DialogTitle>{editingVoucher ? 'Cap nhat voucher' : 'Tao voucher moi'}</DialogTitle>
      <DialogContent>
        <Stack spacing={2} sx={{ mt: 1 }}>
          {error ? <Alert severity="error">{error}</Alert> : null}

          <Box sx={{ display: 'grid', gap: 2, gridTemplateColumns: { xs: '1fr', md: '1fr 1fr' } }}>
            <TextField
              label="Code"
              value={form.code}
              onChange={(e) => setForm((v) => ({ ...v, code: e.target.value }))}
              fullWidth
            />
            <TextField
              label="Tieu de"
              value={form.title}
              onChange={(e) => setForm((v) => ({ ...v, title: e.target.value }))}
              fullWidth
            />
            <FormControl fullWidth>
              <InputLabel id="voucher-discount-type">Loai giam</InputLabel>
              <Select
                labelId="voucher-discount-type"
                label="Loai giam"
                value={form.discountType}
                onChange={(e) => setForm((v) => ({ ...v, discountType: e.target.value }))}
              >
                <MenuItem value="PERCENT">PERCENT (%)</MenuItem>
                <MenuItem value="FIXED">FIXED (so tien)</MenuItem>
              </Select>
            </FormControl>
            <TextField
              label="Gia tri giam"
              value={form.discountValue}
              onChange={(e) => setForm((v) => ({ ...v, discountValue: e.target.value }))}
              inputMode="decimal"
              fullWidth
            />
            <TextField
              label="Don toi thieu"
              value={form.minOrderAmount}
              onChange={(e) => setForm((v) => ({ ...v, minOrderAmount: e.target.value }))}
              inputMode="decimal"
              fullWidth
            />
            <TextField
              label="Tran giam toi da"
              value={form.maxDiscountAmount}
              onChange={(e) => setForm((v) => ({ ...v, maxDiscountAmount: e.target.value }))}
              inputMode="decimal"
              fullWidth
            />
            <TextField
              label="Tong quota"
              value={form.totalQuota}
              onChange={(e) => setForm((v) => ({ ...v, totalQuota: e.target.value }))}
              inputMode="numeric"
              fullWidth
            />
            <TextField
              label="So lan toi da / user"
              value={form.perUserLimit}
              onChange={(e) => setForm((v) => ({ ...v, perUserLimit: e.target.value }))}
              inputMode="numeric"
              fullWidth
            />
            <TextField
              label="Bat dau"
              type="datetime-local"
              value={form.startAt}
              onChange={(e) => setForm((v) => ({ ...v, startAt: e.target.value }))}
              InputLabelProps={{ shrink: true }}
              fullWidth
            />
            <TextField
              label="Ket thuc"
              type="datetime-local"
              value={form.endAt}
              onChange={(e) => setForm((v) => ({ ...v, endAt: e.target.value }))}
              InputLabelProps={{ shrink: true }}
              fullWidth
            />
          </Box>

          <TextField
            label="San pham ap dung (search theo ten/SKU)"
            value={productKeyword}
            onChange={(e) => setProductKeyword(e.target.value)}
            placeholder="Go vai chu de goi y san pham"
            helperText="De trong = voucher ap dung toan bo san pham"
            fullWidth
          />

          {selectedProducts.length > 0 ? (
            <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 1 }}>
              {selectedProducts.map((item) => (
                <Chip
                  key={item.id}
                  label={productOptionLabel(item)}
                  onDelete={() => onRemoveProduct(item.id)}
                  size="small"
                />
              ))}
            </Box>
          ) : null}

          <Paper variant="outlined" sx={{ p: 1, maxHeight: 180, overflowY: 'auto' }}>
            {loadingProducts ? (
              <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, p: 1 }}>
                <CircularProgress size={16} />
                <Typography variant="body2">Dang tim san pham...</Typography>
              </Box>
            ) : mergedProductOptions.length === 0 ? (
              <Typography variant="body2" color="text.secondary" sx={{ p: 1 }}>
                Khong co goi y san pham
              </Typography>
            ) : (
              <Stack spacing={0.5}>
                {mergedProductOptions.map((item) => {
                  const selected = selectedProducts.some((p) => p?.id === item?.id)
                  return (
                    <Button
                      key={item.id}
                      variant={selected ? 'contained' : 'text'}
                      onClick={() => (selected ? onRemoveProduct(item.id) : onSelectProduct(item))}
                      sx={{ justifyContent: 'flex-start', textTransform: 'none' }}
                    >
                      {productOptionLabel(item)}
                    </Button>
                  )
                })}
              </Stack>
            )}
          </Paper>

          <TextField
            label="Mo ta"
            value={form.description}
            onChange={(e) => setForm((v) => ({ ...v, description: e.target.value }))}
            fullWidth
            multiline
            minRows={2}
          />

          <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
            <Switch checked={Boolean(form.active)} onChange={(e) => setForm((v) => ({ ...v, active: e.target.checked }))} />
            <Typography>{form.active ? 'Dang bat' : 'Dang tat'}</Typography>
          </Box>
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose} disabled={saving}>Huy</Button>
        <Button onClick={onSubmit} variant="contained" disabled={saving}>
          {saving ? 'Dang luu...' : 'Luu'}
        </Button>
      </DialogActions>
    </Dialog>
  )
}

export default function VouchersPage() {
  const [vouchers, setVouchers] = useState([])
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const [success, setSuccess] = useState('')
  const [dialogOpen, setDialogOpen] = useState(false)
  const [editingVoucher, setEditingVoucher] = useState(null)
  const [selectedVoucherId, setSelectedVoucherId] = useState(null)
  const [usageLogs, setUsageLogs] = useState([])
  const [loadingLogs, setLoadingLogs] = useState(false)

  const selectedVoucher = useMemo(
    () => vouchers.find((item) => item.id === selectedVoucherId) ?? null,
    [selectedVoucherId, vouchers]
  )

  const refreshVouchers = async () => {
    setLoading(true)
    try {
      const response = await api.get('/marketing/vouchers')
      const list = Array.isArray(response.data) ? response.data : []
      setVouchers(list)
      if (selectedVoucherId && !list.some((item) => item.id === selectedVoucherId)) {
        setSelectedVoucherId(null)
        setUsageLogs([])
      }
    } catch (err) {
      setError(toErrorMessage(err))
    } finally {
      setLoading(false)
    }
  }

  const loadUsageLogs = async (voucherId) => {
    if (!voucherId) {
      setUsageLogs([])
      return
    }
    setLoadingLogs(true)
    try {
      const response = await api.get(`/marketing/vouchers/${voucherId}/usage-logs`)
      setUsageLogs(Array.isArray(response.data) ? response.data : [])
    } catch (err) {
      setError(toErrorMessage(err))
    } finally {
      setLoadingLogs(false)
    }
  }

  useEffect(() => {
    refreshVouchers()
  }, [])

  const onOpenCreate = () => {
    setEditingVoucher(null)
    setDialogOpen(true)
  }

  const onOpenEdit = (voucher) => {
    setEditingVoucher(voucher)
    setDialogOpen(true)
  }

  const onSaved = async () => {
    setError('')
    setSuccess('Da luu voucher')
    await refreshVouchers()
    if (selectedVoucherId) {
      await loadUsageLogs(selectedVoucherId)
    }
  }

  const onViewLogs = async (voucher) => {
    setSelectedVoucherId(voucher.id)
    setUsageLogs([])
    await loadUsageLogs(voucher.id)
  }

  return (
    <Stack spacing={2}>
      <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <Typography variant="h5">Voucher Marketing</Typography>
        <Box sx={{ display: 'flex', gap: 1 }}>
          <Button variant="outlined" onClick={refreshVouchers} disabled={loading}>
            {loading ? 'Dang tai...' : 'Lam moi'}
          </Button>
          <Button variant="contained" onClick={onOpenCreate}>Tao voucher</Button>
        </Box>
      </Box>

      {error ? <Alert severity="error">{error}</Alert> : null}
      {success ? <Alert severity="success">{success}</Alert> : null}

      <Paper variant="outlined">
        <TableContainer>
          <Table size="small">
            <TableHead>
              <TableRow>
                <TableCell>Code</TableCell>
                <TableCell>Tieu de</TableCell>
                <TableCell>Rule</TableCell>
                <TableCell>Quota</TableCell>
                <TableCell>Thoi gian</TableCell>
                <TableCell>Trang thai</TableCell>
                <TableCell align="right">Thao tac</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {vouchers.length === 0 ? (
                <TableRow>
                  <TableCell colSpan={7}>Chua co voucher</TableCell>
                </TableRow>
              ) : (
                vouchers.map((voucher) => (
                  <TableRow key={voucher.id} hover selected={selectedVoucherId === voucher.id}>
                    <TableCell>{voucher.code}</TableCell>
                    <TableCell>{voucher.title}</TableCell>
                    <TableCell>
                      <Stack spacing={0.25}>
                        <Typography variant="body2">
                          {voucher.discountType === 'PERCENT'
                            ? `${toMoney(voucher.discountValue)}%`
                            : toMoney(voucher.discountValue)}
                        </Typography>
                        <Typography variant="caption" color="text.secondary">
                          Min: {toMoney(voucher.minOrderAmount)} | Cap: {toMoney(voucher.maxDiscountAmount)}
                        </Typography>
                        <Typography variant="caption" color="text.secondary">
                          /user: {voucher.perUserLimit ?? 'khong gioi han'}
                        </Typography>
                        <Typography variant="caption" color="text.secondary">
                          SP: {Array.isArray(voucher.eligibleProducts) && voucher.eligibleProducts.length > 0
                            ? voucher.eligibleProducts.map((item) => item.sku || item.name || `#${item.id}`).join(', ')
                            : 'tat ca'}
                        </Typography>
                      </Stack>
                    </TableCell>
                    <TableCell>
                      <Stack spacing={0.25}>
                        <Typography variant="body2">
                          Da dung: {voucher.usedCount ?? 0}
                        </Typography>
                        <Typography variant="caption" color="text.secondary">
                          Con lai: {voucher.remainingQuota ?? 'khong gioi han'}
                        </Typography>
                      </Stack>
                    </TableCell>
                    <TableCell>
                      <Typography variant="body2">{voucher.startAt ? voucher.startAt.replace('T', ' ') : 'Ngay'}</Typography>
                      <Typography variant="caption" color="text.secondary">{voucher.endAt ? voucher.endAt.replace('T', ' ') : 'Vo han'}</Typography>
                    </TableCell>
                    <TableCell>
                      <Chip
                        size="small"
                        color={voucher.active ? 'success' : 'default'}
                        label={voucher.active ? 'DANG BAT' : 'DANG TAT'}
                      />
                    </TableCell>
                    <TableCell align="right">
                      <Box sx={{ display: 'inline-flex', gap: 1 }}>
                        <Button size="small" onClick={() => onOpenEdit(voucher)}>Sua</Button>
                        <Button size="small" onClick={() => onViewLogs(voucher)}>Log</Button>
                      </Box>
                    </TableCell>
                  </TableRow>
                ))
              )}
            </TableBody>
          </Table>
        </TableContainer>
      </Paper>

      {selectedVoucher ? (
        <Paper variant="outlined" sx={{ p: 2 }}>
          <Stack spacing={1.5}>
            <Typography variant="h6">Usage log - {selectedVoucher.code}</Typography>
            <TableContainer>
              <Table size="small">
                <TableHead>
                  <TableRow>
                    <TableCell>Thoi gian</TableCell>
                    <TableCell>User</TableCell>
                    <TableCell>Order</TableCell>
                    <TableCell>Gia tri don</TableCell>
                    <TableCell>Giam</TableCell>
                    <TableCell>Thanh tien</TableCell>
                    <TableCell>Status</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {loadingLogs ? (
                    <TableRow>
                      <TableCell colSpan={7}>Dang tai log...</TableCell>
                    </TableRow>
                  ) : usageLogs.length === 0 ? (
                    <TableRow>
                      <TableCell colSpan={7}>Chua co log su dung</TableCell>
                    </TableRow>
                  ) : (
                    usageLogs.map((log) => (
                      <TableRow key={log.id} hover>
                        <TableCell>{log.usedAt ? String(log.usedAt).replace('T', ' ') : '-'}</TableCell>
                        <TableCell>{log.username || '-'}</TableCell>
                        <TableCell>{log.orderId ?? '-'}</TableCell>
                        <TableCell>{toMoney(log.orderAmount)}</TableCell>
                        <TableCell>{toMoney(log.discountAmount)}</TableCell>
                        <TableCell>{toMoney(log.finalAmount)}</TableCell>
                        <TableCell>
                          <Chip
                            size="small"
                            color={log.status === 'USED' ? 'info' : 'default'}
                            label={log.status || '-'}
                          />
                        </TableCell>
                      </TableRow>
                    ))
                  )}
                </TableBody>
              </Table>
            </TableContainer>
          </Stack>
        </Paper>
      ) : null}

      <VoucherDialog
        open={dialogOpen}
        editingVoucher={editingVoucher}
        onClose={() => setDialogOpen(false)}
        onSaved={onSaved}
      />
    </Stack>
  )
}
