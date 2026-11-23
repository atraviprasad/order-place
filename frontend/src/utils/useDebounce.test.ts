import { afterEach, beforeEach, describe, expect, it, jest } from '@jest/globals';
import { act, renderHook } from '@testing-library/react';
import { useDebounce } from './useDebounce';

describe('useDebounce', () => {
 beforeEach(() => {
  jest.useFakeTimers();
});

afterEach(() => {
  jest.useRealTimers();
});
  it('returns the first value at once', () => {
    const { result } = renderHook(() => useDebounce('a', 400));
    expect(result.current).toBe('a');
  });

  it('only changes after the delay, and only to the latest value', () => {
    const { result, rerender } = renderHook(({ v }) => useDebounce(v, 400), { initialProps: { v: 'a' } });
    rerender({ v: 'ab' });
    rerender({ v: 'abc' });
    act(() => { jest.advanceTimersByTime(399); });
    expect(result.current).toBe('a'); // not yet
    act(() => { jest.advanceTimersByTime(1); });
    expect(result.current).toBe('abc'); // 'ab' was skipped
  });
});
