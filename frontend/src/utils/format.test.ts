import { describe, expect, it } from '@jest/globals';
import { formatMoney } from './format';

describe('formatMoney', () => {
  it('formats rupees with two decimals', () => {
    const s = formatMoney(4500, 'INR');
    expect(s).toContain('₹');
    expect(s).toContain('4,500.00');
  });

  it('uses Indian digit grouping (lakhs)', () => {
    expect(formatMoney(1234567, 'INR')).toContain('12,34,567.00');
  });

  it('falls back to a plain string for a currency code Intl rejects', () => {
    expect(formatMoney(5, 'NOT-A-CODE')).toBe('NOT-A-CODE 5.00');
  });
});
