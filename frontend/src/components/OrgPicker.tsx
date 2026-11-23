import { Autocomplete, TextField } from '@mui/material';
import { useState } from 'react';
import { useOrgs, type Org, type OrgPath } from '../api/orgs';
import { useDebounce } from '../utils/useDebounce';

interface Props {
  path: OrgPath;
  label: string;
  value: Org | null;
  onChange: (org: Org | null) => void;
  error?: string;
  disabled?: boolean;
}

/** Type to search active customers or dealers by name, code or email (the search runs on the server). */
export function OrgPicker({ path, label, value, onChange, error, disabled }: Props) {
  const [input, setInput] = useState('');
  const q = useDebounce(input.trim(), 300);
  const list = useOrgs(path, { q: q || undefined, active: true, page: 0, size: 20, sort: 'name,asc' });
  const codeKey = path === '/customers' ? 'customerCode' : 'dealerCode';

  return (
    <Autocomplete
      options={list.data?.content ?? []}
      value={value}
      onChange={(_, o) => onChange(o)}
      inputValue={input}
      onInputChange={(_, v, reason) => { if (reason !== 'reset') setInput(v); }}
      getOptionLabel={(o) => `${o.name} (${String(o[codeKey] ?? '')})`}
      isOptionEqualToValue={(a, b) => a.id === b.id}
      filterOptions={(options) => options} // already filtered by the server
      loading={list.isFetching}
      disabled={disabled}
      noOptionsText="No active match"
      renderInput={(params) => <TextField {...params} label={label} size="small" required error={!!error} helperText={error} />}
    />
  );
}
