import { useEffect, useState } from 'react'
import {
  Alert,
  Box,
  Button,
  CircularProgress,
  Paper,
  Stack,
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
  return status ? `Yêu cầu thất bại (${status})` : 'Yêu cầu thất bại'
}

export default function SettingsPage() {
  const permissions = getPermissions()
  const canManage = permissions.includes('USER_MANAGE')

  const [form, setForm] = useState({
    storeName: '',
    phone: '',
    address: '',
    mouserApiKey: '',
  })
  const [loading, setLoading] = useState(false)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')
  const [success, setSuccess] = useState('')

  const loadSettings = async () => {
    if (!canManage) return
    setLoading(true)
    setError('')
    try {
      const response = await api.get('/settings')
      const data = response?.data || {}
      setForm({
        storeName: data.storeName || '',
        phone: data.phone || '',
        address: data.address || '',
        mouserApiKey: data.mouserApiKey || '',
      })
    } catch (err) {
      setError(toErrorMessage(err))
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    loadSettings()
  }, [])

  const onSave = async () => {
    setSaving(true)
    setError('')
    setSuccess('')
    try {
      await api.put('/settings', {
        storeName: String(form.storeName || '').trim(),
        phone: String(form.phone || '').trim(),
        address: String(form.address || '').trim(),
        mouserApiKey: String(form.mouserApiKey || '').trim(),
      })
      setSuccess('Đã lưu cấu hình')
      await loadSettings()
    } catch (err) {
      setError(toErrorMessage(err))
    } finally {
      setSaving(false)
    }
  }

  if (!canManage) {
    return <Alert severity="warning">Bạn không có quyền vào mục Cấu hình.</Alert>
  }

  return (
    <Stack spacing={2}>
      <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <Typography variant="h5">Cấu hình</Typography>
        <Button variant="outlined" onClick={loadSettings} disabled={loading || saving}>
          {loading ? 'Đang tải...' : 'Tải lại'}
        </Button>
      </Box>

      {error ? <Alert severity="error">{error}</Alert> : null}
      {success ? <Alert severity="success">{success}</Alert> : null}

      <Paper variant="outlined" sx={{ p: 2 }}>
        <Stack spacing={2}>
          <TextField
            label="Tên cửa hàng"
            placeholder="Ví dụ: T-Group Components"
            value={form.storeName}
            onChange={(e) => setForm((v) => ({ ...v, storeName: e.target.value }))}
            disabled={loading || saving}
            fullWidth
          />

          <TextField
            label="Số điện thoại"
            placeholder="Ví dụ: 0123456789"
            value={form.phone}
            onChange={(e) => setForm((v) => ({ ...v, phone: e.target.value }))}
            disabled={loading || saving}
            fullWidth
          />

          <TextField
            label="Địa chỉ"
            placeholder="Ví dụ: TP.HCM"
            value={form.address}
            onChange={(e) => setForm((v) => ({ ...v, address: e.target.value }))}
            disabled={loading || saving}
            fullWidth
          />

          <TextField
            label="Mouser API Key"
            placeholder="Nhập Mouser API Key"
            value={form.mouserApiKey}
            onChange={(e) => setForm((v) => ({ ...v, mouserApiKey: e.target.value }))}
            disabled={loading || saving}
            fullWidth
          />

          <Box sx={{ display: 'flex', justifyContent: 'flex-end' }}>
            <Button variant="contained" onClick={onSave} disabled={loading || saving}>
              {saving ? <CircularProgress size={20} /> : 'Lưu'}
            </Button>
          </Box>
        </Stack>
      </Paper>
    </Stack>
  )
}
