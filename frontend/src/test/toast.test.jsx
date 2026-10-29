import { act, render, screen } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import '@testing-library/jest-dom/vitest';
import { ToastProvider, useToast } from '../lib/AppContext';

function Fire() {
  const t = useToast();
  return <button onClick={() => { t.push('Shipping is not configured.', 'danger'); t.push('Shipping is not configured.', 'danger'); t.push('Other', 'danger'); }}>go</button>;
}

describe('toast de-duplication', () => {
  afterEach(() => vi.useRealTimers());
  it('shows an identical message only once, and removes it after the timeout', () => {
    vi.useFakeTimers();
    render(<ToastProvider><Fire /></ToastProvider>);
    const go = screen.getByText('go');
    act(() => { go.click(); go.click(); go.click(); });
    expect(screen.getAllByText('Shipping is not configured.')).toHaveLength(1);
    expect(screen.getAllByText('Other')).toHaveLength(1);
    act(() => { vi.advanceTimersByTime(4000); });
    expect(screen.queryByText('Shipping is not configured.')).toBeNull();
  });
});
