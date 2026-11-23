import AddIcon from '@mui/icons-material/Add';
import SearchIcon from '@mui/icons-material/Search';
import { Box, Button, Chip, FormControl, FormControlLabel, InputAdornment, InputLabel, MenuItem, Pagination, Paper, Select, Skeleton, Stack, Switch, Table, TableBody, TableCell, TableHead, TableRow, TextField, Typography } from '@mui/material';
import { useEffect, useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { flattenCategories, indentLabel } from '../admin/categoryUtils';
import { useAdminProducts } from '../api/adminCatalog';
import { useCategories } from '../api/catalog';
import type { Availability } from '../api/types';
import { ErrorAlert } from '../components/ErrorAlert';
import { ProductFormDialog } from '../components/ProductFormDialog';
import { formatMoney } from '../utils/format';
import { useDebounce } from '../utils/useDebounce';

export function AdminProductsPage() {
  const navigate = useNavigate();
  const [search, setSearch] = useState('');
  const q = useDebounce(search.trim());
  const [categoryId, setCategoryId] = useState('');
  const [availability, setAvailability] = useState<Availability | ''>('');
  const [includeInactive, setIncludeInactive] = useState(true);
  const [page, setPage] = useState(0);
  const [creating, setCreating] = useState(false);
  useEffect(() => setPage(0), [q, categoryId, availability, includeInactive]);

  const categories = useCategories();
  const flat = useMemo(() => flattenCategories(categories.data ?? []), [categories.data]);
  const catName = useMemo(() => new Map(flat.map((c) => [c.id, c.name])), [flat]);
  const products = useAdminProducts({ q: q || undefined, categoryId: categoryId || undefined, availability: availability || undefined, includeInactive, page, size: 20, sort: 'name,asc' });

  return (
    <>
      <Stack direction="row" alignItems="center" justifyContent="space-between" sx={{ mb: 2 }}>
        <Typography variant="h5">Products</Typography>
        <Button variant="contained" startIcon={<AddIcon />} onClick={() => setCreating(true)}>New product</Button>
      </Stack>

      <Stack direction={{ xs: 'column', md: 'row' }} spacing={2} sx={{ mb: 2 }} alignItems={{ md: 'center' }}>
        <TextField size="small" label="Search by name or SKU" value={search} onChange={(e) => setSearch(e.target.value)} sx={{ flexGrow: 1 }}
          slotProps={{ input: { startAdornment: <InputAdornment position="start"><SearchIcon fontSize="small" /></InputAdornment> } }} />
        <FormControl size="small" sx={{ minWidth: 200 }}>
          <InputLabel id="pcat-label">Category</InputLabel>
          <Select labelId="pcat-label" label="Category" value={categoryId} onChange={(e) => setCategoryId(e.target.value)}>
            <MenuItem value="">All</MenuItem>
            {flat.map((c) => <MenuItem key={c.id} value={c.id}>{indentLabel(c)}</MenuItem>)}
          </Select>
        </FormControl>
        <FormControl size="small" sx={{ minWidth: 160 }}>
          <InputLabel id="pavail-label">Availability</InputLabel>
          <Select labelId="pavail-label" label="Availability" value={availability} onChange={(e) => setAvailability(e.target.value as Availability | '')}>
            <MenuItem value="">All</MenuItem>
            <MenuItem value="AVAILABLE">Available</MenuItem>
            <MenuItem value="OUT_OF_STOCK">Out of stock</MenuItem>
          </Select>
        </FormControl>
        <FormControlLabel control={<Switch checked={includeInactive} onChange={(e) => setIncludeInactive(e.target.checked)} />} label="Show inactive" />
      </Stack>

      {products.error && <ErrorAlert error={products.error} onRetry={() => void products.refetch()} />}
      {products.isLoading && <Skeleton variant="rounded" height={260} />}
      {products.data && products.data.content.length === 0 && <Typography color="text.secondary" sx={{ py: 6, textAlign: 'center' }}>No products found.</Typography>}
      {products.data && products.data.content.length > 0 && (
        <>
          <Typography variant="body2" color="text.secondary" sx={{ mb: 1 }}>{products.data.totalElements} products</Typography>
          <Paper variant="outlined" sx={{ overflowX: 'auto', opacity: products.isPlaceholderData ? 0.6 : 1 }}>
            <Table size="small">
              <TableHead>
                <TableRow>
                  <TableCell>SKU</TableCell>
                  <TableCell>Name</TableCell>
                  <TableCell>Category</TableCell>
                  <TableCell align="right">Price</TableCell>
                  <TableCell>Availability</TableCell>
                  <TableCell>Status</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {products.data.content.map((p) => (
                  <TableRow key={p.id} hover sx={{ cursor: 'pointer', opacity: p.active ? 1 : 0.6 }} onClick={() => navigate(`/admin/products/${p.id}`)}>
                    <TableCell>{p.sku}</TableCell>
                    <TableCell>{p.name}</TableCell>
                    <TableCell>{catName.get(p.categoryId) ?? '-'}</TableCell>
                    <TableCell align="right">{formatMoney(p.unitPrice, p.currency)} / {p.unitOfMeasure}</TableCell>
                    <TableCell><Chip size="small" label={p.availability === 'AVAILABLE' ? 'Available' : 'Out of stock'} color={p.availability === 'AVAILABLE' ? 'success' : 'warning'} variant="outlined" /></TableCell>
                    <TableCell><Chip size="small" label={p.active ? 'Active' : 'Inactive'} color={p.active ? 'success' : 'default'} variant={p.active ? 'filled' : 'outlined'} /></TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </Paper>
        </>
      )}
      {products.data && products.data.totalPages > 1 && (
        <Box sx={{ display: 'flex', justifyContent: 'center', mt: 3 }}>
          <Pagination count={products.data.totalPages} page={page + 1} onChange={(_, v) => setPage(v - 1)} color="primary" />
        </Box>
      )}

      <ProductFormDialog open={creating} product={null} onClose={() => setCreating(false)} onSaved={(p) => { setCreating(false); navigate(`/admin/products/${p.id}`); }} />
    </>
  );
}
