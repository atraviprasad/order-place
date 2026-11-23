import AddIcon from '@mui/icons-material/Add';
import SearchIcon from '@mui/icons-material/Search';
import { Box, Button, Chip, FormControl, InputAdornment, InputLabel, MenuItem, Pagination, Paper, Select, Skeleton, Stack, Table, TableBody, TableCell, TableHead, TableRow, TextField, Typography } from '@mui/material';
import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useOrgs } from '../api/orgs';
import type { OrgKind } from '../admin/orgKinds';
import { ErrorAlert } from '../components/ErrorAlert';
import { OrgFormDialog } from '../components/OrgFormDialog';
import { formatDateTime } from '../utils/format';
import { useDebounce } from '../utils/useDebounce';

export function AdminOrgsPage({ kind }: { kind: OrgKind }) {
  const navigate = useNavigate();
  const [search, setSearch] = useState('');
  const q = useDebounce(search.trim());
  const [active, setActive] = useState<'all' | 'active' | 'inactive'>('all');
  const [page, setPage] = useState(0);
  const [creating, setCreating] = useState(false);
  useEffect(() => setPage(0), [q, active, kind.path]);

  const list = useOrgs(kind.path, { q: q || undefined, active: active === 'all' ? undefined : active === 'active', page, size: 20, sort: 'name,asc' });

  return (
    <>
      <Stack direction="row" alignItems="center" justifyContent="space-between" sx={{ mb: 2 }}>
        <Typography variant="h5">{kind.plural}</Typography>
        <Button variant="contained" startIcon={<AddIcon />} onClick={() => setCreating(true)}>New {kind.singular}</Button>
      </Stack>

      <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2} sx={{ mb: 2 }}>
        <TextField size="small" label={`Search by name, code or email`} value={search} onChange={(e) => setSearch(e.target.value)} sx={{ flexGrow: 1 }}
          slotProps={{ input: { startAdornment: <InputAdornment position="start"><SearchIcon fontSize="small" /></InputAdornment> } }} />
        <FormControl size="small" sx={{ minWidth: 160 }}>
          <InputLabel id="active-label">Status</InputLabel>
          <Select labelId="active-label" label="Status" value={active} onChange={(e) => setActive(e.target.value as typeof active)}>
            <MenuItem value="all">All</MenuItem>
            <MenuItem value="active">Active</MenuItem>
            <MenuItem value="inactive">Inactive</MenuItem>
          </Select>
        </FormControl>
      </Stack>

      {list.error && <ErrorAlert error={list.error} onRetry={() => void list.refetch()} />}
      {list.isLoading && <Skeleton variant="rounded" height={260} />}
      {list.data && list.data.content.length === 0 && <Typography color="text.secondary" sx={{ py: 6, textAlign: 'center' }}>No {kind.plural.toLowerCase()} found.</Typography>}
      {list.data && list.data.content.length > 0 && (
        <>
          <Typography variant="body2" color="text.secondary" sx={{ mb: 1 }}>{list.data.totalElements} {kind.plural.toLowerCase()}</Typography>
          <Paper variant="outlined" sx={{ overflowX: 'auto', opacity: list.isPlaceholderData ? 0.6 : 1 }}>
            <Table size="small">
              <TableHead>
                <TableRow>
                  <TableCell>Code</TableCell>
                  <TableCell>Name</TableCell>
                  <TableCell>Contact</TableCell>
                  <TableCell>Email</TableCell>
                  <TableCell>Phone</TableCell>
                  <TableCell>Status</TableCell>
                  <TableCell>Created</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {list.data.content.map((o) => (
                  <TableRow key={o.id} hover sx={{ cursor: 'pointer', opacity: o.active ? 1 : 0.6 }} onClick={() => navigate(`/admin/${kind.route}/${o.id}`)}>
                    <TableCell>{String(o[kind.codeKey] ?? '')}</TableCell>
                    <TableCell>{o.name}</TableCell>
                    <TableCell>{o.contactPerson}</TableCell>
                    <TableCell>{o.email}</TableCell>
                    <TableCell>{o.phone}</TableCell>
                    <TableCell><Chip size="small" label={o.active ? 'Active' : 'Inactive'} color={o.active ? 'success' : 'default'} variant={o.active ? 'filled' : 'outlined'} /></TableCell>
                    <TableCell>{formatDateTime(o.createdAt)}</TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </Paper>
        </>
      )}
      {list.data && list.data.totalPages > 1 && (
        <Box sx={{ display: 'flex', justifyContent: 'center', mt: 3 }}>
          <Pagination count={list.data.totalPages} page={page + 1} onChange={(_, v) => setPage(v - 1)} color="primary" />
        </Box>
      )}

      <OrgFormDialog open={creating} kind={kind} org={null} onClose={() => setCreating(false)} onSaved={(o) => { setCreating(false); navigate(`/admin/${kind.route}/${o.id}`); }} />
    </>
  );
}
