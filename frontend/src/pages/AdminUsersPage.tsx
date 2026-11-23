import AddIcon from '@mui/icons-material/Add';
import SearchIcon from '@mui/icons-material/Search';
import { Box, Button, Chip, FormControl, InputAdornment, InputLabel, MenuItem, Pagination, Paper, Select, Skeleton, Stack, Table, TableBody, TableCell, TableHead, TableRow, TextField, Typography } from '@mui/material';
import { useEffect, useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useOrgs } from '../api/orgs';
import { useUsers, type UserRole } from '../api/users';
import { ErrorAlert } from '../components/ErrorAlert';
import { UserFormDialog } from '../components/UserFormDialog';
import { formatDateTime } from '../utils/format';
import { useDebounce } from '../utils/useDebounce';

export const ROLE_LABEL: Record<UserRole, string> = { CUSTOMER: 'Customer user', DEALER: 'Dealer user' };

export function AdminUsersPage() {
  const navigate = useNavigate();
  const [search, setSearch] = useState('');
  const q = useDebounce(search.trim());
  const [role, setRole] = useState<UserRole | ''>('');
  const [active, setActive] = useState<'all' | 'active' | 'inactive'>('all');
  const [page, setPage] = useState(0);
  const [creating, setCreating] = useState(false);
  useEffect(() => setPage(0), [q, role, active]);

  const users = useUsers({ q: q || undefined, role: role || undefined, active: active === 'all' ? undefined : active === 'active', page, size: 20, sort: 'lastName,asc' });
  // dealer users carry only the dealer id: names come from the dealer list (first 100 dealers)
  const dealers = useOrgs('/dealers', { page: 0, size: 100, sort: 'name,asc' });
  const dealerName = useMemo(() => new Map((dealers.data?.content ?? []).map((d) => [d.id, d.name])), [dealers.data]);

  return (
    <>
      <Stack direction="row" alignItems="center" justifyContent="space-between" sx={{ mb: 2 }}>
        <Typography variant="h5">Users</Typography>
        <Button variant="contained" startIcon={<AddIcon />} onClick={() => setCreating(true)}>New user</Button>
      </Stack>

      <Stack direction={{ xs: 'column', md: 'row' }} spacing={2} sx={{ mb: 2 }}>
        <TextField size="small" label="Search by name or email" value={search} onChange={(e) => setSearch(e.target.value)} sx={{ flexGrow: 1 }}
          slotProps={{ input: { startAdornment: <InputAdornment position="start"><SearchIcon fontSize="small" /></InputAdornment> } }} />
        <FormControl size="small" sx={{ minWidth: 170 }}>
          <InputLabel id="role-filter-label">Role</InputLabel>
          <Select labelId="role-filter-label" label="Role" value={role} onChange={(e) => setRole(e.target.value as UserRole | '')}>
            <MenuItem value="">All</MenuItem>
            <MenuItem value="CUSTOMER">Customer users</MenuItem>
            <MenuItem value="DEALER">Dealer users</MenuItem>
          </Select>
        </FormControl>
        <FormControl size="small" sx={{ minWidth: 150 }}>
          <InputLabel id="uactive-label">Status</InputLabel>
          <Select labelId="uactive-label" label="Status" value={active} onChange={(e) => setActive(e.target.value as typeof active)}>
            <MenuItem value="all">All</MenuItem>
            <MenuItem value="active">Active</MenuItem>
            <MenuItem value="inactive">Inactive</MenuItem>
          </Select>
        </FormControl>
      </Stack>

      {users.error && <ErrorAlert error={users.error} onRetry={() => void users.refetch()} />}
      {users.isLoading && <Skeleton variant="rounded" height={260} />}
      {users.data && users.data.content.length === 0 && <Typography color="text.secondary" sx={{ py: 6, textAlign: 'center' }}>No users found.</Typography>}
      {users.data && users.data.content.length > 0 && (
        <>
          <Typography variant="body2" color="text.secondary" sx={{ mb: 1 }}>{users.data.totalElements} users</Typography>
          <Paper variant="outlined" sx={{ overflowX: 'auto', opacity: users.isPlaceholderData ? 0.6 : 1 }}>
            <Table size="small">
              <TableHead>
                <TableRow>
                  <TableCell>Name</TableCell>
                  <TableCell>Email</TableCell>
                  <TableCell>Role</TableCell>
                  <TableCell>Organisation</TableCell>
                  <TableCell>Status</TableCell>
                  <TableCell>Created</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {users.data.content.map((u) => (
                  <TableRow key={u.id} hover sx={{ cursor: 'pointer', opacity: u.active ? 1 : 0.6 }} onClick={() => navigate(`/admin/users/${u.id}`)}>
                    <TableCell>{u.firstName} {u.lastName}</TableCell>
                    <TableCell>{u.email}</TableCell>
                    <TableCell>{ROLE_LABEL[u.role]}</TableCell>
                    <TableCell>{u.role === 'CUSTOMER' ? u.orgName ?? '-' : dealerName.get(u.orgId) ?? '-'}</TableCell>
                    <TableCell><Chip size="small" label={u.active ? 'Active' : 'Inactive'} color={u.active ? 'success' : 'default'} variant={u.active ? 'filled' : 'outlined'} /></TableCell>
                    <TableCell>{formatDateTime(u.createdAt)}</TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </Paper>
        </>
      )}
      {users.data && users.data.totalPages > 1 && (
        <Box sx={{ display: 'flex', justifyContent: 'center', mt: 3 }}>
          <Pagination count={users.data.totalPages} page={page + 1} onChange={(_, v) => setPage(v - 1)} color="primary" />
        </Box>
      )}

      <UserFormDialog open={creating} user={null} onClose={() => setCreating(false)}
        onSaved={(r) => { setCreating(false); navigate(`/admin/users/${r.user.id}`, { state: { emailSent: r.setPasswordEmailSent } }); }} />
    </>
  );
}
