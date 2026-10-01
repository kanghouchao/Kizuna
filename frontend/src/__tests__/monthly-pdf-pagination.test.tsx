import { act, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MonthlyRemunerationsPage } from '@/_pages/store-monthly-remunerations';
import PlatformMonthlyRemunerationsPage from '@/_pages/platform-monthly-remunerations';
import { CastMonthlyRemunerationsPage } from '@/_pages/cast-portal/ui/CastMonthlyRemunerationsPage';
import {
  fetchMonthlyPdf,
  monthlyRemunerationApi,
  platformMonthlyRemunerationApi,
  selfMonthlyRemunerationApi,
} from '@/entities/order';

jest.mock('next/navigation', () => ({ useParams: () => ({ storeId: '1' }) }));
jest.mock('@/shared/lib', () => ({
  ...jest.requireActual('@/shared/lib'),
  hasPermission: () => true,
  readTokenClaims: () => ({}),
}));
jest.mock('@/entities/order', () => ({
  fetchMonthlyPdf: jest.fn(),
  monthlyRemunerationApi: { casts: jest.fn(), monthly: jest.fn() },
  platformMonthlyRemunerationApi: { stores: jest.fn(), casts: jest.fn(), monthly: jest.fn() },
  selfMonthlyRemunerationApi: { stores: jest.fn(), monthly: jest.fn() },
  selfRemunerationApi: { detail: jest.fn(), changes: jest.fn() },
}));
const stores = [
  { store_id: 1, store_name: '対象店舗' },
  { store_id: 2, store_name: '別店舗' },
];
const people = [
  { person_id: 11, name: '対象本人' },
  { person_id: 22, name: '別本人' },
];
const statement = {
  ...stores[0],
  ...people[0],
  month: '2026-09',
  total_remuneration: 21000,
  orders: {
    content: [
      {
        order_id: 'original-order',
        business_date: '2026-09-01',
        service_summary: '対象サービス',
        accrued_remuneration: 7000,
        completion_invalidated: false,
      },
    ],
    number: 0,
    size: 1,
    total_elements: 3,
    total_pages: 3,
  },
};
const blob = () => new Blob(['pdf'], { type: 'application/pdf' });
function deferred<T>() {
  let resolve!: (value: T) => void;
  const promise = new Promise<T>(done => {
    resolve = done;
  });
  return { promise, resolve };
}
async function choose(label: string, name: string) {
  fireEvent.click(await screen.findByRole('combobox', { name: label }));
  const option = await screen.findByRole('option', { name: new RegExp(name) });
  fireEvent.pointerDown(option);
  fireEvent.click(option);
}
const scopes = [
  { scope: 'store', Page: MonthlyRemunerationsPage, api: monthlyRemunerationApi },
  {
    scope: 'platform',
    Page: PlatformMonthlyRemunerationsPage,
    api: platformMonthlyRemunerationApi,
  },
  { scope: 'self', Page: CastMonthlyRemunerationsPage, api: selfMonthlyRemunerationApi },
] as const;
beforeEach(() => {
  jest.resetAllMocks();
  URL.createObjectURL = jest.fn(() => 'blob:statement');
  URL.revokeObjectURL = jest.fn();
  jest.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {});
  const storePage = { rows: stores, page: 0, pageCount: 1, total: 2 };
  const personPage = { rows: people, page: 0, pageCount: 1, total: 2 };
  jest.mocked(selfMonthlyRemunerationApi.stores).mockResolvedValue(storePage);
  jest.mocked(platformMonthlyRemunerationApi.stores).mockResolvedValue(storePage);
  jest.mocked(platformMonthlyRemunerationApi.casts).mockResolvedValue(personPage);
  jest.mocked(monthlyRemunerationApi.casts).mockResolvedValue(personPage);
  for (const { api } of scopes) jest.mocked(api.monthly).mockResolvedValue(statement);
});
afterEach(() => jest.restoreAllMocks());

describe.each(scopes)('$scope の月次 PDF ライフサイクル', ({ scope, Page, api }) => {
  async function search() {
    render(<Page />);
    if (scope !== 'store') await choose('店舗', '対象店舗');
    if (scope !== 'self') await choose('キャスト本人', '対象本人');
    fireEvent.change(screen.getByLabelText('対象月'), { target: { value: '2026-09' } });
    fireEvent.click(screen.getByRole('button', { name: '照会' }));
    await screen.findByLabelText('月次報酬合計');
  }
  function next() {
    fireEvent.click(screen.getAllByRole('button', { name: '次へ' })[0]);
  }

  it.each(['download', 'print'] as const)(
    '%s は未適用の月を編集してページ移動しても完了し保存リンクを保持する',
    async mode => {
      await search();
      const pdf = deferred<Blob>();
      jest.mocked(fetchMonthlyPdf).mockReturnValueOnce(pdf.promise);
      const popup = {
        close: jest.fn(),
        closed: false,
        document: { title: '', body: { textContent: '' } },
        location: { replace: jest.fn() },
      };
      jest.spyOn(window, 'open').mockReturnValue(popup as unknown as Window);
      fireEvent.click(
        screen.getByRole('button', {
          name: mode === 'download' ? 'PDF ダウンロード' : 'PDFを開いて印刷',
        })
      );
      const signal = jest.mocked(fetchMonthlyPdf).mock.calls[0][1];
      const page = deferred<typeof statement>();
      jest.mocked(api.monthly).mockReturnValueOnce(page.promise);
      fireEvent.change(screen.getByLabelText('対象月'), { target: { value: '2026-10' } });
      next();
      await waitFor(() => expect(api.monthly).toHaveBeenCalledTimes(2));
      expect(screen.getByText('全月の PDF を生成しています...')).toBeInTheDocument();
      expect(signal.aborted).toBe(false);
      expect(popup.close).not.toHaveBeenCalled();
      await act(async () =>
        page.resolve({ ...statement, orders: { ...statement.orders, number: 1 } })
      );
      expect(signal.aborted).toBe(false);
      await act(async () => pdf.resolve(blob()));
      expect(screen.getByRole('link', { name: '保存する' })).toHaveAttribute(
        'download',
        'monthly-remuneration-2026-09.pdf'
      );
      if (mode === 'print') expect(popup.location.replace).toHaveBeenCalledWith('blob:statement');
      const third = deferred<typeof statement>();
      jest.mocked(api.monthly).mockReturnValueOnce(third.promise);
      next();
      await waitFor(() => expect(api.monthly).toHaveBeenCalledTimes(3));
      expect(screen.getByRole('link', { name: '保存する' })).toBeInTheDocument();
      expect(URL.revokeObjectURL).not.toHaveBeenCalled();
      await act(async () =>
        third.resolve({ ...statement, orders: { ...statement.orders, number: 2 } })
      );
    }
  );

  it.each(['month', 'identity'] as const)(
    '%s を適用した時点で旧要求を中止し遅延応答を破棄する',
    async change => {
      await search();
      const pdf = deferred<Blob>();
      jest.mocked(fetchMonthlyPdf).mockReturnValueOnce(pdf.promise);
      fireEvent.click(screen.getByRole('button', { name: 'PDF ダウンロード' }));
      const signal = jest.mocked(fetchMonthlyPdf).mock.calls[0][1];
      const changed = deferred<typeof statement>();
      jest.mocked(api.monthly).mockReturnValueOnce(changed.promise);
      if (change === 'month')
        fireEvent.change(screen.getByLabelText('対象月'), { target: { value: '2026-10' } });
      else if (scope === 'store') await choose('キャスト本人', '別本人');
      else {
        await choose('店舗', '別店舗');
        if (scope === 'platform') {
          await choose('キャスト本人', '対象本人');
          fireEvent.change(screen.getByLabelText('対象月'), { target: { value: '2026-09' } });
        }
      }
      fireEvent.click(screen.getByRole('button', { name: '照会' }));
      await waitFor(() => expect(api.monthly).toHaveBeenCalledTimes(2));
      expect(signal.aborted).toBe(true);
      expect(screen.queryByRole('button', { name: 'PDF ダウンロード' })).not.toBeInTheDocument();
      await act(async () => pdf.resolve(blob()));
      expect(URL.createObjectURL).not.toHaveBeenCalled();
      await act(async () =>
        changed.resolve({
          ...statement,
          month: change === 'month' ? '2026-10' : statement.month,
          ...(change === 'identity' ? (scope === 'store' ? people[1] : stores[1]) : {}),
        })
      );
      jest.mocked(fetchMonthlyPdf).mockResolvedValueOnce(blob());
      fireEvent.click(await screen.findByRole('button', { name: 'PDF ダウンロード' }));
      await screen.findByRole('link', { name: '保存する' });
      expect(fetchMonthlyPdf).toHaveBeenLastCalledWith(
        expect.objectContaining({
          scope,
          month: change === 'month' ? '2026-10' : '2026-09',
          ...(change === 'identity' ? (scope === 'store' ? { personId: 22 } : { storeId: 2 }) : {}),
        }),
        expect.any(AbortSignal)
      );
    }
  );

  it('照会が閲覧不可になったら旧要求を中止し成果物を残さない', async () => {
    await search();
    const pdf = deferred<Blob>();
    jest.mocked(fetchMonthlyPdf).mockReturnValueOnce(pdf.promise);
    fireEvent.click(screen.getByRole('button', { name: 'PDF ダウンロード' }));
    const signal = jest.mocked(fetchMonthlyPdf).mock.calls[0][1];
    jest.mocked(api.monthly).mockRejectedValueOnce({ response: { status: 403 } });
    next();
    await waitFor(() => expect(signal.aborted).toBe(true));
    expect(screen.queryByRole('button', { name: 'PDF ダウンロード' })).not.toBeInTheDocument();
    await act(async () => pdf.resolve(blob()));
    expect(URL.createObjectURL).not.toHaveBeenCalled();
  });

  it('ページ移動中も明示取消でき、503 と再試行状態を保持して旧応答を破棄する', async () => {
    await search();
    const pdf = deferred<Blob>();
    jest.mocked(fetchMonthlyPdf).mockReturnValueOnce(pdf.promise);
    fireEvent.click(screen.getByRole('button', { name: 'PDF ダウンロード' }));
    const signal = jest.mocked(fetchMonthlyPdf).mock.calls[0][1];
    const page = deferred<typeof statement>();
    jest.mocked(api.monthly).mockReturnValueOnce(page.promise);
    next();
    await waitFor(() => expect(api.monthly).toHaveBeenCalledTimes(2));
    fireEvent.click(screen.getByRole('button', { name: '取得を中止' }));
    expect(signal.aborted).toBe(true);
    jest
      .mocked(fetchMonthlyPdf)
      .mockRejectedValueOnce(new Error('PDF を生成中です。再試行してください'));
    fireEvent.click(screen.getByRole('button', { name: 'PDF ダウンロード' }));
    await screen.findByText('PDF を生成中です。再試行してください');
    await act(async () =>
      page.resolve({ ...statement, orders: { ...statement.orders, number: 1 } })
    );
    expect(screen.getByText('PDF を生成中です。再試行してください')).toBeInTheDocument();
    jest.mocked(fetchMonthlyPdf).mockResolvedValueOnce(blob());
    fireEvent.click(screen.getByRole('button', { name: '再試行' }));
    await screen.findByRole('link', { name: '保存する' });
    await act(async () => pdf.resolve(blob()));
    expect(URL.createObjectURL).toHaveBeenCalledTimes(1);
  });
});
