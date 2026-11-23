import LogoutIcon from '@mui/icons-material/Logout';
import ShoppingCartIcon from '@mui/icons-material/ShoppingCart';
import {
  AppBar, Avatar, Badge, Box, Button, Chip, Container, IconButton, ListItemIcon, Menu, MenuItem, Toolbar, Typography,
} from '@mui/material';
import { useState } from 'react';
import { Link as RouterLink, NavLink, Outlet } from 'react-router-dom';
import { useAuth } from '../auth/AuthProvider';
import type { Role } from '../auth/roles';
import { useCart } from '../cart/CartContext';

interface NavItem {
  label: string;
  to: string;
  end?: boolean; // exact match only (a parent link such as /admin would otherwise stay active on every admin page)
}

const NAV: Record<Role, NavItem[]> = {
  CUSTOMER: [
    { label: 'Catalog', to: '/catalog' },
    { label: 'My orders', to: '/orders' },
  ],
  DEALER: [
    { label: 'Orders to review', to: '/dealer/orders' },
    { label: 'Catalog', to: '/catalog' },
  ],
  ADMIN: [
    { label: 'Dashboard', to: '/admin', end: true },
    { label: 'Orders', to: '/admin/orders' },
    { label: 'Payments', to: '/admin/payments' },
    { label: 'Emails', to: '/admin/notifications' },
    { label: 'Customers', to: '/admin/customers' },
    { label: 'Dealers', to: '/admin/dealers' },
    { label: 'Users', to: '/admin/users' },
    { label: 'Products', to: '/admin/products' },
    { label: 'Categories', to: '/admin/categories' },
    { label: 'Catalog', to: '/catalog' },
  ],
};

export function AppLayout() {
  const { user, logout } = useAuth();
  const cart = useCart();
  const [anchor, setAnchor] = useState<HTMLElement | null>(null);

  return (
    <Box sx={{ minHeight: '100vh', bgcolor: 'background.default' }}>
      <AppBar position="sticky" color="primary">
        <Toolbar sx={{ gap: 1 }}>
          <Typography variant="h6" component="div"  sx={{ mr: 3, fontWeight: 700 }}>
            Order Place
          </Typography>
          <Box component="nav" sx={{ display: 'flex', gap: 0.5, flexGrow: 1, overflowX: 'auto' }}>
            {NAV[user.role].map((item) => (
              <Button
                key={item.to}
                component={NavLink}
                to={item.to}
                end={item.end}
                color="inherit"
                sx={{ '&.active': { bgcolor: 'rgba(65, 51, 51, 0.18)' } }}
              >
                {item.label}
              </Button>
            ))}
          </Box>
          {user.role === 'CUSTOMER' && (
            <IconButton component={RouterLink} to="/cart" color="inherit" aria-label={`Cart, ${cart.lineCount} items`}>
              <Badge badgeContent={cart.lineCount} color="secondary" max={99}><ShoppingCartIcon /></Badge>
            </IconButton>
          )}
          <Chip size="small" label={user.role} sx={{ bgcolor: 'rgba(255,255,255,0.2)', color: 'inherit' }} />
          <IconButton onClick={(e) => setAnchor(e.currentTarget)} aria-label="Account menu" size="small">
            <Avatar sx={{ width: 32, height: 32, bgcolor: 'secondary.main' }}>{user.name.charAt(0).toUpperCase()}</Avatar>
          </IconButton>
          <Menu anchorEl={anchor} open={anchor !== null} onClose={() => setAnchor(null)}>
            <Box sx={{ px: 2, py: 1 }}>
              <Typography variant="subtitle2">{user.name}</Typography>
              {user.email && <Typography variant="caption" color="text.secondary">{user.email}</Typography>}
            </Box>
            <MenuItem onClick={logout}>
              <ListItemIcon><LogoutIcon fontSize="small" /></ListItemIcon>
              Sign out
            </MenuItem>
          </Menu>
        </Toolbar>
      </AppBar>
      <Container maxWidth="xl" sx={{ py: 3 }}>
        <Outlet />
      </Container>
    </Box>
  );
}
