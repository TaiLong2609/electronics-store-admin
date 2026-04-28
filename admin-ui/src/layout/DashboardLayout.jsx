import { useEffect, useMemo, useState } from 'react';
import {
  Avatar,
  AppBar,
  Box,
  Button,
  Badge,
  Collapse,
  Container,
  Drawer,
  IconButton,
  List,
  ListItemIcon,
  ListItemButton,
  ListItemText,
  Menu,
  MenuItem,
  Divider,
  InputBase,
  Toolbar,
  Typography,
} from '@mui/material';
import { alpha } from '@mui/material/styles';
import { Outlet, useLocation, useNavigate } from 'react-router-dom';
import {
  AdminPanelSettings as AdminPanelSettingsIcon,
  Badge as BadgeIcon,
  Campaign as CampaignIcon,
  Category as CategoryIcon,
  CloudDownload as CloudDownloadIcon,
  Dashboard as DashboardIcon,
  ExpandLess as ExpandLessIcon,
  ExpandMore as ExpandMoreIcon,
  Inventory2 as ProductsIcon,
  LocalOffer as LocalOfferIcon,
  Menu as MenuIcon,
  Notifications as NotificationsIcon,
  Paid as PaidIcon,
  People as PeopleIcon,
  ReceiptLong as ReceiptLongIcon,
  Reviews as ReviewsIcon,
  Search as SearchIcon,
  Settings as SettingsIcon,
  SettingsApplications as SettingsApplicationsIcon,
  Storefront as StorefrontIcon,
  Warehouse as WarehouseIcon,
} from '@mui/icons-material';
import { api } from '../api/http.js';
import { clearAuth, getPermissions, getRoles, getUsername } from '../auth/auth.js';

const drawerWidthOpen = 260;
const drawerWidthClosed = 72;

export default function DashboardLayout() {
  const navigate = useNavigate();
  const location = useLocation();
  const username = getUsername();
  const roles = getRoles();
  const permissions = getPermissions();
  const isSuperAdmin = Array.isArray(roles) && roles.includes('SUPER_ADMIN');

  const hasPermission = (p) => permissions?.includes(p);
  const hasAnyPermission = (items) => (Array.isArray(items) ? items.some(hasPermission) : false);

  const showOrders = hasAnyPermission(['ORDER_VIEW_SELF', 'ORDER_VIEW_ALL']);
  const showTransactions = hasPermission('STATS_VIEW');

  const showProducts = hasPermission('PRODUCT_VIEW');
  const showWarehouseTools = hasAnyPermission([
    'PRODUCT_CREATE',
    'PRODUCT_UPDATE',
    'PRODUCT_DELETE',
  ]);
  const canViewInventory = hasPermission('PRODUCT_VIEW');
  const canReceiveLowStockAlert = Array.isArray(roles)
    && (roles.includes('SUPER_ADMIN') || roles.includes('WAREHOUSE'))
    && canViewInventory;
  const showMarketing = hasPermission('MARKETING_MANAGE');
  const showCategories = showWarehouseTools;
  const showInventory = showWarehouseTools || canViewInventory;

  const showCustomers = hasPermission('ORDER_VIEW_ALL');
  const showReviews = hasPermission('ORDER_VIEW_ALL');

  const showVouchers = showMarketing;

  const showSystem = hasPermission('USER_MANAGE');
  const showStaffs = showSystem;
  const showRolesPermissions = showSystem;
  const showSettings = showSystem;
  const showDataSync = isSuperAdmin;

  const showStoreGroup = showOrders || showTransactions;
  const showWarehouseGroup = showCategories || showProducts || showInventory;
  const showCustomersGroup = showCustomers || showReviews;
  const showMarketingGroup = showVouchers;
  const showSystemGroup = showStaffs || showRolesPermissions || showSettings;

  const [drawerOpen, setDrawerOpen] = useState(true);

  const isStorePath =
    location.pathname.startsWith('/orders') ||
    location.pathname.startsWith('/transactions');
  const isWarehousePath =
    location.pathname.startsWith('/categories') ||
    location.pathname.startsWith('/products') ||
    location.pathname.startsWith('/inventory');
  const isCustomersPath =
    location.pathname.startsWith('/customers') ||
    location.pathname.startsWith('/reviews');
  const isMarketingPath = location.pathname.startsWith('/vouchers');
  const isSystemPath =
    location.pathname.startsWith('/staffs') ||
    location.pathname.startsWith('/roles-permissions') ||
    location.pathname.startsWith('/settings') ||
    location.pathname.startsWith('/admin-data-sync');

  const [storeOpen, setStoreOpen] = useState(isStorePath);
  const [warehouseOpen, setWarehouseOpen] = useState(isWarehousePath);
  const [customersOpen, setCustomersOpen] = useState(isCustomersPath);
  const [marketingOpen, setMarketingOpen] = useState(isMarketingPath);
  const [systemOpen, setSystemOpen] = useState(isSystemPath);

  const storeExpanded = storeOpen || isStorePath;
  const warehouseExpanded = warehouseOpen || isWarehousePath;
  const customersExpanded = customersOpen || isCustomersPath;
  const marketingExpanded = marketingOpen || isMarketingPath;
  const systemExpanded = systemOpen || isSystemPath;

  const [searchText, setSearchText] = useState('');

  const [notifAnchorEl, setNotifAnchorEl] = useState(null);
  const notifOpen = Boolean(notifAnchorEl);

  const [profileAnchorEl, setProfileAnchorEl] = useState(null);
  const profileOpen = Boolean(profileAnchorEl);
  const [lowStockAlertSummary, setLowStockAlertSummary] = useState({
    total: 0,
    outOfStock: 0,
    nearOut: 0,
  });

  useEffect(() => {
    if (!canReceiveLowStockAlert) {
      setLowStockAlertSummary({ total: 0, outOfStock: 0, nearOut: 0 });
      return;
    }

    let cancelled = false;
    const fetchLowStock = async () => {
      try {
        const response = await api.get('/inventory/low-stock');
        if (cancelled) {
          return;
        }
        const items = Array.isArray(response.data) ? response.data : [];
        const outOfStock = items.filter(
          (item) => Number(item?.availableQuantity ?? item?.inventoryQuantity ?? 0) <= 0
        ).length;
        const total = items.length;
        setLowStockAlertSummary({
          total,
          outOfStock,
          nearOut: Math.max(0, total - outOfStock),
        });
      } catch {
        if (!cancelled) {
          setLowStockAlertSummary({ total: 0, outOfStock: 0, nearOut: 0 });
        }
      }
    };

    fetchLowStock();
    const intervalId = window.setInterval(fetchLowStock, 30000);
    return () => {
      cancelled = true;
      window.clearInterval(intervalId);
    };
  }, [canReceiveLowStockAlert]);

  const notifications = useMemo(() => {
    const list = [];
    if (canReceiveLowStockAlert && lowStockAlertSummary.total > 0) {
      list.push({
        id: 'inventory',
        label: `Canh bao ton kho: ${lowStockAlertSummary.outOfStock} het hang, ${lowStockAlertSummary.nearOut} sap het`,
        to: '/inventory',
        count: lowStockAlertSummary.total,
      });
    }
    return list;
  }, [canReceiveLowStockAlert, lowStockAlertSummary]);

  const notificationBadgeCount = useMemo(
    () => notifications.reduce((sum, item) => sum + (item.count ?? 1), 0),
    [notifications]
  );

  const onLogout = () => {
    clearAuth();
    navigate('/login', { replace: true });
  };

  const drawerWidth = drawerOpen ? drawerWidthOpen : drawerWidthClosed;
  const roleLabel = roles?.length ? roles[0] : '';

  const go = (to) => {
    navigate(to);
  };

  return (
    <Box sx={{ display: 'flex', minHeight: '100%', bgcolor: 'background.default' }}>
      <AppBar
        position="fixed"
        sx={{ zIndex: (theme) => theme.zIndex.drawer + 1 }}
      >
        <Toolbar>
          <IconButton
            color="inherit"
            edge="start"
            onClick={() => setDrawerOpen((v) => !v)}
            sx={{ mr: 1 }}
            aria-label="Toggle sidebar"
          >
            <MenuIcon />
          </IconButton>

          <Typography variant="h6" sx={{ flexGrow: 1 }}>
            Ecomerce Admin
          </Typography>

          <Box
            sx={(theme) => ({
              display: { xs: 'none', sm: 'flex' },
              alignItems: 'center',
              gap: 1,
              mr: 1,
              px: 1,
              py: 0.25,
              borderRadius: 1,
              backgroundColor: alpha(theme.palette.common.white, 0.15),
              '&:hover': { backgroundColor: alpha(theme.palette.common.white, 0.22) },
              minWidth: 320,
            })}
          >
            <SearchIcon fontSize="small" />
            <InputBase
              placeholder="Tìm mã đơn / SĐT…"
              value={searchText}
              onChange={(e) => setSearchText(e.target.value)}
              sx={{ color: 'inherit', width: '100%' }}
              inputProps={{ 'aria-label': 'Global search' }}
            />
          </Box>

          <IconButton
            color="inherit"
            onClick={(e) => setNotifAnchorEl(e.currentTarget)}
            aria-label="Notifications"
          >
            <Badge badgeContent={notificationBadgeCount} color="error">
              <NotificationsIcon />
            </Badge>
          </IconButton>

          <IconButton
            color="inherit"
            onClick={(e) => setProfileAnchorEl(e.currentTarget)}
            aria-label="User menu"
            sx={{ ml: 0.5 }}
          >
            <Avatar sx={{ width: 28, height: 28 }}>
              {(username || 'U').slice(0, 1).toUpperCase()}
            </Avatar>
          </IconButton>

          {username ? (
            <Box sx={{ ml: 1, display: { xs: 'none', md: 'block' } }}>
              <Typography variant="body2" sx={{ lineHeight: 1.1 }}>
                {username}
              </Typography>
              {roleLabel ? (
                <Typography variant="caption" sx={{ opacity: 0.85 }}>
                  {roleLabel}
                </Typography>
              ) : null}
            </Box>
          ) : null}
        </Toolbar>
      </AppBar>

      <Drawer
        variant="permanent"
        sx={{
          width: drawerWidth,
          flexShrink: 0,
          '& .MuiDrawer-paper': {
            width: drawerWidth,
            boxSizing: 'border-box',
            overflowX: 'hidden',
            transition: (theme) =>
              theme.transitions.create('width', {
                easing: theme.transitions.easing.sharp,
                duration: theme.transitions.duration.shortest,
              }),
          },
        }}
      >
        <Toolbar />

        <List dense>
          <ListItemButton
            selected={location.pathname.startsWith('/dashboard')}
            onClick={() => go('/dashboard')}
          >
            <ListItemIcon sx={{ minWidth: 40 }}>
              <DashboardIcon />
            </ListItemIcon>
            {drawerOpen ? <ListItemText primary="Tổng quan" /> : null}
          </ListItemButton>

          {showDataSync ? (
            <ListItemButton
              selected={location.pathname.startsWith('/admin-data-sync')}
              onClick={() => go('/admin-data-sync')}
            >
              <ListItemIcon sx={{ minWidth: 40 }}>
                <CloudDownloadIcon />
              </ListItemIcon>
              {drawerOpen ? <ListItemText primary="Đồng bộ dữ liệu" /> : null}
            </ListItemButton>
          ) : null}

          {showStoreGroup ? (
            <>
              <ListItemButton onClick={() => setStoreOpen((v) => !v)}>
                <ListItemIcon sx={{ minWidth: 40 }}>
                  <StorefrontIcon />
                </ListItemIcon>
                {drawerOpen ? <ListItemText primary="Quản lý Cửa hàng" /> : null}
                {drawerOpen ? (storeExpanded ? <ExpandLessIcon /> : <ExpandMoreIcon />) : null}
              </ListItemButton>
              <Collapse in={storeExpanded} timeout="auto" unmountOnExit>
                <List component="div" disablePadding dense>
                  {showOrders ? (
                    <ListItemButton
                      sx={{ pl: drawerOpen ? 4 : 2 }}
                      selected={location.pathname.startsWith('/orders')}
                      onClick={() => go('/orders')}
                    >
                      <ListItemIcon sx={{ minWidth: 40 }}>
                        <ReceiptLongIcon fontSize="small" />
                      </ListItemIcon>
                      {drawerOpen ? <ListItemText primary="Đơn hàng" /> : null}
                    </ListItemButton>
                  ) : null}
                  {showTransactions ? (
                    <ListItemButton
                      sx={{ pl: drawerOpen ? 4 : 2 }}
                      selected={location.pathname.startsWith('/transactions')}
                      onClick={() => go('/transactions')}
                    >
                      <ListItemIcon sx={{ minWidth: 40 }}>
                        <PaidIcon fontSize="small" />
                      </ListItemIcon>
                      {drawerOpen ? <ListItemText primary="Giao dịch" /> : null}
                    </ListItemButton>
                  ) : null}
                </List>
              </Collapse>
            </>
          ) : null}

          {showWarehouseGroup ? (
            <>
              <ListItemButton onClick={() => setWarehouseOpen((v) => !v)}>
                <ListItemIcon sx={{ minWidth: 40 }}>
                  <WarehouseIcon />
                </ListItemIcon>
                {drawerOpen ? <ListItemText primary="Kho & Sản phẩm" /> : null}
                {drawerOpen ? (warehouseExpanded ? <ExpandLessIcon /> : <ExpandMoreIcon />) : null}
              </ListItemButton>
              <Collapse in={warehouseExpanded} timeout="auto" unmountOnExit>
                <List component="div" disablePadding dense>
                  {showCategories ? (
                    <ListItemButton
                      sx={{ pl: drawerOpen ? 4 : 2 }}
                      selected={location.pathname.startsWith('/categories')}
                      onClick={() => go('/categories')}
                    >
                      <ListItemIcon sx={{ minWidth: 40 }}>
                        <CategoryIcon fontSize="small" />
                      </ListItemIcon>
                      {drawerOpen ? <ListItemText primary="Danh mục" /> : null}
                    </ListItemButton>
                  ) : null}
                  {showProducts ? (
                    <ListItemButton
                      sx={{ pl: drawerOpen ? 4 : 2 }}
                      selected={location.pathname.startsWith('/products')}
                      onClick={() => go('/products')}
                    >
                      <ListItemIcon sx={{ minWidth: 40 }}>
                        <ProductsIcon fontSize="small" />
                      </ListItemIcon>
                      {drawerOpen ? <ListItemText primary="Sản phẩm" /> : null}
                    </ListItemButton>
                  ) : null}
                  {showInventory ? (
                    <ListItemButton
                      sx={{ pl: drawerOpen ? 4 : 2 }}
                      selected={location.pathname.startsWith('/inventory')}
                      onClick={() => go('/inventory')}
                    >
                      <ListItemIcon sx={{ minWidth: 40 }}>
                        <WarehouseIcon fontSize="small" />
                      </ListItemIcon>
                      {drawerOpen ? <ListItemText primary="Tồn kho" /> : null}
                    </ListItemButton>
                  ) : null}
                </List>
              </Collapse>
            </>
          ) : null}

          {showCustomersGroup ? (
            <>
              <ListItemButton onClick={() => setCustomersOpen((v) => !v)}>
                <ListItemIcon sx={{ minWidth: 40 }}>
                  <PeopleIcon />
                </ListItemIcon>
                {drawerOpen ? <ListItemText primary="Khách hàng" /> : null}
                {drawerOpen ? (customersExpanded ? <ExpandLessIcon /> : <ExpandMoreIcon />) : null}
              </ListItemButton>
              <Collapse in={customersExpanded} timeout="auto" unmountOnExit>
                <List component="div" disablePadding dense>
                  {showCustomers ? (
                    <ListItemButton
                      sx={{ pl: drawerOpen ? 4 : 2 }}
                      selected={location.pathname.startsWith('/customers')}
                      onClick={() => go('/customers')}
                    >
                      <ListItemIcon sx={{ minWidth: 40 }}>
                        <PeopleIcon fontSize="small" />
                      </ListItemIcon>
                      {drawerOpen ? <ListItemText primary="Danh sách" /> : null}
                    </ListItemButton>
                  ) : null}
                  {showReviews ? (
                    <ListItemButton
                      sx={{ pl: drawerOpen ? 4 : 2 }}
                      selected={location.pathname.startsWith('/reviews')}
                      onClick={() => go('/reviews')}
                    >
                      <ListItemIcon sx={{ minWidth: 40 }}>
                        <ReviewsIcon fontSize="small" />
                      </ListItemIcon>
                      {drawerOpen ? <ListItemText primary="Đánh giá / Bình luận" /> : null}
                    </ListItemButton>
                  ) : null}
                </List>
              </Collapse>
            </>
          ) : null}

          {showMarketingGroup ? (
            <>
              <ListItemButton onClick={() => setMarketingOpen((v) => !v)}>
                <ListItemIcon sx={{ minWidth: 40 }}>
                  <CampaignIcon />
                </ListItemIcon>
                {drawerOpen ? <ListItemText primary="Marketing" /> : null}
                {drawerOpen ? (marketingExpanded ? <ExpandLessIcon /> : <ExpandMoreIcon />) : null}
              </ListItemButton>
              <Collapse in={marketingExpanded} timeout="auto" unmountOnExit>
                <List component="div" disablePadding dense>
                  {showVouchers ? (
                    <ListItemButton
                      sx={{ pl: drawerOpen ? 4 : 2 }}
                      selected={location.pathname.startsWith('/vouchers')}
                      onClick={() => go('/vouchers')}
                    >
                      <ListItemIcon sx={{ minWidth: 40 }}>
                        <LocalOfferIcon fontSize="small" />
                      </ListItemIcon>
                      {drawerOpen ? <ListItemText primary="Mã giảm giá" /> : null}
                    </ListItemButton>
                  ) : null}
                </List>
              </Collapse>
            </>
          ) : null}

          {showSystemGroup ? (
            <>
              <ListItemButton onClick={() => setSystemOpen((v) => !v)}>
                <ListItemIcon sx={{ minWidth: 40 }}>
                  <SettingsApplicationsIcon />
                </ListItemIcon>
                {drawerOpen ? <ListItemText primary="Hệ thống & Phân quyền" /> : null}
                {drawerOpen ? (systemExpanded ? <ExpandLessIcon /> : <ExpandMoreIcon />) : null}
              </ListItemButton>
              <Collapse in={systemExpanded} timeout="auto" unmountOnExit>
                <List component="div" disablePadding dense>
                  {showStaffs ? (
                    <ListItemButton
                      sx={{ pl: drawerOpen ? 4 : 2 }}
                      selected={location.pathname.startsWith('/staffs')}
                      onClick={() => go('/staffs')}
                    >
                      <ListItemIcon sx={{ minWidth: 40 }}>
                        <BadgeIcon fontSize="small" />
                      </ListItemIcon>
                      {drawerOpen ? <ListItemText primary="Tài khoản nội bộ" /> : null}
                    </ListItemButton>
                  ) : null}
                  {showRolesPermissions ? (
                    <ListItemButton
                      sx={{ pl: drawerOpen ? 4 : 2 }}
                      selected={location.pathname.startsWith('/roles-permissions')}
                      onClick={() => go('/roles-permissions')}
                    >
                      <ListItemIcon sx={{ minWidth: 40 }}>
                        <AdminPanelSettingsIcon fontSize="small" />
                      </ListItemIcon>
                      {drawerOpen ? <ListItemText primary="Phân quyền" /> : null}
                    </ListItemButton>
                  ) : null}
                  {showSettings ? (
                    <ListItemButton
                      sx={{ pl: drawerOpen ? 4 : 2 }}
                      selected={location.pathname.startsWith('/settings')}
                      onClick={() => go('/settings')}
                    >
                      <ListItemIcon sx={{ minWidth: 40 }}>
                        <SettingsIcon fontSize="small" />
                      </ListItemIcon>
                      {drawerOpen ? <ListItemText primary="Cấu hình" /> : null}
                    </ListItemButton>
                  ) : null}
                </List>
              </Collapse>
            </>
          ) : null}
        </List>
      </Drawer>

      <Box component="main" sx={{ flexGrow: 1 }}>
        <Toolbar />
        <Container sx={{ py: 3 }} maxWidth="lg">
          <Outlet />
        </Container>
      </Box>

      <Menu
        anchorEl={notifAnchorEl}
        open={notifOpen}
        onClose={() => setNotifAnchorEl(null)}
        anchorOrigin={{ vertical: 'bottom', horizontal: 'right' }}
        transformOrigin={{ vertical: 'top', horizontal: 'right' }}
      >
        <Typography variant="subtitle2" sx={{ px: 2, pt: 1.5, pb: 1 }}>
          Thông báo
        </Typography>
        <Divider />
        {notifications.length === 0 ? (
          <MenuItem disabled>Khong co canh bao moi</MenuItem>
        ) : (
          notifications.map((n) => (
            <MenuItem
              key={n.id}
              onClick={() => {
                setNotifAnchorEl(null);
                go(n.to);
              }}
            >
              {n.label}
            </MenuItem>
          ))
        )}
      </Menu>

      <Menu
        anchorEl={profileAnchorEl}
        open={profileOpen}
        onClose={() => setProfileAnchorEl(null)}
        anchorOrigin={{ vertical: 'bottom', horizontal: 'right' }}
        transformOrigin={{ vertical: 'top', horizontal: 'right' }}
      >
        <MenuItem
          onClick={() => {
            setProfileAnchorEl(null);
            go('/profile');
          }}
        >
          Thông tin cá nhân
        </MenuItem>
        <MenuItem
          onClick={() => {
            setProfileAnchorEl(null);
            go('/change-password');
          }}
        >
          Đổi mật khẩu
        </MenuItem>
        <Divider />
        <MenuItem
          onClick={() => {
            setProfileAnchorEl(null);
            onLogout();
          }}
        >
          Đăng xuất
        </MenuItem>
      </Menu>
    </Box>
  );
}
