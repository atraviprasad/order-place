import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState, type ReactNode } from 'react';
import type { Order } from '../api/orderTypes';
import { useAuth } from '../auth/AuthProvider';

export const MAX_LINES = 50; // same limit as the Order Service (orders.max-lines)
export const MAX_QTY = 100000;

/** The few product fields the cart needs; both the list item and the detail view can supply them. */
export interface CartProduct {
  id: string;
  sku: string;
  name: string;
  unitPrice: number;
  currency: string;
  unitOfMeasure: string;
  minOrderQty: number;
  imageUrl: string | null;
}

export interface CartItem {
  productId: string;
  sku: string;
  name: string;
  unitPrice: number; // for display only; the server prices the order from the catalog
  currency: string;
  unitOfMeasure: string;
  minOrderQty: number;
  quantity: number;
  imageUrl: string | null;
}

interface CartState {
  items: CartItem[];
  dealerId: string | null;
  address: string;
  notes: string;
  draft: { id: string; version: number } | null; // set when the cart was loaded from, or saved as, a draft
}

const EMPTY: CartState = { items: [], dealerId: null, address: '', notes: '', draft: null };

interface CartApi extends CartState {
  lineCount: number;
  add: (p: CartProduct, qty: number) => 'added' | 'full';
  setQty: (productId: string, qty: number) => void;
  remove: (productId: string) => void;
  setDealer: (id: string | null) => void;
  setAddress: (v: string) => void;
  setNotes: (v: string) => void;
  setDraft: (d: CartState['draft']) => void;
  loadFromDraft: (order: Order) => void;
  clear: () => void;
}

const CartContext = createContext<CartApi | null>(null);

function load(key: string): CartState {
  try {
    const raw = localStorage.getItem(key);
    if (raw) return { ...EMPTY, ...(JSON.parse(raw) as Partial<CartState>) };
  } catch {
    /* storage blocked or the content is damaged: start with an empty cart */
  }
  return EMPTY;
}

export function CartProvider({ children }: { children: ReactNode }) {
  const { user } = useAuth();
  const key = `orderplace.cart.${user.id}`; // one cart per user on this browser
  const [state, setState] = useState<CartState>(() => load(key));

  useEffect(() => {
    try {
      localStorage.setItem(key, JSON.stringify(state));
    } catch {
      /* ignore: the cart still works for this page view */
    }
  }, [key, state]);

  const latest = useRef(state);
  latest.current = state;

  const add = useCallback<CartApi['add']>((p, qty) => {
    const current = latest.current;
    if (!current.items.some((i) => i.productId === p.id) && current.items.length >= MAX_LINES) return 'full';
    setState((s) => {
      const existing = s.items.find((i) => i.productId === p.id);
      const items = existing
        ? s.items.map((i) => (i.productId === p.id ? { ...i, quantity: Math.min(MAX_QTY, i.quantity + qty) } : i))
        : [...s.items, { productId: p.id, sku: p.sku, name: p.name, unitPrice: p.unitPrice, currency: p.currency, unitOfMeasure: p.unitOfMeasure, minOrderQty: p.minOrderQty, quantity: Math.min(MAX_QTY, qty), imageUrl: p.imageUrl }];
      return { ...s, items };
    });
    return 'added';
  }, []);

  const api = useMemo<CartApi>(
    () => ({
      ...state,
      lineCount: state.items.length,
      add,
      setQty: (productId, qty) =>
        setState((s) => ({ ...s, items: s.items.map((i) => (i.productId === productId ? { ...i, quantity: Math.max(1, Math.min(MAX_QTY, Math.floor(qty) || 1)) } : i)) })),
      remove: (productId) => setState((s) => ({ ...s, items: s.items.filter((i) => i.productId !== productId) })),
      setDealer: (dealerId) => setState((s) => ({ ...s, dealerId })),
      setAddress: (address) => setState((s) => ({ ...s, address })),
      setNotes: (notes) => setState((s) => ({ ...s, notes })),
      setDraft: (draft) => setState((s) => ({ ...s, draft })),
      loadFromDraft: (o) =>
        setState({
          items: o.lines.map((l) => ({ productId: l.productId, sku: l.sku, name: l.productName, unitPrice: l.unitPrice, currency: o.currency, unitOfMeasure: '', minOrderQty: 1, quantity: l.submittedQty, imageUrl: null })),
          dealerId: o.dealer?.id ?? null,
          address: o.deliveryAddress ?? '',
          notes: o.customerNotes ?? '',
          draft: { id: o.id, version: o.version },
        }),
      clear: () => setState(EMPTY),
    }),
    [state, add],
  );

  return <CartContext.Provider value={api}>{children}</CartContext.Provider>;
}

export function useCart(): CartApi {
  const ctx = useContext(CartContext);
  if (!ctx) throw new Error('useCart must be used inside CartProvider');
  return ctx;
}
