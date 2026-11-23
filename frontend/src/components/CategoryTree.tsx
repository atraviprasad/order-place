import { List, ListItemButton, ListItemText, Paper, Typography } from '@mui/material';
import type { CategoryNode } from '../api/types';

interface Props {
  nodes: CategoryNode[];
  selectedId: string | null;
  onSelect: (id: string | null) => void;
}

/** Selecting a parent category also shows the products of its sub-categories (the API does that). */
export function CategoryTree({ nodes, selectedId, onSelect }: Props) {
  return (
    <Paper variant="outlined" sx={{ p: 1 }}>
      <Typography variant="overline" sx={{ px: 1 }}>Categories</Typography>
      <List dense disablePadding>
        <ListItemButton selected={selectedId === null} onClick={() => onSelect(null)}>
          <ListItemText primary="All products" />
        </ListItemButton>
        {nodes.map((n) => (
          <Node key={n.id} node={n} depth={0} selectedId={selectedId} onSelect={onSelect} />
        ))}
      </List>
    </Paper>
  );
}

function Node({ node, depth, selectedId, onSelect }: { node: CategoryNode; depth: number } & Omit<Props, 'nodes'>) {
  return (
    <>
      <ListItemButton selected={selectedId === node.id} onClick={() => onSelect(node.id)} sx={{ pl: 2 + depth * 2 }}>
        <ListItemText
          primary={node.name}
          slotProps={{ primary: { sx: { opacity: node.active ? 1 : 0.5 } } }}
        />
      </ListItemButton>
      {node.children.map((c) => (
        <Node key={c.id} node={c} depth={depth + 1} selectedId={selectedId} onSelect={onSelect} />
      ))}
    </>
  );
}
