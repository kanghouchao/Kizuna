import { fetchBlob } from '@/shared/api';
import { fetchMonthlyPdf } from '../monthly-pdf';

jest.mock('@/shared/api', () => ({ fetchBlob: jest.fn() }));
const fetch = jest.mocked(fetchBlob);
const signal = new AbortController().signal;
afterEach(() => jest.clearAllMocks());

it('本人要求へ他人のIDや画面ページを送らない', async () => {
  const pdf = new Blob(['pdf'], { type: 'application/pdf' });
  fetch.mockResolvedValue(pdf);
  expect(await fetchMonthlyPdf({ scope: 'self', storeId: 1, month: '2026-09' }, signal)).toBe(pdf);
  expect(fetch).toHaveBeenCalledWith('/me/monthly-remunerations/pdf', {
    params: { store_id: 1, month: '2026-09' },
    signal,
    types: ['application/pdf'],
    fallback: expect.stringContaining('PDF を取得できませんでした'),
  });
});

it('店舗の要求は対象本人だけを送る', async () => {
  await fetchMonthlyPdf({ scope: 'store', personId: 2, month: '2026-09' }, signal);
  expect(fetch).toHaveBeenCalledWith(
    '/monthly-remunerations/pdf',
    expect.objectContaining({ params: { person_id: 2, month: '2026-09' } })
  );
});
