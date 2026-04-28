import { useEffect, useMemo, useState } from 'react'
import {
  Alert,
  Box,
  Button,
  Chip,
  FormControl,
  InputLabel,
  MenuItem,
  Paper,
  Select,
  Stack,
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
import { getPermissions } from '../auth/auth.js'

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

function toInt(value) {
  const parsed = Number(value)
  return Number.isInteger(parsed) ? parsed : null
}

function toMoney(value) {
  const amount = Number(value)
  if (!Number.isFinite(amount)) return '-'
  return `$${amount.toLocaleString('en-US', { minimumFractionDigits: 0, maximumFractionDigits: 2 })}`
}

function statusColor(status) {
  const key = String(status || '').trim().toUpperCase()
  if (key === 'CONFIRMED') return 'info'
  if (key === 'SHIPPED' || key === 'FULFILLED') return 'success'
  if (key === 'CANCELLED') return 'error'
  if (key === 'RETURNED') return 'warning'
  return 'default'
}

export default function OrdersPage() {
  const permissions = getPermissions()
  const canView = permissions.includes('ORDER_VIEW_SELF') || permissions.includes('ORDER_VIEW_ALL')
  const canCreate = permissions.includes('ORDER_CREATE')
  const canModifyStatus = permissions.includes('ORDER_MODIFY_STATUS')
  const canViewProducts = permissions.includes('PRODUCT_VIEW')

  const [orders, setOrders] = useState([])
  const [products, setProducts] = useState([])
  const [loading, setLoading] = useState(false)
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState('')
  const [success, setSuccess] = useState('')

  const [createForm, setCreateForm] = useState({
    productId: '',
    quantity: '1',
    voucherCode: '',
  })

  const productById = useMemo(() => {
    const map = new Map()
    for (const item of products) {
      map.set(item.productId, item)
    }
    return map
  }, [products])

  const showError = (err) => {
    setSuccess('')
    setError(toErrorMessage(err))
  }

  const showSuccess = (message) => {
    setError('')
    setSuccess(message)
  }

  const refreshOrders = async () => {
    if (!canView) return
    setLoading(true)
    try {
      const response = await api.get('/orders')
      setOrders(Array.isArray(response.data) ? response.data : [])
    } catch (err) {
      showError(err)
    } finally {
      setLoading(false)
    }
  }

  const refreshProducts = async () => {
    if (!canViewProducts) return
    try {
      const response = await api.get('/inventory/stocks')
      setProducts(Array.isArray(response.data) ? response.data : [])
    } catch (err) {
      showError(err)
    }
  }

  useEffect(() => {
    refreshOrders()
    refreshProducts()
  }, [])

  const onCreateOrder = async () => {
    const productId = toInt(createForm.productId)
    const quantity = toInt(createForm.quantity)
    if (!productId || productId <= 0 || !quantity || quantity <= 0) {
      setError('Can chon san pham va quantity > 0')
      return
    }

    setSubmitting(true)
    try {
      await api.post('/orders', {
        productId,
        quantity,
        voucherCode: String(createForm.voucherCode || '').trim() || null,
      })
      showSuccess('Da tao don va giu cho ton kha dung')
      setCreateForm({ productId: '', quantity: '1', voucherCode: '' })
      await Promise.all([refreshOrders(), refreshProducts()])
    } catch (err) {
      showError(err)
    } finally {
      setSubmitting(false)
    }
  }

  const onCancelOrder = async (orderId) => {
    if (!orderId) return
    setSubmitting(true)
    try {
      await api.put(`/orders/${orderId}/status`, { status: 'CANCELLED' })
      showSuccess(`Da huy don #${orderId} va nhả giu cho ton`)
      await Promise.all([refreshOrders(), refreshProducts()])
    } catch (err) {
      showError(err)
    } finally {
      setSubmitting(false)
    }
  }

  if (!canView) {
    return <Alert severity="warning">Ban khong co quyen xem don hang.</Alert>
  }

  return (
    <Stack spacing={2}>
      <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <Typography variant="h5">Don hang (Sales)</Typography>
        <Button variant="outlined" onClick={() => Promise.all([refreshOrders(), refreshProducts()])} disabled={loading}>
          {loading ? 'Dang tai...' : 'Lam moi'}
        </Button>
      </Box>

      {error ? <Alert severity="error">{error}</Alert> : null}
      {success ? <Alert severity="success">{success}</Alert> : null}

      {canCreate ? (
        <Paper variant="outlined" sx={{ p: 2 }}>
          <Stack spacing={2}>
            <Typography variant="h6">Tao don hang va giu cho ton kha dung</Typography>
            <Box sx={{ display: 'grid', gap: 2, gridTemplateColumns: { xs: '1fr', md: '2fr 1fr 1.2fr auto' } }}>
              {canViewProducts ? (
                <FormControl fullWidth>
                  <InputLabel id="create-order-product">San pham</InputLabel>
                  <Select
                    labelId="create-order-product"
                    label="San pham"
                    value={createForm.productId}
                    onChange={(e) => setCreateForm((v) => ({ ...v, productId: e.target.value }))}
                  >
                    <MenuItem value="">
                      <em>Chon san pham</em>
                    </MenuItem>
                    {products.map((item) => (
                      <MenuItem key={item.productId} value={String(item.productId)}>
                        {item.sku || `#${item.productId}`} - {item.name} (Kha dung: {item.availableQuantity ?? item.inventoryQuantity ?? 0})
                      </MenuItem>
                    ))}
                  </Select>
                </FormControl>
              ) : (
                <TextField
                  label="Ma san pham"
                  value={createForm.productId}
                  onChange={(e) => setCreateForm((v) => ({ ...v, productId: e.target.value }))}
                />
              )}

              <TextField
                label="So luong"
                value={createForm.quantity}
                onChange={(e) => setCreateForm((v) => ({ ...v, quantity: e.target.value }))}
                inputMode="numeric"
              />

              <TextField
                label="Voucher (neu co)"
                value={createForm.voucherCode}
                onChange={(e) => setCreateForm((v) => ({ ...v, voucherCode: e.target.value }))}
              />

              <Button variant="contained" onClick={onCreateOrder} disabled={submitting}>
                Tao don
              </Button>
            </Box>
          </Stack>
        </Paper>
      ) : null}

      <Paper variant="outlined">
        <TableContainer>
          <Table size="small">
            <TableHead>
              <TableRow>
                <TableCell>Ma don</TableCell>
                <TableCell>Trang thai</TableCell>
                <TableCell>Nguoi dat</TableCell>
                <TableCell>San pham</TableCell>
                <TableCell>SKU</TableCell>
                <TableCell>So luong</TableCell>
                <TableCell>Don gia (USD)</TableCell>
                <TableCell>Tam tinh (USD)</TableCell>
                <TableCell>Voucher</TableCell>
                <TableCell>Giam (USD)</TableCell>
                <TableCell>Thanh tien (USD)</TableCell>
                <TableCell>Ton kha dung hien tai</TableCell>
                {canModifyStatus ? <TableCell align="right">Thao tac</TableCell> : null}
              </TableRow>
            </TableHead>
            <TableBody>
              {orders.length === 0 ? (
                <TableRow>
                  <TableCell colSpan={canModifyStatus ? 13 : 12}>Khong co don hang</TableCell>
                </TableRow>
              ) : (
                orders.map((order) => {
                  const status = String(order.status || '').toUpperCase()
                  const product = productById.get(order.productId)
                  const canCancel = status === 'CONFIRMED'
                  return (
                    <TableRow key={order.id} hover>
                      <TableCell>{order.id}</TableCell>
                      <TableCell>
                        <Chip size="small" color={statusColor(status)} label={status || '-'} />
                      </TableCell>
                      <TableCell>{order.username || '-'}</TableCell>
                      <TableCell>{product?.name || `#${order.productId}`}</TableCell>
                      <TableCell>{product?.sku || '-'}</TableCell>
                      <TableCell>{order.quantity ?? '-'}</TableCell>
                      <TableCell>{toMoney(order.unitPrice)}</TableCell>
                      <TableCell>{toMoney(order.subtotalAmount)}</TableCell>
                      <TableCell>{order.voucherCode || '-'}</TableCell>
                      <TableCell>{toMoney(order.discountAmount)}</TableCell>
                      <TableCell>{toMoney(order.payableAmount)}</TableCell>
                      <TableCell>{product?.availableQuantity ?? product?.inventoryQuantity ?? '-'}</TableCell>
                      {canModifyStatus ? (
                        <TableCell align="right">
                          <Button
                            size="small"
                            color="error"
                            onClick={() => onCancelOrder(order.id)}
                            disabled={submitting || !canCancel}
                          >
                            Huy don
                          </Button>
                        </TableCell>
                      ) : null}
                    </TableRow>
                  )
                })
              )}
            </TableBody>
          </Table>
        </TableContainer>
      </Paper>
    </Stack>
  )
}
