export interface Paged<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export type Availability = 'AVAILABLE' | 'OUT_OF_STOCK';

export interface CategoryNode {
  id: string;
  name: string;
  sortOrder: number;
  active: boolean;
  children: CategoryNode[];
}

export interface ProductSummary {
  id: string;
  sku: string;
  name: string;
  categoryId: string;
  unitPrice: number;
  currency: string;
  unitOfMeasure: string;
  availability: Availability;
  minOrderQty: number;
  active: boolean;
  primaryImageUrl: string | null;
}

export interface ProductImage {
  id: string;
  url: string;
  primary: boolean;
  sortOrder: number;
}

export interface ProductDetail extends Omit<ProductSummary, 'primaryImageUrl'> {
  description: string | null;
  categoryName: string;
  attributes: Record<string, unknown> | null;
  version: number;
  images: ProductImage[];
  createdAt: string;
  updatedAt: string;
}

export interface ProductQuery {
  q?: string;
  categoryId?: string;
  availability?: Availability;
  page: number;
  size: number;
  sort: string;
}
