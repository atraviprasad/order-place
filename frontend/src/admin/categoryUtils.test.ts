import { describe, expect, it } from '@jest/globals';
import type { CategoryNode } from '../api/types';
import { flattenCategories, indentLabel, subtreeIds } from './categoryUtils';

const node = (id: string, name: string, children: CategoryNode[] = [], active = true): CategoryNode => ({ id, name, sortOrder: 0, active, children });

const tree: CategoryNode[] = [
  node('furniture', 'Furniture', [node('chairs', 'Chairs', [node('office', 'Office chairs')]), node('tables', 'Tables')]),
  node('storage', 'Storage', [], false),
];

describe('flattenCategories', () => {
  it('lists parents before children with depth and parent id', () => {
    const flat = flattenCategories(tree);
    expect(flat.map((c) => [c.id, c.depth, c.parentId])).toEqual([
      ['furniture', 0, null],
      ['chairs', 1, 'furniture'],
      ['office', 2, 'chairs'],
      ['tables', 1, 'furniture'],
      ['storage', 0, null],
    ]);
  });

  it('returns an empty list for an empty tree', () => {
    expect(flattenCategories([])).toEqual([]);
  });
});

describe('subtreeIds', () => {
  it('contains the category and all its descendants, nothing else', () => {
    const ids = subtreeIds(flattenCategories(tree), 'chairs');
    expect([...ids].sort()).toEqual(['chairs', 'office']);
  });

  it('is only the category itself for a leaf', () => {
    expect([...subtreeIds(flattenCategories(tree), 'tables')]).toEqual(['tables']);
  });
});

describe('indentLabel', () => {
  it('indents by depth and marks inactive ones', () => {
    const flat = flattenCategories(tree);
    expect(indentLabel(flat[0])).toBe('Furniture');
    expect(indentLabel(flat[2])).toContain('Office chairs');
    expect(indentLabel(flat[2]).startsWith(' ')).toBe(true);
    expect(indentLabel(flat[4])).toBe('Storage (inactive)');
  });
});
