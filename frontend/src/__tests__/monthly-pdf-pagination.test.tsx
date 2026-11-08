import { useMe } from '@/entities/user';
import type { PermissionCode } from '@/entities/user';
import { act, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MonthlyRemunerationsPage } from '@/_pages/store-monthly-remunerations';
import { CastMonthlyRemunerationsPage } from '@/_pages/cast-portal/ui/CastMonthlyRemunerationsPage';
import {
  fetchMonthlyPdf,
  monthlyRemunerationApi,
  selfMonthlyRemunerationApi,
} from '@/entities/order';
import { tokyoDate } from '@/shared/lib';
import { remunerationApi, type Statement } from '@/widgets/remuneration-statement';

// 本人の月次は店舗と月を URL に持つ。jsdom の履歴を URL の正本にし、pushState を描画へ知らせる
const mockQueryListeners = new Set<() => void>();
const originalPushState = window.history.pushState.bind(window.history);
window.history.pushState = (...args: Parameters<History['pushState']>) => {
  originalPushState(...args);
  mockQueryListeners.forEach(listener => listener());
};
jest.mock('next/navigation', () => {
  const React = jest.requireActual('react');
  return {
    ...jest.requireActual('next/navigation'),
    useSearchParams: () => {
      const query = React.useSyncExternalStore(
        (listener: () => void) => {
          mockQueryListeners.add(listener);
          return () => mockQueryListeners.delete(listener);
        },
        () => window.location.search.slice(1)
      );
      return React.useMemo(() => new URLSearchParams(query), [query]);
    },
    usePathname: () => '/cast/remunerations/monthly',
  };
});
jest.mock('@/entities/user', () => ({
  ...jest.requireActual('@/entities/user'),
  useStoreContext: () => ({
    stores: [{ id: 1, name: 'テスト店舗' }],
    currentStoreId: '1',
    currentStore: { id: 1, name: 'テスト店舗' },
    loadFailed: false,
    reload: jest.fn(),
    switchStore: jest.fn(),
  }),
}));
jest.mock('@/entities/user/model/MeContext', () => ({
  ...jest.requireActual('@/entities/user/model/MeContext'),
  useMe: jest.fn(),
}));
const mockedUseMe = useMe as jest.MockedFunction<typeof useMe>;
const meWith = (permissions: PermissionCode[]) =>
  mockedUseMe.mockReturnValue({
    me: {
      email: 'staff@example.com',
      user_type: 'STAFF',
      permissions,
      line_linked: false,
      email_verified: false,
    },
    isLoading: false,
    failure: null,
    reload: jest.fn(),
    setMe: jest.fn(),
  });
const grantMe = () =>
  meWith([
    'ORDER_MANAGE',
    'REMUNERATION_VIEW',
    'GUARANTEE_MANAGE',
    'BONUS_AWARD',
    'REMUNERATION_CORRECT',
  ]);
jest.mock('@/entities/order', () => ({
  ...jest.requireActual('@/entities/order'),
  fetchMonthlyPdf: jest.fn(),
  monthlyRemunerationApi: { casts: jest.fn(), monthly: jest.fn() },
  selfMonthlyRemunerationApi: { stores: jest.fn() },
}));
jest.mock('@/widgets/remuneration-statement', () => {
  const actual = jest.requireActual('@/widgets/remuneration-statement');
  return {
    ...actual,
    remunerationApi: { ...actual.remunerationApi, statement: jest.fn(), accept: jest.fn() },
  };
});
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
] as const;
beforeEach(() => {
  jest.resetAllMocks();
  window.history.replaceState(null, '', '/');
  grantMe();
  URL.createObjectURL = jest.fn(() => 'blob:statement');
  URL.revokeObjectURL = jest.fn();
  jest.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {});
  const storePage = { rows: stores, page: 0, pageCount: 1, total: 2 };
  const personPage = { rows: people, page: 0, pageCount: 1, total: 2 };
  jest.mocked(selfMonthlyRemunerationApi.stores).mockResolvedValue(storePage);
  jest.mocked(monthlyRemunerationApi.casts).mockResolvedValue(personPage);
  for (const { api } of scopes) jest.mocked(api.monthly).mockResolvedValue(statement);
  jest
    .mocked(remunerationApi.statement)
    .mockImplementation(async (scope, month) =>
      selfStatement('storeId' in scope ? scope.storeId : 0, month)
    );
});
afterEach(() => jest.restoreAllMocks());

describe.each(scopes)('$scope の月次 PDF ライフサイクル', ({ scope, Page, api }) => {
  async function search() {
    render(<Page />);
    await choose('キャスト本人', '対象本人');
    fireEvent.change(screen.getByLabelText('対象月'), { target: { value: '2026-09' } });
    fireEvent.click(screen.getByRole('button', { name: '照会' }));
    await screen.findByLabelText('月次報酬合計');
  }
  function next() {
    fireEvent.click(screen.getAllByRole('button', { name: '次へ' })[0]);
  }

  it.each(['download', 'print'] as const)(
    '%s は同じ対象のページ移動でも完了し保存リンクを保持する',
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
      else await choose('キャスト本人', '別本人');
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
          ...(change === 'identity' ? people[1] : {}),
        })
      );
      jest.mocked(fetchMonthlyPdf).mockResolvedValueOnce(blob());
      fireEvent.click(await screen.findByRole('button', { name: 'PDF ダウンロード' }));
      await screen.findByRole('link', { name: '保存する' });
      expect(fetchMonthlyPdf).toHaveBeenLastCalledWith(
        expect.objectContaining({
          scope,
          month: change === 'month' ? '2026-10' : '2026-09',
          ...(change === 'identity' ? { personId: 22 } : {}),
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

const thisMonth = tokyoDate(new Date()).slice(0, 7);
function selfStatement(storeId: number, month: string): Statement {
  const empty = { content: [], number: 0, size: 100, total_elements: 0, total_pages: 0 };
  return {
    store_id: storeId,
    store_name: stores.find(store => store.store_id === storeId)?.store_name ?? '',
    person_id: 11,
    name: '対象本人',
    month,
    generated_at: `${month}-01T00:00:00Z`,
    order_total: 7000,
    known_guarantee_total: 0,
    guarantee_total: 0,
    bonus_total: 0,
    total: 7000,
    accepted_days: 0,
    acceptable_days: 1,
    days: [
      {
        business_date: `${month}-01`,
        order_amount: 7000,
        closed_duration: 'PT0S',
        attendance_incomplete: false,
        guarantee_status: 'NOT_ELIGIBLE',
        guarantee_amount: 0,
        bonus_amount: 0,
        acceptance_state: 'NOT_ACCEPTED',
        content_digest: 'digest',
      },
    ],
    orders: {
      ...empty,
      content: [
        {
          order_id: 'self-order',
          business_date: `${month}-01`,
          service_summary: '対象サービス',
          accrued_remuneration: 7000,
          completion_invalidated: false,
        },
      ],
      total_elements: 1,
      total_pages: 1,
    },
    bonus_awards: empty,
  };
}

describe('本人の月次 PDF ライフサイクル', () => {
  async function startPdf() {
    render(<CastMonthlyRemunerationsPage />);
    const pdf = deferred<Blob>();
    jest.mocked(fetchMonthlyPdf).mockReturnValueOnce(pdf.promise);
    fireEvent.click(await screen.findByRole('button', { name: 'PDF ダウンロード' }));
    expect(fetchMonthlyPdf).toHaveBeenLastCalledWith(
      { scope: 'self', storeId: 1, month: thisMonth },
      expect.any(AbortSignal)
    );
    return { pdf, signal: jest.mocked(fetchMonthlyPdf).mock.calls[0][1] };
  }

  it.each(['month', 'store'] as const)(
    '%s を切り替えた時点で旧要求を中止し遅延応答を破棄する',
    async change => {
      const { pdf, signal } = await startPdf();
      if (change === 'month') fireEvent.click(screen.getByRole('button', { name: '前の月' }));
      else fireEvent.click(screen.getByRole('button', { name: '別店舗' }));
      expect(signal.aborted).toBe(true);
      await act(async () => pdf.resolve(blob()));
      expect(URL.createObjectURL).not.toHaveBeenCalled();
      jest.mocked(fetchMonthlyPdf).mockResolvedValueOnce(blob());
      fireEvent.click(await screen.findByRole('button', { name: 'PDF ダウンロード' }));
      await screen.findByRole('link', { name: '保存する' });
      expect(fetchMonthlyPdf).toHaveBeenLastCalledWith(
        expect.objectContaining(
          change === 'month' ? { storeId: 1 } : { storeId: 2, month: thisMonth }
        ),
        expect.any(AbortSignal)
      );
      if (change === 'month')
        expect(jest.mocked(fetchMonthlyPdf).mock.lastCall?.[0].month).not.toBe(thisMonth);
    }
  );

  it('承諾で明細を取り直しても同じ月の PDF の生成は続く', async () => {
    jest.mocked(remunerationApi.accept).mockResolvedValue({
      id: 'acc',
      accepted_at: `${thisMonth}-02T00:00:00Z`,
      order_amount: 7000,
      guarantee_amount: 0,
      bonus_amount: 0,
    });
    const { pdf, signal } = await startPdf();
    fireEvent.click(screen.getByRole('button', { name: /を承諾する$/ }));
    await waitFor(() => expect(remunerationApi.statement).toHaveBeenCalledTimes(2));
    expect(signal.aborted).toBe(false);
    await act(async () => pdf.resolve(blob()));
    expect(screen.getByRole('link', { name: '保存する' })).toBeInTheDocument();
  });

  it('明細を取り直せなくなったら旧要求を中止し成果物を残さない', async () => {
    jest.mocked(remunerationApi.accept).mockRejectedValue(new Error('network'));
    const { pdf, signal } = await startPdf();
    jest.mocked(remunerationApi.statement).mockRejectedValueOnce({ response: { status: 403 } });
    fireEvent.click(screen.getByRole('button', { name: /を承諾する$/ }));
    await waitFor(() => expect(signal.aborted).toBe(true));
    expect(screen.queryByRole('button', { name: 'PDF ダウンロード' })).not.toBeInTheDocument();
    await act(async () => pdf.resolve(blob()));
    expect(URL.createObjectURL).not.toHaveBeenCalled();
  });
});
