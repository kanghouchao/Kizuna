import { apiClient } from '@/shared/api';
import { downloadReport, fetchReport, type ReportCriteria } from '../reports';

jest.mock('@/shared/api', () => ({ apiClient: { get: jest.fn() } }));
const get = jest.mocked(apiClient.get);
const criteria: ReportCriteria = {
  from: '2026-09-30',
  to: '2026-10-01',
  group_by: 'day',
  store_id: 7,
  include_remuneration: true,
};
beforeEach(() => jest.clearAllMocks());

test('query preserves null, zero and known subtotal without client recalculation', async () => {
  const response = {
    remuneration: {
      guarantee_total: null,
      known_guarantee_total: 2000,
      bonus_total: 0,
      total: null,
    },
  };
  get.mockResolvedValueOnce({ data: response });
  expect(await fetchReport('platform', criteria, 2)).toBe(response);
  expect(get).toHaveBeenCalledWith('/platform/operational-reports', {
    params: { ...criteria, page: 2, size: 20 },
  });
});

test.each([
  ['csv', 'text/csv'],
  ['xlsx', 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet'],
] as const)(
  '%s exports pass the same complete criteria and cancellation signal',
  async (format, type) => {
    const blob = new Blob(['report'], { type });
    get.mockResolvedValueOnce({ data: blob });
    const signal = new AbortController().signal;
    expect(await downloadReport('store', criteria, format, signal)).toBe(blob);
    expect(get).toHaveBeenCalledWith('/store/operational-reports/exports', {
      params: { ...criteria, format },
      responseType: 'blob',
      signal,
    });
  }
);
