import { useEffect, useState } from 'react'
import {
  Alert,
  Box,
  Button,
  FormControl,
  InputLabel,
  MenuItem,
  Paper,
  Select,
  Stack,
  TextField,
  Typography,
} from '@mui/material'
import { api } from '../api/http.js'

function normalizeText(value) {
  return typeof value === 'string' ? value.trim() : ''
}

async function extractApiError(err) {
  const status = err?.response?.status
  if (status === 403) {
    return 'Bạn không có quyền SUPER_ADMIN để thực hiện chức năng này.'
  }

  const responseData = err?.response?.data
  if (typeof responseData === 'string' && responseData.trim()) {
    return responseData.trim()
  }

  if (responseData && typeof responseData === 'object') {
    const message = normalizeText(responseData.message) || normalizeText(responseData.error)
    if (message.includes('Type definition error: [simple type, class com.fasterxml.jackson.databind.JsonNode]')) {
      return 'Backend dang gap loi parse du lieu JSON (JsonNode). Hay reload trang va thu lai; neu van loi, can restart backend de nhan ban fix moi nhat.'
    }
    if (message) return message
  }

  if (status) {
    return `Không thể đồng bộ dữ liệu từ Mouser (HTTP ${status}).`
  }
  return 'Không thể đồng bộ dữ liệu từ Mouser. Kiểm tra API key và endpoint backend.'
}

export default function AdminDataSyncPage() {
  const [keyword, setKeyword] = useState('ESP32')
  const [limit, setLimit] = useState('20')
  const [categoryId, setCategoryId] = useState('')
  const [categories, setCategories] = useState([])
  const [mouserApiKey, setMouserApiKey] = useState('')

  const [loading, setLoading] = useState(false)
  const [loadingCategories, setLoadingCategories] = useState(false)
  const [error, setError] = useState('')
  const [message, setMessage] = useState('')
  const [categoryLoadError, setCategoryLoadError] = useState('')

  const loadCategories = async () => {
    setLoadingCategories(true)
    setCategoryLoadError('')

    try {
      const response = await api.get('/api/admin/category-options')
      setCategories(Array.isArray(response.data) ? response.data : [])
    } catch {
      setCategories([])
      setCategoryLoadError('Khong tai duoc danh muc. Ban van co the dong bo ma khong gan danh muc.')
    } finally {
      setLoadingCategories(false)
    }
  }

  useEffect(() => {
    loadCategories()
  }, [])

  const onSync = async () => {
    setError('')
    setMessage('')
    setLoading(true)

    const payload = {}
    if (keyword.trim()) {
      payload.keyword = keyword.trim()
    }

    if (limit.trim()) {
      const parsedLimit = Number(limit)
      if (Number.isNaN(parsedLimit) || parsedLimit <= 0) {
        setLoading(false)
        setError('Limit phải là số dương.')
        return
      }
      payload.limit = parsedLimit
    }

    if (categoryId) {
      const parsedCategoryId = Number(categoryId)
      if (!Number.isInteger(parsedCategoryId) || parsedCategoryId <= 0) {
        setLoading(false)
        setError('Danh muc khong hop le.')
        return
      }
      payload.categoryId = parsedCategoryId
    }

    if (normalizeText(mouserApiKey)) {
      payload.apiKey = normalizeText(mouserApiKey)
    }

    try {
      const response = await api.post('/api/admin/export-components-mouser', payload)
      const inserted = Number(response?.data?.inserted ?? 0)
      const updated = Number(response?.data?.updated ?? 0)
      const skipped = Number(response?.data?.skipped ?? 0)
      const fetched = Number(response?.data?.fetched ?? inserted + updated + skipped)
      setMessage(
        `Đã đồng bộ vào database thành công. Fetched: ${fetched}, thêm mới: ${inserted}, cập nhật: ${updated}, bỏ qua: ${skipped}.`
      )
    } catch (err) {
      const apiError = await extractApiError(err)
      setError(apiError)
    } finally {
      setLoading(false)
    }
  }

  return (
    <Stack spacing={2}>
      <Typography variant="h5">Đồng bộ dữ liệu linh kiện (Super Admin)</Typography>

      <Paper variant="outlined" sx={{ p: 2 }}>
        <Stack spacing={2}>
          <Typography variant="body2" color="text.secondary">
            Chức năng này đồng bộ trực tiếp linh kiện từ Mouser API vào database. Chỉ dành cho SUPER_ADMIN.
          </Typography>

          {error ? <Alert severity="error">{error}</Alert> : null}
          {message ? <Alert severity="success">{message}</Alert> : null}
          {categoryLoadError ? <Alert severity="warning">{categoryLoadError}</Alert> : null}

          <Box
            sx={{
              display: 'grid',
              gap: 2,
              gridTemplateColumns: {
                xs: '1fr',
                md: '2fr 1fr 1fr',
              },
            }}
          >
            <TextField
              label="Keyword"
              value={keyword}
              onChange={(e) => setKeyword(e.target.value)}
              fullWidth
            />
            <TextField
              label="Limit"
              value={limit}
              onChange={(e) => setLimit(e.target.value)}
              inputMode="numeric"
              fullWidth
            />
            <FormControl fullWidth>
              <InputLabel id="admin-sync-category-label">Danh muc (optional)</InputLabel>
              <Select
                labelId="admin-sync-category-label"
                label="Danh muc (optional)"
                value={categoryId}
                onChange={(e) => setCategoryId(e.target.value)}
                disabled={loadingCategories}
              >
                <MenuItem value="">
                  <em>Tat ca danh muc</em>
                </MenuItem>
                {categories.map((category) => (
                  <MenuItem key={category.id} value={String(category.id)}>
                    {category.parentName ? `${category.parentName} / ${category.name}` : category.name}
                  </MenuItem>
                ))}
              </Select>
            </FormControl>
          </Box>

          <Typography variant="subtitle2">Cấu hình Mouser (tùy chọn)</Typography>
          <Typography variant="body2" color="text.secondary">
            Nếu backend chưa set mouser.api.key, bạn có thể nhập trực tiếp Mouser API key tại đây.
          </Typography>


          <TextField
            label="Mouser API Key (optional)"
            type="password"
            value={mouserApiKey}
            onChange={(e) => setMouserApiKey(e.target.value)}
            fullWidth
          />

          <Box>
            <Button
              variant="contained"
              onClick={onSync}
              disabled={loading}
            >
              {loading ? 'Đang đồng bộ...' : 'Đồng bộ trực tiếp vào database'}
            </Button>
          </Box>
        </Stack>
      </Paper>
    </Stack>
  )
}
