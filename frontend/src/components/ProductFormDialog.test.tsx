import { beforeEach, describe, expect, it, jest } from '@jest/globals';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import type { CategoryNode, ProductDetail } from '../api/types';
import { ProductFormDialog } from './ProductFormDialog';

// The dialog talks to the server through two hooks. Replacing them keeps this a test of the form only:
// no network, no QueryClientProvider. jest.mock calls are lifted above the imports by ts-jest.
const mutateAsync = jest.fn<(arg: { id?: string; body: Record<string, unknown> }) => Promise<unknown>>();
jest.mock('../api/adminCatalog', () => ({ useSaveProduct: () => ({ mutateAsync, isPending: false }) }));
const tree: CategoryNode[] = [{ id: 'cat-1', name: 'Chairs', sortOrder: 0, active: true, children: [] }];
jest.mock('../api/catalog', () => ({ useCategories: () => ({ data: tree }) }));

const existing: ProductDetail = {
  id: 'p-1', sku: 'CHR-ERG-001', name: 'Ergonomic Office Chair', description: 'Lumbar support', categoryId: 'cat-1', categoryName: 'Chairs',
  unitPrice: 4500, currency: 'INR', unitOfMeasure: 'EACH', availability: 'AVAILABLE', minOrderQty: 10, active: true,
  attributes: { colour: 'Black', weight: 12 }, version: 3, images: [], createdAt: '2026-01-01T00:00:00Z', updatedAt: '2026-01-02T00:00:00Z',
};

beforeEach(() => {
  mutateAsync.mockResolvedValue(existing);
});

describe('ProductFormDialog', () => {
  it('create mode: empty form shows field errors and does not call the server', async () => {
    const user = userEvent.setup();
    render(<ProductFormDialog open product={null} onClose={() => undefined} onSaved={() => undefined} />);
    await user.click(screen.getByRole('button', { name: 'Save' }));
    expect(screen.getByText(/2-40 characters/)).toBeInTheDocument();
    expect(screen.getByText('Choose a category')).toBeInTheDocument();
    expect(screen.getByText(/at most 2 decimals/)).toBeInTheDocument();
    expect(mutateAsync).not.toHaveBeenCalled();
  });

  it('edit mode: SKU is read-only and a price with 3 decimals is rejected', async () => {
    const user = userEvent.setup();
    render(<ProductFormDialog open product={existing} onClose={() => undefined} onSaved={() => undefined} />);
    expect(screen.getByLabelText(/SKU/)).toBeDisabled();
    const price = screen.getByLabelText(/Unit price/);
    await user.clear(price);
    await user.type(price, '10.123');
    await user.click(screen.getByRole('button', { name: 'Save' }));
    expect(screen.getByText(/at most 2 decimals/)).toBeInTheDocument();
    expect(mutateAsync).not.toHaveBeenCalled();
  });

  it('edit mode: sends the changed price with the version and keeps number attributes as numbers', async () => {
    const user = userEvent.setup();
    const onSaved = jest.fn<(p: ProductDetail) => void>();
    render(<ProductFormDialog open product={existing} onClose={() => undefined} onSaved={onSaved} />);
    const price = screen.getByLabelText(/Unit price/);
    await user.clear(price);
    await user.type(price, '4800.50');
    await user.click(screen.getByRole('button', { name: 'Save' }));

    expect(mutateAsync).toHaveBeenCalledTimes(1);
    const { id, body } = mutateAsync.mock.calls[0][0];
    expect(id).toBe('p-1');
    expect(body).toMatchObject({ unitPrice: 4800.5, version: 3, categoryId: 'cat-1', attributes: { colour: 'Black', weight: 12 } });
    expect(body).not.toHaveProperty('sku'); // the SKU is never sent on update
    expect(onSaved).toHaveBeenCalledWith(existing);
  });
});
