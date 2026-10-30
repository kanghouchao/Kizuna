import type { AxiosAdapter, InternalAxiosRequestConfig } from 'axios';
import { apiClient } from '@/shared/api';
import { guestOrderApplicationApi } from '../order';

describe('guestOrderApplicationApi.request', () => {
  it('呼び出し元が渡した店舗を X-Store-ID で名乗る', async () => {
    let sent: InternalAxiosRequestConfig | undefined;
    const original = apiClient.defaults.adapter;
    apiClient.defaults.adapter = (async (config: InternalAxiosRequestConfig) => {
      sent = config;
      return { data: { id: 'a1' }, status: 201, statusText: 'Created', headers: {}, config };
    }) as AxiosAdapter;

    try {
      await guestOrderApplicationApi.request('7', {
        contact_consent: { version: '1', business_allowed: true, marketing_allowed: false },
        business_date: '2026-08-25',
        pax: 1,
        contact_snapshot: { name: 'ゲスト', phone_number: '09000000000', email: '', line_id: '' },
      });
    } finally {
      apiClient.defaults.adapter = original;
    }

    expect(sent?.url).toBe('/order-applications/public');
    expect(sent?.headers.get('X-Store-ID')).toBe('7');
  });
});
