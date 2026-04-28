import { useEffect, useMemo, useState } from 'react'
import {
  Alert,
  Box,
  Button,
  Chip,
  Divider,
  FormControl,
  FormControlLabel,
  InputLabel,
  MenuItem,
  Paper,
  Select,
  Stack,
  Switch,
  Tab,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Tabs,
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
    if (typeof data.error === 'string' && data.error.trim()) {
      return `${status ?? ''} ${data.error}`.trim()
    }
    if (typeof data.message === 'string' && data.message.trim()) {
      return `${status ?? ''} ${data.message}`.trim()
    }
  }
  return status ? `Yeu cau that bai (${status})` : 'Yeu cau that bai'
}

function toInt(value) {
  const parsed = Number(value)
  return Number.isInteger(parsed) ? parsed : null
}

function toNumber(value) {
  const parsed = Number(value)
  return Number.isFinite(parsed) ? parsed : null
}

function availableQtyOf(item) {
  return Number(item?.availableQuantity ?? item?.inventoryQuantity ?? 0)
}

function stockStatusOf(item) {
  const available = availableQtyOf(item)
  if (available <= 0) {
    return { label: 'HET HANG', color: 'error' }
  }
  if (item?.lowStock) {
    return { label: 'SAP HET', color: 'warning' }
  }
  return { label: 'ON DINH', color: 'success' }
}

export default function InventoryPage() {
  const permissions = getPermissions()
  const canView = permissions.includes('PRODUCT_VIEW')
  const canMutate =
    permissions.includes('PRODUCT_CREATE') ||
    permissions.includes('PRODUCT_UPDATE') ||
    permissions.includes('PRODUCT_DELETE')

  const [tab, setTab] = useState(0)
  const [stocks, setStocks] = useState([])
  const [lowStocks, setLowStocks] = useState([])
  const [orders, setOrders] = useState([])
  const [ledger, setLedger] = useState([])
  const [categories, setCategories] = useState([])
  const [reportRows, setReportRows] = useState([])
  const [reportSummary, setReportSummary] = useState({ totalInbound: 0, totalOutbound: 0, totalNet: 0 })

  const [keyword, setKeyword] = useState('')
  const [stockCategoryFilterId, setStockCategoryFilterId] = useState('')
  const [stockAttributeKeyword, setStockAttributeKeyword] = useState('')
  const [threshold, setThreshold] = useState('20')
  const [ledgerSku, setLedgerSku] = useState('')
  const [reportFromDate, setReportFromDate] = useState('')
  const [reportToDate, setReportToDate] = useState('')
  const [reportCategoryId, setReportCategoryId] = useState('')
  const [reportAttributeKeyword, setReportAttributeKeyword] = useState('')
  const [reportLowStockOnly, setReportLowStockOnly] = useState(false)

  const [inbound, setInbound] = useState({
    sku: '',
    quantity: '',
    referenceCode: '',
    location: '',
    note: '',
  })

  const [manualAdd, setManualAdd] = useState({
    name: '',
    mpn: '',
    categoryId: '',
    initialQuantity: '',
    reorderLevel: '10',
    storageLocation: '',
    price: '',
    description: '',
  })

  const [locationUpdate, setLocationUpdate] = useState({
    productId: '',
    storageLocation: '',
    note: '',
  })

  const [adjustment, setAdjustment] = useState({
    productId: '',
    deltaQuantity: '',
    note: '',
  })

  const [returnForm, setReturnForm] = useState({
    orderId: '',
    quantity: '',
    note: '',
  })

  const [loadingStocks, setLoadingStocks] = useState(false)
  const [loadingLowStocks, setLoadingLowStocks] = useState(false)
  const [loadingOrders, setLoadingOrders] = useState(false)
  const [loadingLedger, setLoadingLedger] = useState(false)
  const [loadingCategories, setLoadingCategories] = useState(false)
  const [loadingReport, setLoadingReport] = useState(false)
  const [submitting, setSubmitting] = useState(false)

  const [error, setError] = useState('')
  const [success, setSuccess] = useState('')

  const tabKeys = canMutate ? ['inbound', 'outbound', 'stock', 'ledger', 'report'] : ['stock', 'ledger', 'report']
  const activeTabKey = tabKeys[tab] ?? tabKeys[0]

  useEffect(() => {
    if (tab >= tabKeys.length) {
      setTab(0)
    }
  }, [tab, tabKeys.length])

  const summary = useMemo(() => {
    const totalItems = stocks.length
    const totalPhysical = stocks.reduce((sum, item) => sum + (item.inventoryQuantity ?? 0), 0)
    const totalAvailable = stocks.reduce((sum, item) => sum + (item.availableQuantity ?? item.inventoryQuantity ?? 0), 0)
    return { totalItems, totalPhysical, totalAvailable }
  }, [stocks])

  const lowStockSummary = useMemo(() => {
    const outOfStock = lowStocks.filter((item) => availableQtyOf(item) <= 0).length
    const nearOut = Math.max(0, lowStocks.length - outOfStock)
    return { outOfStock, nearOut }
  }, [lowStocks])

  const showError = (err) => {
    setSuccess('')
    setError(toErrorMessage(err))
  }

  const showSuccess = (message) => {
    setError('')
    setSuccess(message)
  }

  const refreshStocks = async (
    nextKeyword = keyword,
    nextCategoryId = stockCategoryFilterId,
    nextAttributeKeyword = stockAttributeKeyword,
  ) => {
    if (!canView) return
    setLoadingStocks(true)
    try {
      const params = {}
      if (nextKeyword && nextKeyword.trim()) {
        params.keyword = nextKeyword.trim()
      }
      if (nextCategoryId && String(nextCategoryId).trim()) {
        params.categoryId = Number(nextCategoryId)
      }
      if (nextAttributeKeyword && nextAttributeKeyword.trim()) {
        params.attributeKeyword = nextAttributeKeyword.trim()
      }
      const res = await api.get('/inventory/stocks', { params })
      setStocks(Array.isArray(res.data) ? res.data : [])
    } catch (err) {
      showError(err)
    } finally {
      setLoadingStocks(false)
    }
  }

  const refreshLowStocks = async (nextThreshold = threshold) => {
    if (!canView) return
    setLoadingLowStocks(true)
    try {
      const params = {}
      const parsed = toInt(nextThreshold)
      if (parsed && parsed > 0) {
        params.threshold = parsed
      }
      const res = await api.get('/inventory/low-stock', { params })
      setLowStocks(Array.isArray(res.data) ? res.data : [])
    } catch (err) {
      showError(err)
    } finally {
      setLoadingLowStocks(false)
    }
  }

  const refreshOrders = async () => {
    if (!canMutate) return
    setLoadingOrders(true)
    try {
      const res = await api.get('/inventory/orders')
      setOrders(Array.isArray(res.data) ? res.data : [])
    } catch (err) {
      showError(err)
    } finally {
      setLoadingOrders(false)
    }
  }

  const refreshCategories = async () => {
    if (!canView) return
    setLoadingCategories(true)
    try {
      const res = await api.get('/inventory/categories')
      setCategories(Array.isArray(res.data) ? res.data : [])
    } catch (err) {
      showError(err)
    } finally {
      setLoadingCategories(false)
    }
  }

  const refreshLedger = async (skuRaw = ledgerSku) => {
    if (!canView) return
    const sku = String(skuRaw || '').trim()
    if (!sku) {
      setLedger([])
      return
    }

    setLoadingLedger(true)
    try {
      const res = await api.get(`/inventory/ledger/${encodeURIComponent(sku)}`)
      setLedger(Array.isArray(res.data) ? res.data : [])
    } catch (err) {
      showError(err)
    } finally {
      setLoadingLedger(false)
    }
  }

  const refreshReport = async (
    fromDate = reportFromDate,
    toDate = reportToDate,
    categoryId = reportCategoryId,
    attributeKeyword = reportAttributeKeyword,
    lowStockOnly = reportLowStockOnly,
  ) => {
    if (!canView) return
    setLoadingReport(true)
    try {
      const params = {}
      if (fromDate && String(fromDate).trim()) {
        params.fromDate = String(fromDate).trim()
      }
      if (toDate && String(toDate).trim()) {
        params.toDate = String(toDate).trim()
      }
      if (categoryId && String(categoryId).trim()) {
        params.categoryId = Number(categoryId)
      }
      if (attributeKeyword && String(attributeKeyword).trim()) {
        params.attributeKeyword = String(attributeKeyword).trim()
      }
      if (lowStockOnly) {
        params.lowStockOnly = true
      }
      const res = await api.get('/inventory/reports/summary', { params })
      const data = res?.data ?? {}
      setReportRows(Array.isArray(data.rows) ? data.rows : [])
      setReportSummary({
        totalInbound: Number.isFinite(data.totalInbound) ? data.totalInbound : 0,
        totalOutbound: Number.isFinite(data.totalOutbound) ? data.totalOutbound : 0,
        totalNet: Number.isFinite(data.totalNet) ? data.totalNet : 0,
      })
    } catch (err) {
      showError(err)
      setReportRows([])
      setReportSummary({ totalInbound: 0, totalOutbound: 0, totalNet: 0 })
    } finally {
      setLoadingReport(false)
    }
  }

  const exportReportCsv = () => {
    if (!reportRows.length) {
      setError('Khong co du lieu bao cao de xuat')
      return
    }
    const escapeCsv = (value) => {
      const source = value == null ? '' : String(value)
      return `"${source.replaceAll('"', '""')}"`
    }
    const header = [
      'SKU',
      'Ten',
      'Danh muc',
      'Ton dau ky',
      'Nhap trong ky',
      'Xuat trong ky',
      'Net trong ky',
      'Ton cuoi ky',
      'Ton hien tai',
      'Kha dung hien tai',
      'Canh bao',
    ]
    const lines = [header.map(escapeCsv).join(',')]
    for (const row of reportRows) {
      const status = stockStatusOf({
        availableQuantity: row.currentAvailable,
        inventoryQuantity: row.currentInventory,
        lowStock: row.lowStock,
      })
      lines.push([
        row.sku ?? '',
        row.name ?? '',
        row.categoryName ?? '',
        row.openingBalance ?? 0,
        row.inboundQuantity ?? 0,
        row.outboundQuantity ?? 0,
        row.netQuantity ?? 0,
        row.closingBalance ?? 0,
        row.currentInventory ?? 0,
        row.currentAvailable ?? 0,
        status.label,
      ].map(escapeCsv).join(','))
    }

    const blob = new Blob([`\uFEFF${lines.join('\n')}`], { type: 'text/csv;charset=utf-8;' })
    const href = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = href
    a.download = 'bao-cao-kho.csv'
    document.body.appendChild(a)
    a.click()
    document.body.removeChild(a)
    URL.revokeObjectURL(href)
    showSuccess('Da xuat bao cao CSV')
  }

  useEffect(() => {
    refreshStocks('', '', '')
    refreshLowStocks('20')
    refreshOrders()
    refreshCategories()
    refreshReport('', '', '', '', false)
  }, [])

  const onInboundSubmit = async () => {
    const quantity = toInt(inbound.quantity)
    const sku = String(inbound.sku || '').trim()
    if (!sku || !quantity || quantity <= 0) {
      setError('Phiếu nhập cần sku và quantity > 0')
      return
    }

    setSubmitting(true)
    try {
      await api.post('/inventory/inbound', {
        sku,
        quantity,
        referenceCode: inbound.referenceCode || null,
        location: inbound.location || null,
        note: inbound.note || null,
      })
      showSuccess('Da tao phieu nhap va cong ton kho')
      setInbound({ sku: '', quantity: '', referenceCode: '', location: '', note: '' })
      await Promise.all([refreshStocks(), refreshLowStocks(), refreshOrders()])
    } catch (err) {
      showError(err)
    } finally {
      setSubmitting(false)
    }
  }

  const onManualAddSubmit = async () => {
    const categoryId = toInt(manualAdd.categoryId)
    const initialQuantity = manualAdd.initialQuantity ? toInt(manualAdd.initialQuantity) : 0
    const reorderLevel = manualAdd.reorderLevel ? toInt(manualAdd.reorderLevel) : 10
    const price = manualAdd.price ? toNumber(manualAdd.price) : null

    if (!manualAdd.name.trim() || !manualAdd.mpn.trim() || !categoryId || categoryId <= 0) {
      setError('Them thu cong can name, mpn va categoryId')
      return
    }
    if (initialQuantity == null || initialQuantity < 0) {
      setError('initialQuantity phai >= 0')
      return
    }
    if (reorderLevel == null || reorderLevel <= 0) {
      setError('reorderLevel phai > 0')
      return
    }

    setSubmitting(true)
    try {
      await api.post('/inventory/manual-products', {
        name: manualAdd.name.trim(),
        mpn: manualAdd.mpn.trim(),
        categoryId,
        initialQuantity,
        reorderLevel,
        storageLocation: manualAdd.storageLocation || null,
        price,
        description: manualAdd.description || null,
      })
      showSuccess('Da them linh kien thu cong')
      setManualAdd({
        name: '',
        mpn: '',
        categoryId: '',
        initialQuantity: '',
        reorderLevel: '10',
        storageLocation: '',
        price: '',
        description: '',
      })
      await Promise.all([refreshStocks(), refreshLowStocks()])
    } catch (err) {
      showError(err)
    } finally {
      setSubmitting(false)
    }
  }

  const onLocationUpdateSubmit = async () => {
    const productId = toInt(locationUpdate.productId)
    if (!productId || !locationUpdate.storageLocation.trim()) {
      setError('Cap nhat vi tri can productId va storageLocation')
      return
    }

    setSubmitting(true)
    try {
      await api.patch(`/inventory/products/${productId}/location`, {
        storageLocation: locationUpdate.storageLocation.trim(),
        note: locationUpdate.note || null,
      })
      showSuccess('Da cap nhat vi tri luu tru')
      setLocationUpdate({ productId: '', storageLocation: '', note: '' })
      await Promise.all([refreshStocks(), refreshLowStocks()])
    } catch (err) {
      showError(err)
    } finally {
      setSubmitting(false)
    }
  }

  const onFulfillOrder = async (orderId) => {
    if (!orderId) return
    setSubmitting(true)
    try {
      await api.post('/inventory/outbound', { orderId, note: 'Xuat kho don hang' })
      showSuccess(`Don #${orderId} da xuat kho`) 
      await Promise.all([refreshOrders(), refreshStocks(), refreshLowStocks()])
    } catch (err) {
      showError(err)
    } finally {
      setSubmitting(false)
    }
  }

  const onReturnSubmit = async () => {
    const orderId = toInt(returnForm.orderId)
    const quantity = returnForm.quantity ? toInt(returnForm.quantity) : null
    if (!orderId || orderId <= 0) {
      setError('Hoan tra can orderId')
      return
    }
    if (returnForm.quantity && (quantity == null || quantity <= 0)) {
      setError('So luong hoan tra phai > 0')
      return
    }

    setSubmitting(true)
    try {
      await api.post('/inventory/returns', {
        orderId,
        quantity,
        note: returnForm.note || null,
      })
      showSuccess('Da xu ly hoan tra va cong lai ton kho')
      setReturnForm({ orderId: '', quantity: '', note: '' })
      await Promise.all([refreshOrders(), refreshStocks(), refreshLowStocks()])
    } catch (err) {
      showError(err)
    } finally {
      setSubmitting(false)
    }
  }

  const onAdjustSubmit = async () => {
    const productId = toInt(adjustment.productId)
    const deltaQuantity = toInt(adjustment.deltaQuantity)
    if (!productId || productId <= 0 || deltaQuantity == null || deltaQuantity === 0) {
      setError('Dieu chinh can productId va deltaQuantity != 0')
      return
    }

    setSubmitting(true)
    try {
      await api.post('/inventory/adjustments', {
        productId,
        deltaQuantity,
        note: adjustment.note || null,
      })
      showSuccess('Da dieu chinh ton kho thanh cong')
      setAdjustment({ productId: '', deltaQuantity: '', note: '' })
      await Promise.all([refreshStocks(), refreshLowStocks(), refreshLedger()])
    } catch (err) {
      showError(err)
    } finally {
      setSubmitting(false)
    }
  }

  if (!canView && !canMutate) {
    return <Alert severity="warning">Ban khong co quyen truy cap module kho.</Alert>
  }

  return (
    <Stack spacing={2}>
      <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <Typography variant="h5">Van hanh kho</Typography>
        <Chip label={`Mat hang: ${summary.totalItems} | Ton thuc te: ${summary.totalPhysical} | Kha dung: ${summary.totalAvailable}`} color="primary" />
      </Box>

      {error ? <Alert severity="error">{error}</Alert> : null}
      {success ? <Alert severity="success">{success}</Alert> : null}
      {!canMutate ? (
        <Alert severity="info">
          Ban dang o che do chi xem. Nhap/Xuat/Dieu chinh ton kho chi danh cho role Warehouse.
        </Alert>
      ) : null}
      {canMutate && lowStocks.length > 0 ? (
        <Alert severity="warning">
          Bao dong ton kho: {lowStockSummary.outOfStock} het hang, {lowStockSummary.nearOut} sap het (nguong {threshold || '20'}). Hay tao phieu nhap hang som.
        </Alert>
      ) : null}

      <Paper variant="outlined">
        <Tabs value={tab} onChange={(_, value) => setTab(value)} variant="scrollable" scrollButtons="auto">
          {canMutate ? <Tab label="Nhap kho" /> : null}
          {canMutate ? <Tab label="Xuat kho" /> : null}
          <Tab label="Danh muc ton kho" />
          <Tab label="The kho" />
          <Tab label="Bao cao kho" />
        </Tabs>

        <Divider />

        <Box sx={{ p: 2 }}>
          {activeTabKey === 'inbound' ? (
            <Stack spacing={3}>
              <Typography variant="h6">Phieu nhap kho</Typography>
              <Box sx={{ display: 'grid', gap: 2, gridTemplateColumns: { xs: '1fr', md: 'repeat(3, 1fr)' } }}>
                <TextField label="Ma SKU" value={inbound.sku} onChange={(e) => setInbound((v) => ({ ...v, sku: e.target.value }))} />
                <TextField label="So luong" value={inbound.quantity} onChange={(e) => setInbound((v) => ({ ...v, quantity: e.target.value }))} />
                <TextField label="Ma phieu" value={inbound.referenceCode} onChange={(e) => setInbound((v) => ({ ...v, referenceCode: e.target.value }))} />
                <TextField label="Vi tri luu tru" value={inbound.location} onChange={(e) => setInbound((v) => ({ ...v, location: e.target.value }))} />
                <TextField label="Ghi chu" value={inbound.note} onChange={(e) => setInbound((v) => ({ ...v, note: e.target.value }))} sx={{ gridColumn: { xs: 'span 1', md: 'span 2' } }} />
              </Box>
              <Box>
                <Button variant="contained" onClick={onInboundSubmit} disabled={!canMutate || submitting}>
                  Tao phieu nhap
                </Button>
              </Box>

              <Divider />

              <Typography variant="h6">Them linh kien thu cong</Typography>
              <Box sx={{ display: 'grid', gap: 2, gridTemplateColumns: { xs: '1fr', md: 'repeat(4, 1fr)' } }}>
                <TextField label="Ten" value={manualAdd.name} onChange={(e) => setManualAdd((v) => ({ ...v, name: e.target.value }))} />
                <TextField
                  label="Ma linh kien (MPN)"
                  required
                  value={manualAdd.mpn}
                  onChange={(e) => setManualAdd((v) => ({ ...v, mpn: e.target.value }))}
                />
                <TextField
                  label="Ma noi bo (SKU)"
                  value="He thong tu dong tao sau khi luu"
                  disabled
                />
                <FormControl fullWidth>
                  <InputLabel id="manual-category-label">Danh muc</InputLabel>
                  <Select
                    labelId="manual-category-label"
                    label="Danh muc"
                    value={manualAdd.categoryId}
                    onChange={(e) => setManualAdd((v) => ({ ...v, categoryId: e.target.value }))}
                    disabled={loadingCategories}
                  >
                    <MenuItem value="">
                      <em>Chon danh muc</em>
                    </MenuItem>
                    {categories.map((category) => (
                      <MenuItem key={category.id} value={String(category.id)}>
                        {category.parentName ? `${category.parentName} / ${category.name}` : category.name}
                      </MenuItem>
                    ))}
                  </Select>
                </FormControl>
                <TextField label="Ton dau" value={manualAdd.initialQuantity} onChange={(e) => setManualAdd((v) => ({ ...v, initialQuantity: e.target.value }))} />
                <TextField label="Nguong canh bao" value={manualAdd.reorderLevel} onChange={(e) => setManualAdd((v) => ({ ...v, reorderLevel: e.target.value }))} />
                <TextField label="Vi tri" value={manualAdd.storageLocation} onChange={(e) => setManualAdd((v) => ({ ...v, storageLocation: e.target.value }))} />
                <TextField label="Gia" value={manualAdd.price} onChange={(e) => setManualAdd((v) => ({ ...v, price: e.target.value }))} />
                <TextField label="Mo ta" value={manualAdd.description} onChange={(e) => setManualAdd((v) => ({ ...v, description: e.target.value }))} />
              </Box>
              <Box>
                <Button variant="contained" onClick={onManualAddSubmit} disabled={!canMutate || submitting}>
                  Them linh kien
                </Button>
              </Box>

              <Divider />

              <Typography variant="h6">Cap nhat vi tri luu tru</Typography>
              <Box sx={{ display: 'grid', gap: 2, gridTemplateColumns: { xs: '1fr', md: 'repeat(3, 1fr)' } }}>
                <TextField label="Ma san pham" value={locationUpdate.productId} onChange={(e) => setLocationUpdate((v) => ({ ...v, productId: e.target.value }))} />
                <TextField label="Vi tri luu tru" value={locationUpdate.storageLocation} onChange={(e) => setLocationUpdate((v) => ({ ...v, storageLocation: e.target.value }))} />
                <TextField label="Ghi chu" value={locationUpdate.note} onChange={(e) => setLocationUpdate((v) => ({ ...v, note: e.target.value }))} />
              </Box>
              <Box>
                <Button variant="contained" onClick={onLocationUpdateSubmit} disabled={!canMutate || submitting}>
                  Cap nhat vi tri
                </Button>
              </Box>
            </Stack>
          ) : null}

          {activeTabKey === 'outbound' ? (
            <Stack spacing={3}>
              <Typography variant="h6">Xu ly don hang</Typography>
              <Button variant="outlined" onClick={refreshOrders} disabled={loadingOrders}>
                {loadingOrders ? 'Dang tai don hang...' : 'Lam moi don hang'}
              </Button>

              <TableContainer component={Paper} variant="outlined">
                <Table size="small">
                  <TableHead>
                    <TableRow>
                      <TableCell>Ma don</TableCell>
                      <TableCell>Trang thai</TableCell>
                      <TableCell>Nguoi dat</TableCell>
                      <TableCell>San pham</TableCell>
                      <TableCell>SKU</TableCell>
                      <TableCell>So luong</TableCell>
                      <TableCell>Ton kha dung</TableCell>
                      <TableCell align="right">Thao tac</TableCell>
                    </TableRow>
                  </TableHead>
                  <TableBody>
                    {orders.length === 0 ? (
                      <TableRow>
                        <TableCell colSpan={8}>Khong co don can xuat kho</TableCell>
                      </TableRow>
                    ) : orders.map((order) => (
                      <TableRow key={order.orderId} hover>
                        <TableCell>{order.orderId}</TableCell>
                        <TableCell>{order.status}</TableCell>
                        <TableCell>{order.username}</TableCell>
                        <TableCell>{order.productName}</TableCell>
                        <TableCell>{order.sku}</TableCell>
                        <TableCell>{order.orderQuantity}</TableCell>
                        <TableCell>{order.availableQuantity}</TableCell>
                        <TableCell align="right">
                          <Button
                            size="small"
                            variant="contained"
                            onClick={() => onFulfillOrder(order.orderId)}
                            disabled={!canMutate || submitting || order.availableQuantity < order.orderQuantity}
                          >
                              Xuat kho (Ship)
                          </Button>
                        </TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </TableContainer>

              <Divider />

              <Typography variant="h6">Xu ly hoan tra</Typography>
              <Box sx={{ display: 'grid', gap: 2, gridTemplateColumns: { xs: '1fr', md: 'repeat(3, 1fr)' } }}>
                <TextField label="Ma don" value={returnForm.orderId} onChange={(e) => setReturnForm((v) => ({ ...v, orderId: e.target.value }))} />
                <TextField label="So luong hoan (tuy chon)" value={returnForm.quantity} onChange={(e) => setReturnForm((v) => ({ ...v, quantity: e.target.value }))} />
                <TextField label="Ghi chu" value={returnForm.note} onChange={(e) => setReturnForm((v) => ({ ...v, note: e.target.value }))} />
              </Box>
              <Box>
                <Button variant="contained" onClick={onReturnSubmit} disabled={!canMutate || submitting}>
                  Xac nhan hoan tra
                </Button>
              </Box>
            </Stack>
          ) : null}

          {activeTabKey === 'stock' ? (
            <Stack spacing={3}>
              <Typography variant="h6">Danh sach ton kho</Typography>
              <Box sx={{ display: 'grid', gap: 2, gridTemplateColumns: { xs: '1fr', md: '2fr 2fr 2fr 1fr 1fr' } }}>
                <TextField label="Tim theo SKU hoac ten" value={keyword} onChange={(e) => setKeyword(e.target.value)} />
                <TextField
                  label="Tim theo thuoc tinh"
                  value={stockAttributeKeyword}
                  onChange={(e) => setStockAttributeKeyword(e.target.value)}
                />
                <FormControl fullWidth>
                  <InputLabel id="stock-filter-category-label">Danh muc</InputLabel>
                  <Select
                    labelId="stock-filter-category-label"
                    label="Danh muc"
                    value={stockCategoryFilterId}
                    onChange={(e) => setStockCategoryFilterId(e.target.value)}
                  >
                    <MenuItem value="">
                      <em>Tat ca</em>
                    </MenuItem>
                    {categories.map((category) => (
                      <MenuItem key={`stock-filter-${category.id}`} value={String(category.id)}>
                        {category.parentName ? `${category.parentName} / ${category.name}` : category.name}
                      </MenuItem>
                    ))}
                  </Select>
                </FormControl>
                <Button
                  variant="outlined"
                  onClick={() => refreshStocks(keyword, stockCategoryFilterId, stockAttributeKeyword)}
                  disabled={loadingStocks}
                >
                  {loadingStocks ? 'Dang tim...' : 'Tim kiem'}
                </Button>
                <Button
                  variant="outlined"
                  onClick={() => {
                    setKeyword('')
                    setStockCategoryFilterId('')
                    setStockAttributeKeyword('')
                    refreshStocks('', '', '')
                  }}
                >
                  Xoa loc
                </Button>
              </Box>

              <TableContainer component={Paper} variant="outlined">
                <Table size="small" sx={{ minWidth: 1300 }}>
                  <TableHead>
                    <TableRow>
                      <TableCell>STT</TableCell>
                      <TableCell>SKU</TableCell>
                      <TableCell>Ten</TableCell>
                      <TableCell>Ton thuc te</TableCell>
                      <TableCell>Dang giu cho</TableCell>
                      <TableCell>Ton kha dung</TableCell>
                      <TableCell>Nguong nhap</TableCell>
                       <TableCell>Vi tri</TableCell>
                      <TableCell>Danh muc</TableCell>
                      <TableCell>Thuoc tinh</TableCell>
                      <TableCell>Gia</TableCell>
                      <TableCell width={130}>Canh bao</TableCell>
                     </TableRow>
                   </TableHead>
                   <TableBody>
                     {stocks.length === 0 ? (
                       <TableRow>
                         <TableCell colSpan={12}>Khong co du lieu ton kho</TableCell>
                       </TableRow>
                     ) : stocks.map((item, index) => (
                       <TableRow key={item.productId} hover>
                        <TableCell>{index + 1}</TableCell>
                        <TableCell>{item.sku}</TableCell>
                        <TableCell>{item.name}</TableCell>
                        <TableCell>{item.inventoryQuantity}</TableCell>
                        <TableCell>{item.reservedQuantity ?? 0}</TableCell>
                        <TableCell>{item.availableQuantity ?? item.inventoryQuantity ?? 0}</TableCell>
                         <TableCell>{item.reorderLevel}</TableCell>
                         <TableCell>{item.storageLocation || '-'}</TableCell>
                         <TableCell>{item.categoryName || '-'}</TableCell>
                         <TableCell>
                           {(item.attributeValues ?? []).length === 0
                             ? '-'
                             : item.attributeValues.map((attr) => `${attr.name}: ${attr.displayValue || '-'}`).join(' | ')}
                         </TableCell>
                          <TableCell>{item.price ?? '-'}</TableCell>
                          <TableCell>
                            {(() => {
                              const status = stockStatusOf(item)
                              return (
                                <Chip
                                  size="small"
                                  color={status.color}
                                  label={status.label}
                                  sx={{ minWidth: 90, justifyContent: 'center' }}
                                />
                              )
                            })()}
                          </TableCell>
                        </TableRow>
                      ))}
                  </TableBody>
                </Table>
              </TableContainer>

              <Divider />

              <Typography variant="h6">Canh bao ton thap / het hang</Typography>
              <Box sx={{ display: 'grid', gap: 2, gridTemplateColumns: { xs: '1fr', md: '2fr 1fr 1fr' } }}>
                <TextField label="Nguong canh bao" value={threshold} onChange={(e) => setThreshold(e.target.value)} />
                <Button variant="outlined" onClick={() => refreshLowStocks(threshold)} disabled={loadingLowStocks}>
                  {loadingLowStocks ? 'Dang tai...' : 'Lam moi canh bao'}
                </Button>
              </Box>

              <TableContainer component={Paper} variant="outlined">
                <Table size="small">
                  <TableHead>
                    <TableRow>
                      <TableCell>STT</TableCell>
                      <TableCell>SKU</TableCell>
                      <TableCell>Ten</TableCell>
                      <TableCell>Ton kha dung</TableCell>
                        <TableCell>Nguong nhap</TableCell>
                        <TableCell>Trang thai</TableCell>
                      </TableRow>
                    </TableHead>
                    <TableBody>
                      {lowStocks.length === 0 ? (
                        <TableRow>
                          <TableCell colSpan={6}>Khong co mat hang ton thap</TableCell>
                        </TableRow>
                      ) : lowStocks.map((item, index) => (
                        <TableRow key={`low-${item.productId}`} hover>
                          <TableCell>{index + 1}</TableCell>
                          <TableCell>{item.sku}</TableCell>
                          <TableCell>{item.name}</TableCell>
                          <TableCell>{item.availableQuantity ?? item.inventoryQuantity ?? 0}</TableCell>
                          <TableCell>{item.reorderLevel}</TableCell>
                          <TableCell>
                            {(() => {
                              const status = stockStatusOf(item)
                              return (
                                <Chip
                                  size="small"
                                  color={status.color}
                                  label={status.label}
                                  sx={{ minWidth: 90, justifyContent: 'center' }}
                                />
                              )
                            })()}
                          </TableCell>
                        </TableRow>
                      ))}
                    </TableBody>
                </Table>
              </TableContainer>

              {canMutate ? (
                <>
                  <Divider />

                  <Typography variant="h6">Dieu chinh ton kho</Typography>
                  <Box sx={{ display: 'grid', gap: 2, gridTemplateColumns: { xs: '1fr', md: 'repeat(3, 1fr)' } }}>
                    <TextField label="Ma san pham" value={adjustment.productId} onChange={(e) => setAdjustment((v) => ({ ...v, productId: e.target.value }))} />
                    <TextField label="Luong dieu chinh (+/-)" value={adjustment.deltaQuantity} onChange={(e) => setAdjustment((v) => ({ ...v, deltaQuantity: e.target.value }))} />
                    <TextField label="Ly do / Ghi chu" value={adjustment.note} onChange={(e) => setAdjustment((v) => ({ ...v, note: e.target.value }))} />
                  </Box>
                  <Box>
                    <Button variant="contained" onClick={onAdjustSubmit} disabled={!canMutate || submitting}>
                      Ap dung dieu chinh
                    </Button>
                  </Box>
                </>
              ) : null}
            </Stack>
          ) : null}

          {activeTabKey === 'ledger' ? (
            <Stack spacing={3}>
              <Typography variant="h6">The kho</Typography>
              <Box sx={{ display: 'grid', gap: 2, gridTemplateColumns: { xs: '1fr', md: '2fr 1fr' } }}>
                <TextField label="Ma SKU" value={ledgerSku} onChange={(e) => setLedgerSku(e.target.value)} />
                <Button variant="outlined" onClick={() => refreshLedger(ledgerSku)} disabled={loadingLedger}>
                  {loadingLedger ? 'Dang tai...' : 'Tai the kho'}
                </Button>
              </Box>

              <TableContainer component={Paper} variant="outlined">
                <Table size="small">
                  <TableHead>
                    <TableRow>
                      <TableCell>Thoi gian</TableCell>
                      <TableCell>Loai</TableCell>
                      <TableCell>Bien dong</TableCell>
                      <TableCell>So du</TableCell>
                      <TableCell>Tham chieu</TableCell>
                      <TableCell>Ghi chu</TableCell>
                      <TableCell>Nguoi tao</TableCell>
                    </TableRow>
                  </TableHead>
                  <TableBody>
                    {ledger.length === 0 ? (
                      <TableRow>
                        <TableCell colSpan={7}>Khong co du lieu the kho</TableCell>
                      </TableRow>
                    ) : ledger.map((row) => (
                      <TableRow key={row.transactionId} hover>
                        <TableCell>{row.createdAt}</TableCell>
                        <TableCell>{row.transactionType}</TableCell>
                        <TableCell>{row.quantityChange}</TableCell>
                        <TableCell>{row.balanceAfter}</TableCell>
                        <TableCell>{row.referenceCode || '-'}</TableCell>
                        <TableCell>{row.note || '-'}</TableCell>
                        <TableCell>{row.createdBy || '-'}</TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </TableContainer>
            </Stack>
          ) : null}

          {activeTabKey === 'report' ? (
            <Stack spacing={3}>
              <Typography variant="h6">Bao cao Nhap - Xuat - Ton</Typography>
              <Box sx={{ display: 'grid', gap: 2, gridTemplateColumns: { xs: '1fr', md: 'repeat(6, 1fr)' } }}>
                <TextField
                  label="Tu ngay"
                  type="date"
                  InputLabelProps={{ shrink: true }}
                  value={reportFromDate}
                  onChange={(e) => setReportFromDate(e.target.value)}
                />
                <TextField
                  label="Den ngay"
                  type="date"
                  InputLabelProps={{ shrink: true }}
                  value={reportToDate}
                  onChange={(e) => setReportToDate(e.target.value)}
                />
                <FormControl fullWidth>
                  <InputLabel id="report-category-label">Danh muc</InputLabel>
                  <Select
                    labelId="report-category-label"
                    label="Danh muc"
                    value={reportCategoryId}
                    onChange={(e) => setReportCategoryId(e.target.value)}
                  >
                    <MenuItem value="">
                      <em>Tat ca</em>
                    </MenuItem>
                    {categories.map((category) => (
                      <MenuItem key={`report-category-${category.id}`} value={String(category.id)}>
                        {category.parentName ? `${category.parentName} / ${category.name}` : category.name}
                      </MenuItem>
                    ))}
                  </Select>
                </FormControl>
                <TextField
                  label="Tu khoa thuoc tinh"
                  value={reportAttributeKeyword}
                  onChange={(e) => setReportAttributeKeyword(e.target.value)}
                />
                <FormControlLabel
                  control={
                    <Switch
                      checked={reportLowStockOnly}
                      onChange={(e) => setReportLowStockOnly(e.target.checked)}
                    />
                  }
                  label="Chi low-stock"
                />
                <Box sx={{ display: 'flex', gap: 1 }}>
                  <Button
                    variant="outlined"
                    onClick={() => refreshReport(reportFromDate, reportToDate, reportCategoryId, reportAttributeKeyword, reportLowStockOnly)}
                    disabled={loadingReport}
                  >
                    {loadingReport ? 'Dang tao...' : 'Tao bao cao'}
                  </Button>
                </Box>
              </Box>

              <Box sx={{ display: 'flex', gap: 1, flexWrap: 'wrap' }}>
                <Chip color="primary" label={`Tong nhap: ${reportSummary.totalInbound ?? 0}`} />
                <Chip color="warning" label={`Tong xuat: ${reportSummary.totalOutbound ?? 0}`} />
                <Chip color={(reportSummary.totalNet ?? 0) >= 0 ? 'success' : 'error'} label={`Net: ${reportSummary.totalNet ?? 0}`} />
                <Chip label={`SKU: ${reportRows.length}`} />
                <Button variant="contained" onClick={exportReportCsv} disabled={!reportRows.length}>
                  Xuat CSV
                </Button>
              </Box>

              <TableContainer component={Paper} variant="outlined">
                <Table size="small" sx={{ minWidth: 1400 }}>
                  <TableHead>
                    <TableRow>
                      <TableCell>STT</TableCell>
                      <TableCell>SKU</TableCell>
                      <TableCell>Ten</TableCell>
                      <TableCell>Danh muc</TableCell>
                      <TableCell>Ton dau ky</TableCell>
                      <TableCell>Nhap ky</TableCell>
                      <TableCell>Xuat ky</TableCell>
                      <TableCell>Net ky</TableCell>
                      <TableCell>Ton cuoi ky</TableCell>
                      <TableCell>Ton hien tai</TableCell>
                      <TableCell>Kha dung hien tai</TableCell>
                      <TableCell width={130}>Canh bao</TableCell>
                    </TableRow>
                  </TableHead>
                  <TableBody>
                    {reportRows.length === 0 ? (
                      <TableRow>
                        <TableCell colSpan={12}>Khong co du lieu bao cao</TableCell>
                      </TableRow>
                    ) : reportRows.map((row, index) => (
                      <TableRow key={`report-${row.productId}`} hover>
                        <TableCell>{index + 1}</TableCell>
                        <TableCell>{row.sku || '-'}</TableCell>
                        <TableCell>{row.name || '-'}</TableCell>
                        <TableCell>{row.categoryName || '-'}</TableCell>
                        <TableCell>{row.openingBalance ?? 0}</TableCell>
                        <TableCell>{row.inboundQuantity ?? 0}</TableCell>
                        <TableCell>{row.outboundQuantity ?? 0}</TableCell>
                        <TableCell>{row.netQuantity ?? 0}</TableCell>
                        <TableCell>{row.closingBalance ?? 0}</TableCell>
                        <TableCell>{row.currentInventory ?? 0}</TableCell>
                        <TableCell>{row.currentAvailable ?? 0}</TableCell>
                        <TableCell>
                          {(() => {
                            const status = stockStatusOf({
                              availableQuantity: row.currentAvailable,
                              inventoryQuantity: row.currentInventory,
                              lowStock: row.lowStock,
                            })
                            return (
                              <Chip
                                size="small"
                                color={status.color}
                                label={status.label}
                                sx={{ minWidth: 90, justifyContent: 'center' }}
                              />
                            )
                          })()}
                        </TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </TableContainer>
            </Stack>
          ) : null}
        </Box>
      </Paper>
    </Stack>
  )
}
