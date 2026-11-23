import type { CategoryNode } from '../api/types';

export interface FlatCategory {
  id: string;
  name: string;
  depth: number;
  parentId: string | null;
  active: boolean;
  sortOrder: number;
}

/** The tree as one ordered list (parents before their children), with the depth for indenting. */
export function flattenCategories(nodes: CategoryNode[], depth = 0, parentId: string | null = null): FlatCategory[] {
  return nodes.flatMap((n) => [
    { id: n.id, name: n.name, depth, parentId, active: n.active, sortOrder: n.sortOrder },
    ...flattenCategories(n.children, depth + 1, n.id),
  ]);
}

/** A category and everything below it: none of these may become its new parent. */
export function subtreeIds(flat: FlatCategory[], id: string): Set<string> {
  const ids = new Set<string>([id]);
  let grew = true;
  while (grew) {
    grew = false;
    for (const c of flat) {
      if (c.parentId && ids.has(c.parentId) && !ids.has(c.id)) {
        ids.add(c.id);
        grew = true;
      }
    }
  }
  return ids;
}

export function indentLabel(c: FlatCategory): string {
  return `${'   '.repeat(c.depth)}${c.depth > 0 ? '└ ' : ''}${c.name}${c.active ? '' : ' (inactive)'}`;
}
