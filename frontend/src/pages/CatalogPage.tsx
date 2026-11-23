import SearchIcon from '@mui/icons-material/Search';
import { Alert, Box, FormControl, InputAdornment, InputLabel, MenuItem, Pagination, Select, Skeleton, Snackbar, Stack, TextField, Typography } from '@mui/material';
import { useEffect, useState } from 'react';
import { useCategories, useProducts } from '../api/catalog';
import type { Availability, ProductDetail, ProductSummary } from '../api/types';
import { useAuth } from '../auth/AuthProvider';
import { useCart, type CartProduct } from '../cart/CartContext';
import { CategoryTree } from '../components/CategoryTree';
import { ErrorAlert } from '../components/ErrorAlert';
import { ProductCard } from '../components/ProductCard';
import { ProductDialog } from '../components/ProductDialog';
import { useDebounce } from '../utils/useDebounce';

const PAGE_SIZE = 12;

// these sort values are the ones the Catalog API accepts: name, sku, unitPrice, createdAt
const SORTS = [
  { value: 'name,asc', label: 'Name (A to Z)' },
  { value: 'unitPrice,asc', label: 'Price (low to high)' },
  { value: 'unitPrice,desc', label: 'Price (high to low)' },
  { value: 'createdAt,desc', label: 'Newest first' },
];

function fromSummary(p: ProductSummary): CartProduct {
  return { id: p.id, sku: p.sku, name: p.name, unitPrice: p.unitPrice, currency: p.currency, unitOfMeasure: p.unitOfMeasure, minOrderQty: p.minOrderQty, imageUrl: p.primaryImageUrl };
}

function fromDetail(p: ProductDetail): CartProduct {
  const image = p.images.find((i) => i.primary) ?? p.images[0];
  return { id: p.id, sku: p.sku, name: p.name, unitPrice: p.unitPrice, currency: p.currency, unitOfMeasure: p.unitOfMeasure, minOrderQty: p.minOrderQty, imageUrl: image?.url ?? null };
}

export function CatalogPage() {
  const { user } = useAuth();
  const cart = useCart();
  const [toast, setToast] = useState<{ severity: 'success' | 'warning'; text: string } | null>(null);
  const isCustomer = user.role === 'CUSTOMER';

  function addToCart(p: CartProduct, qty: number) {
    const result = cart.add(p, qty);
    setToast(result === 'added' ? { severity: 'success', text: `Added ${qty} x ${p.name} to your cart` } : { severity: 'warning', text: 'The cart is full (50 different products at most).' });
  }

  const [search, setSearch] = useState('');
  const q = useDebounce(search.trim());
  const [categoryId, setCategoryId] = useState<string | null>(null);
  const [availability, setAvailability] = useState<Availability | ''>('');
  const [sort, setSort] = useState(SORTS[0].value);
  const [page, setPage] = useState(0);
  const [openId, setOpenId] = useState<string | null>(null);

  // any filter change goes back to the first page
  useEffect(() => setPage(0), [q, categoryId, availability, sort]);

  const categories = useCategories();
  const products = useProducts({ q: q || undefined, categoryId: categoryId ?? undefined, availability: availability || undefined, page, size: PAGE_SIZE, sort });

  return (
    <>
      <Typography variant="h5" gutterBottom>Catalog</Typography>
      <Box sx={{ display: 'grid', gap: 3, gridTemplateColumns: { xs: '1fr', md: '260px 1fr' }, alignItems: 'start' }}>
        <Box>
          {categories.error ? (
            <ErrorAlert error={categories.error} onRetry={() => void categories.refetch()} />
          ) : categories.data ? (
            <CategoryTree nodes={categories.data} selectedId={categoryId} onSelect={setCategoryId} />
          ) : (
            <Skeleton variant="rounded" height={200} />
          )}
        </Box>

        <Box>
          <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2} sx={{ mb: 2 }}>
            <TextField
              size="small"
              label="Search by name or SKU"
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              sx={{ flexGrow: 1 }}
              slotProps={{ input: { startAdornment: <InputAdornment position="start"><SearchIcon fontSize="small" /></InputAdornment> } }}
            />
            <FormControl size="small" sx={{ minWidth: 160 }}>
              <InputLabel id="avail-label">Availability</InputLabel>
              <Select labelId="avail-label" label="Availability" value={availability} onChange={(e) => setAvailability(e.target.value as Availability | '')}>
                <MenuItem value="">All</MenuItem>
                <MenuItem value="AVAILABLE">Available</MenuItem>
                <MenuItem value="OUT_OF_STOCK">Out of stock</MenuItem>
              </Select>
            </FormControl>
            <FormControl size="small" sx={{ minWidth: 190 }}>
              <InputLabel id="sort-label">Sort by</InputLabel>
              <Select labelId="sort-label" label="Sort by" value={sort} onChange={(e) => setSort(e.target.value)}>
                {SORTS.map((s) => <MenuItem key={s.value} value={s.value}>{s.label}</MenuItem>)}
              </Select>
            </FormControl>
          </Stack>

          {products.error && <ErrorAlert error={products.error} onRetry={() => void products.refetch()} />}

          {products.isLoading && (
            <Box sx={{ display: 'grid', gap: 2, gridTemplateColumns: 'repeat(auto-fill, minmax(220px, 1fr))' }}>
              {Array.from({ length: 6 }, (_, i) => <Skeleton key={i} variant="rounded" height={300} />)}
            </Box>
          )}

          {products.data && products.data.content.length === 0 && (
            <Typography color="text.secondary" sx={{ py: 6, textAlign: 'center' }}>No products match your filters.</Typography>
          )}

          {products.data && products.data.content.length > 0 && (
            <>
              <Typography variant="body2" color="text.secondary" sx={{ mb: 1 }}>{products.data.totalElements} products</Typography>
              <Box sx={{ display: 'grid', gap: 2, gridTemplateColumns: 'repeat(auto-fill, minmax(220px, 1fr))', opacity: products.isPlaceholderData ? 0.6 : 1 }}>
                {products.data.content.map((p) => <ProductCard key={p.id} product={p} onOpen={setOpenId} onAdd={isCustomer ? (x) => addToCart(fromSummary(x), x.minOrderQty) : undefined} />)}
              </Box>
              {products.data.totalPages > 1 && (
                <Stack alignItems="center" sx={{ mt: 3 }}>
                  <Pagination count={products.data.totalPages} page={page + 1} onChange={(_, v) => setPage(v - 1)} color="primary" />
                </Stack>
              )}
            </>
          )}
        </Box>
      </Box>
      <ProductDialog productId={openId} onClose={() => setOpenId(null)} onAdd={isCustomer ? (p, qty) => { addToCart(fromDetail(p), qty); setOpenId(null); } : undefined} />
      {toast && (
        <Snackbar open autoHideDuration={3000} onClose={() => setToast(null)} anchorOrigin={{ vertical: 'bottom', horizontal: 'center' }}>
          <Alert severity={toast.severity} variant="filled" onClose={() => setToast(null)}>{toast.text}</Alert>
        </Snackbar>
      )}
    </>
  );
}
