import React from 'react';
import { render, screen, waitFor } from '@testing-library/react';
import { useStoreInfo } from '@/entities/store';

function TestComp() {
  const { store, loading, error } = useStoreInfo();
  return (
    <div>
      <div data-testid="loading">{String(loading)}</div>
      <div data-testid="store">{store ? JSON.stringify(store) : 'null'}</div>
      <div data-testid="error">{error ?? ''}</div>
    </div>
  );
}

describe('useStoreInfo', () => {
  const originalHref = window.location.href;
  const setLocation = (_hostname: string, pathname: string) => {
    // Use history.pushState with a relative path to avoid cross-origin pushState errors in jsdom
    window.history.pushState({}, '', pathname);
  };
  afterEach(() => {
    // restore original URL
    window.history.pushState({}, '', originalHref);
    (global as any).fetch?.mockClear?.();
  });

  it('skips fetching on /platform path', async () => {
    setLocation('kizuna.test', '/platform');
    (global as any).fetch = jest.fn();
    render(<TestComp />);
    await waitFor(() => expect(screen.getByTestId('loading').textContent).toBe('false'));
    expect((global as any).fetch).not.toHaveBeenCalled();
    expect(screen.getByTestId('store').textContent).toBe('null');
    expect(screen.getByTestId('error').textContent).toBe('');
  });

  it('resolves store info when API returns success', async () => {
    setLocation('tenant.example.test', '/');
    (global as any).fetch = jest.fn(async () => ({
      ok: true,
      status: 200,
      json: async () => ({
        success: true,
        domain: 'tenant.example.test',
        tenant_id: '1',
        tenant_name: 'T1',
      }),
    }));
    render(<TestComp />);
    await waitFor(() => expect(screen.getByTestId('loading').textContent).toBe('false'));
    const store = screen.getByTestId('store').textContent || '';
    expect(store).toContain('tenant.example.test');
    expect(store).toContain('"tenant_id":"1"');
  });

  it('handles 404 as store not found', async () => {
    setLocation('no.example.test', '/');
    (global as any).fetch = jest.fn(async () => ({ ok: false, status: 404 }));
    render(<TestComp />);
    await waitFor(() => expect(screen.getByTestId('loading').textContent).toBe('false'));
    expect(screen.getByTestId('store').textContent).toBe('null');
    // Avoid coupling to a specific localized string; just ensure an error is surfaced
    expect(screen.getByTestId('error').textContent).not.toBe('');
  });
});
