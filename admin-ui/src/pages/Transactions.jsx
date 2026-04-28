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

const TYPE_OPTIONS = [
  { value: 'INCOME', label: 'Thu' },
  { value: 'EXPENSE', label: 'Chi' },
  { value: 'REFUND', label: 'Hoàn tiền' },
]

const STATUS_OPTIONS = [
  { value: 'PENDING', label: 'Chờ xác nhận' },
  { value: 'SUCCESS', label: 'Thành công' },
  { value: 'REVERSED', label: 'Đã đảo' },
]

const METHOD_OPTIONS = [
  { value: 'CASH', label: 'Tiền mặt' },
  { value: 'BANK_TRANSFER', label: 'Chuyển khoản' },
  { value: 'CARD', label: 'Thẻ' },
  { value: 'E_WALLET', label: 'Ví điện tử' },
  { value: 'ORDER', label: 'Tự động từ đơn' },
  { value: 'OTHER', label: 'Khác' },
]

function toErrorMessage(err) {
  const status = err?.response?.status
  const data = err?.response?.data
  if (typeof data === 'string' && data.trim()) return `${status ?? ''} ${data}`.trim()
  if (data && typeof data === 'object') {
    if (typeof data.error === 'string' && data.error.trim()) return `${status ?? ''} ${data.error}`.trim()
    if (typeof data.message === 'string' && data.message.trim()) return `${status ?? ''} ${data.message}`.trim()
  }
  return status ? `Yêu cầu thất bại (${status})` : 'Yêu cầu thất bại'
}

function toMoney(value) {
  const amount = Number(value)
  if (!Number.isFinite(amount)) return '-'
  return `$${amount.toLocaleString('en-US', { minimumFractionDigits: 0, maximumFractionDigits: 2 })}`
}

function toDateTimeInput(value) {
  if (!value) return ''
  const text = String(value).trim()
  if (!text) return ''
  return text.length >= 16 ? text.slice(0, 16) : text
}

function toOptionalLong(value) {
  const parsed = Number(value)
  return Number.isInteger(parsed) && parsed > 0 ? parsed : null
}

function statusColor(status) {
  const key = String(status || '').toUpperCase()
  if (key === 'SUCCESS') return 'success'
  if (key === 'PENDING') return 'warning'
  if (key === 'REVERSED') return 'default'
  return 'default'
}

function typeColor(type) {
  const key = String(type || '').toUpperCase()
  if (key === 'INCOME') return 'success'
  if (key === 'EXPENSE' || key === 'REFUND') return 'error'
  return 'default'
}

function toTypeLabel(type) {
  const key = String(type || '').toUpperCase()
  return TYPE_OPTIONS.find((item) => item.value === key)?.label || '-'
}

function toStatusLabel(status) {
  const key = String(status || '').toUpperCase()
  return STATUS_OPTIONS.find((item) => item.value === key)?.label || '-'
}

function toMethodLabel(method) {
  const key = String(method || '').toUpperCase()
  return METHOD_OPTIONS.find((item) => item.value === key)?.label || '-'
}

export default function TransactionsPage() {
  const permissions = getPermissions()
  const canView = permissions.includes('STATS_VIEW')

  const [transactions, setTransactions] = useState([])
  const [summary, setSummary] = useState({
    totalIncome: 0,
    totalExpense: 0,
    netAmount: 0,
    totalTransactions: 0,
  })
  const [loading, setLoading] = useState(false)
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState('')
  const [success, setSuccess] = useState('')

  const [filters, setFilters] = useState({
    fromAt: '',
    toAt: '',
    type: '',
    status: '',
    method: '',
    orderId: '',
    keyword: '',
  })

  const [createForm, setCreateForm] = useState({
    type: 'EXPENSE',
    amount: '',
    method: 'CASH',
    status: 'PENDING',
    orderId: '',
    referenceCode: '',
    note: '',
  })

  const requestParams = useMemo(() => {
    const params = {}
    if (filters.fromAt) params.fromAt = filters.fromAt
    if (filters.toAt) params.toAt = filters.toAt
    if (filters.type) params.type = filters.type
    if (filters.status) params.status = filters.status
    if (filters.method) params.method = filters.method
    const orderId = toOptionalLong(filters.orderId)
    if (orderId) params.orderId = orderId
    if (String(filters.keyword || '').trim()) params.keyword = String(filters.keyword).trim()
    return params
  }, [filters])

  const showError = (err) => {
    setSuccess('')
    setError(toErrorMessage(err))
  }

  const showSuccess = (message) => {
    setError('')
    setSuccess(message)
  }

  const reload = async () => {
    if (!canView) return
    setLoading(true)
    setError('')
    try {
      const [listRes, summaryRes] = await Promise.all([
        api.get('/transactions', { params: requestParams }),
        api.get('/transactions/summary', { params: requestParams }),
      ])
      setTransactions(Array.isArray(listRes.data) ? listRes.data : [])
      setSummary(summaryRes?.data || { totalIncome: 0, totalExpense: 0, netAmount: 0, totalTransactions: 0 })
    } catch (err) {
      showError(err)
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    reload()
  }, [])

  const onCreate = async () => {
    const amount = Number(createForm.amount)
    if (!Number.isFinite(amount) || amount <= 0) {
      setError('Số tiền phải > 0')
      return
    }
    setSubmitting(true)
    try {
      await api.post('/transactions', {
        type: createForm.type,
        amount,
        method: createForm.method || null,
        status: createForm.status || null,
        orderId: toOptionalLong(createForm.orderId),
        referenceCode: String(createForm.referenceCode || '').trim() || null,
        note: String(createForm.note || '').trim() || null,
      })
      showSuccess('Đã tạo giao dịch')
      setCreateForm({
        type: 'EXPENSE',
        amount: '',
        method: 'CASH',
        status: 'PENDING',
        orderId: '',
        referenceCode: '',
        note: '',
      })
      await reload()
    } catch (err) {
      showError(err)
    } finally {
      setSubmitting(false)
    }
  }

  const onConfirm = async (transactionId) => {
    setSubmitting(true)
    try {
      await api.post(`/transactions/${transactionId}/confirm`)
      showSuccess(`Đã xác nhận giao dịch #${transactionId}`)
      await reload()
    } catch (err) {
      showError(err)
    } finally {
      setSubmitting(false)
    }
  }

  const onReverse = async (transactionId) => {
    const reason = window.prompt('Nhập lý do đảo giao dịch (không bắt buộc):') || ''
    setSubmitting(true)
    try {
      await api.post(`/transactions/${transactionId}/reverse`, { reason: reason.trim() || null })
      showSuccess(`Đã đảo giao dịch #${transactionId}`)
      await reload()
    } catch (err) {
      showError(err)
    } finally {
      setSubmitting(false)
    }
  }

  if (!canView) {
    return <Alert severity="warning">Bạn không có quyền xem giao dịch.</Alert>
  }

  return (
    <Stack spacing={2}>
      <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <Typography variant="h5">Giao dịch</Typography>
        <Button variant="outlined" onClick={reload} disabled={loading || submitting}>
          {loading ? 'Đang tải...' : 'Tải lại'}
        </Button>
      </Box>

      {error ? <Alert severity="error">{error}</Alert> : null}
      {success ? <Alert severity="success">{success}</Alert> : null}

      <Paper variant="outlined" sx={{ p: 2 }}>
        <Stack spacing={2}>
          <Typography variant="h6">Tổng quan</Typography>
          <Box sx={{ display: 'grid', gap: 2, gridTemplateColumns: { xs: '1fr', md: 'repeat(4, 1fr)' } }}>
            <Paper variant="outlined" sx={{ p: 2 }}>
              <Typography variant="caption" color="text.secondary">Tổng thu (USD)</Typography>
              <Typography variant="h6" color="success.main">{toMoney(summary.totalIncome)}</Typography>
            </Paper>
            <Paper variant="outlined" sx={{ p: 2 }}>
              <Typography variant="caption" color="text.secondary">Tổng chi (USD)</Typography>
              <Typography variant="h6" color="error.main">{toMoney(summary.totalExpense)}</Typography>
            </Paper>
            <Paper variant="outlined" sx={{ p: 2 }}>
              <Typography variant="caption" color="text.secondary">Thuần (USD)</Typography>
              <Typography variant="h6" color={Number(summary.netAmount) >= 0 ? 'success.main' : 'error.main'}>
                {toMoney(summary.netAmount)}
              </Typography>
            </Paper>
            <Paper variant="outlined" sx={{ p: 2 }}>
              <Typography variant="caption" color="text.secondary">Số giao dịch</Typography>
              <Typography variant="h6">{summary.totalTransactions ?? 0}</Typography>
            </Paper>
          </Box>
        </Stack>
      </Paper>

      <Paper variant="outlined" sx={{ p: 2 }}>
        <Stack spacing={2}>
          <Typography variant="h6">Bộ lọc</Typography>
          <Box sx={{ display: 'grid', gap: 2, gridTemplateColumns: { xs: '1fr', md: '1.2fr 1.2fr 1fr 1fr 1fr 1fr 1.2fr' } }}>
            <TextField
              label="Từ ngày giờ"
              type="datetime-local"
              value={filters.fromAt}
              onChange={(e) => setFilters((v) => ({ ...v, fromAt: e.target.value }))}
              InputLabelProps={{ shrink: true }}
            />
            <TextField
              label="Đến ngày giờ"
              type="datetime-local"
              value={filters.toAt}
              onChange={(e) => setFilters((v) => ({ ...v, toAt: e.target.value }))}
              InputLabelProps={{ shrink: true }}
            />
            <FormControl>
              <InputLabel id="tx-filter-type">Loại</InputLabel>
              <Select
                labelId="tx-filter-type"
                label="Loại"
                value={filters.type}
                onChange={(e) => setFilters((v) => ({ ...v, type: e.target.value }))}
              >
                <MenuItem value="">Tất cả</MenuItem>
                {TYPE_OPTIONS.map((option) => (
                  <MenuItem key={option.value} value={option.value}>{option.label}</MenuItem>
                ))}
              </Select>
            </FormControl>
            <FormControl>
              <InputLabel id="tx-filter-status">Trạng thái</InputLabel>
              <Select
                labelId="tx-filter-status"
                label="Trạng thái"
                value={filters.status}
                onChange={(e) => setFilters((v) => ({ ...v, status: e.target.value }))}
              >
                <MenuItem value="">Tất cả</MenuItem>
                {STATUS_OPTIONS.map((option) => (
                  <MenuItem key={option.value} value={option.value}>{option.label}</MenuItem>
                ))}
              </Select>
            </FormControl>
            <FormControl>
              <InputLabel id="tx-filter-method">Phương thức</InputLabel>
              <Select
                labelId="tx-filter-method"
                label="Phương thức"
                value={filters.method}
                onChange={(e) => setFilters((v) => ({ ...v, method: e.target.value }))}
              >
                <MenuItem value="">Tất cả</MenuItem>
                {METHOD_OPTIONS.map((option) => (
                  <MenuItem key={option.value} value={option.value}>{option.label}</MenuItem>
                ))}
              </Select>
            </FormControl>
            <TextField
              label="Mã đơn"
              value={filters.orderId}
              onChange={(e) => setFilters((v) => ({ ...v, orderId: e.target.value }))}
              inputMode="numeric"
            />
            <TextField
              label="Từ khóa"
              value={filters.keyword}
              onChange={(e) => setFilters((v) => ({ ...v, keyword: e.target.value }))}
            />
          </Box>
          <Box sx={{ display: 'flex', gap: 1 }}>
            <Button variant="contained" onClick={reload} disabled={loading || submitting}>Áp dụng</Button>
            <Button
              variant="outlined"
              onClick={() => {
                setFilters({
                  fromAt: '',
                  toAt: '',
                  type: '',
                  status: '',
                  method: '',
                  orderId: '',
                  keyword: '',
                })
              }}
              disabled={loading || submitting}
            >
              Xóa lọc
            </Button>
          </Box>
        </Stack>
      </Paper>

      <Paper variant="outlined" sx={{ p: 2 }}>
        <Stack spacing={2}>
          <Typography variant="h6">Tạo giao dịch thủ công</Typography>
          <Box sx={{ display: 'grid', gap: 2, gridTemplateColumns: { xs: '1fr', md: '1fr 1fr 1fr 1fr 1fr 1.2fr 2fr auto' } }}>
            <FormControl>
              <InputLabel id="tx-create-type">Loại</InputLabel>
              <Select
                labelId="tx-create-type"
                label="Loại"
                value={createForm.type}
                onChange={(e) => setCreateForm((v) => ({ ...v, type: e.target.value }))}
              >
                {TYPE_OPTIONS.map((option) => (
                  <MenuItem key={option.value} value={option.value}>{option.label}</MenuItem>
                ))}
              </Select>
            </FormControl>
            <TextField
              label="Số tiền (USD)"
              value={createForm.amount}
              onChange={(e) => setCreateForm((v) => ({ ...v, amount: e.target.value }))}
              inputMode="decimal"
            />
            <FormControl>
              <InputLabel id="tx-create-method">Phương thức</InputLabel>
              <Select
                labelId="tx-create-method"
                label="Phương thức"
                value={createForm.method}
                onChange={(e) => setCreateForm((v) => ({ ...v, method: e.target.value }))}
              >
                {METHOD_OPTIONS.filter((option) => option.value !== 'ORDER').map((option) => (
                  <MenuItem key={option.value} value={option.value}>{option.label}</MenuItem>
                ))}
              </Select>
            </FormControl>
            <FormControl>
              <InputLabel id="tx-create-status">Trạng thái</InputLabel>
              <Select
                labelId="tx-create-status"
                label="Trạng thái"
                value={createForm.status}
                onChange={(e) => setCreateForm((v) => ({ ...v, status: e.target.value }))}
              >
                {STATUS_OPTIONS.filter((option) => option.value !== 'REVERSED').map((option) => (
                  <MenuItem key={option.value} value={option.value}>{option.label}</MenuItem>
                ))}
              </Select>
            </FormControl>
            <TextField
              label="Mã đơn (nếu có)"
              value={createForm.orderId}
              onChange={(e) => setCreateForm((v) => ({ ...v, orderId: e.target.value }))}
              inputMode="numeric"
            />
            <TextField
              label="Mã tham chiếu"
              value={createForm.referenceCode}
              onChange={(e) => setCreateForm((v) => ({ ...v, referenceCode: e.target.value }))}
            />
            <TextField
              label="Ghi chú"
              value={createForm.note}
              onChange={(e) => setCreateForm((v) => ({ ...v, note: e.target.value }))}
            />
            <Button variant="contained" onClick={onCreate} disabled={submitting || loading}>
              Tạo
            </Button>
          </Box>
        </Stack>
      </Paper>

      <Paper variant="outlined">
        <TableContainer>
          <Table size="small">
            <TableHead>
              <TableRow>
                <TableCell>Mã GD</TableCell>
                <TableCell>Thời gian</TableCell>
                <TableCell>Loại</TableCell>
                <TableCell>Trạng thái</TableCell>
                <TableCell>Số tiền (USD)</TableCell>
                <TableCell>Phương thức</TableCell>
                <TableCell>Mã đơn</TableCell>
                <TableCell>Tham chiếu</TableCell>
                <TableCell>Ghi chú</TableCell>
                <TableCell>Người tạo</TableCell>
                <TableCell align="right">Thao tác</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {transactions.length === 0 ? (
                <TableRow>
                  <TableCell colSpan={11}>Không có giao dịch</TableCell>
                </TableRow>
              ) : (
                transactions.map((tx) => {
                  const canConfirm = String(tx.status || '').toUpperCase() === 'PENDING'
                  const canReverse = String(tx.status || '').toUpperCase() === 'SUCCESS'
                  return (
                    <TableRow key={tx.id} hover>
                      <TableCell>{tx.id}</TableCell>
                      <TableCell>{toDateTimeInput(tx.createdAt) || '-'}</TableCell>
                      <TableCell>
                        <Chip size="small" color={typeColor(tx.type)} label={toTypeLabel(tx.type)} />
                      </TableCell>
                      <TableCell>
                        <Chip size="small" color={statusColor(tx.status)} label={toStatusLabel(tx.status)} />
                      </TableCell>
                      <TableCell>{toMoney(tx.amount)}</TableCell>
                      <TableCell>{toMethodLabel(tx.method)}</TableCell>
                      <TableCell>{tx.orderId ?? '-'}</TableCell>
                      <TableCell>{tx.referenceCode || '-'}</TableCell>
                      <TableCell>{tx.note || '-'}</TableCell>
                      <TableCell>{tx.createdBy || '-'}</TableCell>
                      <TableCell align="right">
                        <Box sx={{ display: 'inline-flex', gap: 1 }}>
                          <Button
                            size="small"
                            variant="outlined"
                            onClick={() => onConfirm(tx.id)}
                            disabled={!canConfirm || submitting || loading}
                          >
                            Xác nhận
                          </Button>
                          <Button
                            size="small"
                            color="error"
                            variant="outlined"
                            onClick={() => onReverse(tx.id)}
                            disabled={!canReverse || submitting || loading}
                          >
                            Đảo
                          </Button>
                        </Box>
                      </TableCell>
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
