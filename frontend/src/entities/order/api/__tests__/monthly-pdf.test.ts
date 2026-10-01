import { apiClient } from '@/shared/api';
import { fetchMonthlyPdf } from '../monthly-pdf';

jest.mock('@/shared/api', () => ({ apiClient: { get: jest.fn() } }));
const get = jest.mocked(apiClient.get);
const signal = new AbortController().signal;
afterEach(() => jest.clearAllMocks());

it('本人要求へ他人のIDや画面ページを送らない', async () => {
  const pdf = new Blob(['pdf'], { type: 'application/pdf' });
  get.mockResolvedValue({ data: pdf });
  expect(await fetchMonthlyPdf({ scope: 'self', storeId: 1, month: '2026-09' }, signal)).toBe(pdf);
  expect(get).toHaveBeenCalledWith('/platform/me/monthly-remunerations/pdf', {
    params: { store_id: 1, month: '2026-09' },
    responseType: 'blob',
    headers: { Accept: 'application/pdf, application/json' },
    signal,
  });
});

it('JSONエラーBlobをメッセージへ戻す', async () => {
  const blob = new Blob([], { type: 'application/json' });
  Object.defineProperty(blob, 'text', {
    value: async () => '{"error":"対象明細が大きいため PDF を生成できませんでした"}',
  });
  get.mockRejectedValue({ response: { data: blob } });
  await expect(
    fetchMonthlyPdf({ scope: 'store', personId: 1, month: '2026-09' }, signal)
  ).rejects.toThrow('対象明細が大きいため PDF を生成できませんでした');
});

it.each([undefined, new Blob(['Bad gateway'], { type: 'text/html' })])(
  '通信と非JSON代理エラーをPDFとして保存しない',
  async data => {
    get.mockRejectedValue({ response: { data } });
    await expect(
      fetchMonthlyPdf({ scope: 'platform', storeId: 1, personId: 2, month: '2026-09' }, signal)
    ).rejects.toThrow('通信状態を確認');
  }
);
