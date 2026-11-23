import AddShoppingCartIcon from '@mui/icons-material/AddShoppingCart';
import ImageNotSupportedOutlinedIcon from '@mui/icons-material/ImageNotSupportedOutlined';
import { Box, Button, Card, CardActionArea, CardActions, CardContent, CardMedia, Chip, Stack, Typography } from '@mui/material';
import type { ProductSummary } from '../api/types';
import { formatMoney } from '../utils/format';

interface Props {
  product: ProductSummary;
  onOpen: (id: string) => void;
  /** Only passed for customers: the card then shows a quick "Add" button (adds the minimum order quantity). */
  onAdd?: (p: ProductSummary) => void;
}

export function ProductCard({ product, onOpen, onAdd }: Props) {
  const unavailable = product.availability === 'OUT_OF_STOCK';
  return (
    <Card variant="outlined" sx={{ height: '100%', display: 'flex', flexDirection: 'column', opacity: product.active ? 1 : 0.6 }}>
      <CardActionArea onClick={() => onOpen(product.id)} sx={{ flexGrow: 1, display: 'flex', flexDirection: 'column', alignItems: 'stretch' }}>
        {product.primaryImageUrl ? (
          <CardMedia component="img" height="160" image={product.primaryImageUrl} alt={product.name} sx={{ objectFit: 'cover' }} />
        ) : (
          <Box sx={{ height: 160, display: 'grid', placeItems: 'center', bgcolor: 'action.hover', color: 'text.disabled' }}>
            <ImageNotSupportedOutlinedIcon fontSize="large" />
          </Box>
        )}
        <CardContent sx={{ flexGrow: 1 }}>
          <Typography variant="subtitle1" sx={{ fontWeight: 500, lineHeight: 1.3 }}>{product.name}</Typography>
          <Typography variant="caption" color="text.secondary">{product.sku}</Typography>
          <Typography variant="h6" sx={{ mt: 1 }}>
            {formatMoney(product.unitPrice, product.currency)}
            <Typography component="span" variant="body2" color="text.secondary"> / {product.unitOfMeasure}</Typography>
          </Typography>
          <Stack direction="row" spacing={1} sx={{ mt: 1 }} useFlexGap flexWrap="wrap">
            <Chip size="small" color={unavailable ? 'default' : 'success'} variant={unavailable ? 'outlined' : 'filled'} label={unavailable ? 'Out of stock' : 'Available'} />
            <Chip size="small" variant="outlined" label={`Min ${product.minOrderQty}`} />
            {!product.active && <Chip size="small" color="warning" label="Inactive" />}
          </Stack>
        </CardContent>
      </CardActionArea>
      {onAdd && (
        <CardActions>
          <Button size="small" startIcon={<AddShoppingCartIcon />} disabled={unavailable || !product.active} onClick={() => onAdd(product)}>
            Add {product.minOrderQty}
          </Button>
        </CardActions>
      )}
    </Card>
  );
}
