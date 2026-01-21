import React from 'react';
import { render, screen, waitFor } from '@testing-library/react';
import { useTenantInfo } from '@/hooks/useTenantInfo';

function TestComp() {
  const { tenant, loading, error } = useTenantInfo();
  return (
    <div>
      <div data-testid="loading">{String(loading)}</div>
      <div data-testid="tenant">{tenant ? JSON.stringify(tenant) : 'null'}</div>
      <div data-testid="error">{error ?? ''}</div>
    </div>
  );
}

describe('useTenantInfo', () => {
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

  it('skips fetching on /central path', async () => {
    setLocation('kizuna.test', '/central');
    (global as any).fetch = jest.fn();
    render(<TestComp />);
    await waitFor(() => expect(screen.getByTestId('loading').textContent).toBe('false'));
    expect((global as any).fetch).not.toHaveBeenCalled();
    expect(screen.getByTestId('tenant').textContent).toBe('null');
    expect(screen.getByTestId('error').textContent).toBe('');
  });

  it('resolves tenant info when API returns success', async () => {
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
    const tenant = screen.getByTestId('tenant').textContent || '';
    expect(tenant).toContain('tenant.example.test');
    expect(tenant).toContain('"tenant_id":"1"');
  });

  it('handles 404 as tenant not found', async () => {
    setLocation('no.example.test', '/');
    (global as any).fetch = jest.fn(async () => ({ ok: false, status: 404 }));
    render(<TestComp />);
    await waitFor(() => expect(screen.getByTestId('loading').textContent).toBe('false'));
    expect(screen.getByTestId('tenant').textContent).toBe('null');
    // Avoid coupling to a specific localized string; just ensure an error is surfaced
    expect(screen.getByTestId('error').textContent).not.toBe('');
  });
});
