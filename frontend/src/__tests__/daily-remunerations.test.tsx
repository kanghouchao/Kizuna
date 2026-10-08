import { act, fireEvent, render, screen, waitFor } from '@testing-library/react';
import MonthlyRemunerationsPage from '@/_pages/store-monthly-remunerations/ui/MonthlyRemunerationsPage';
import { CastMonthlyRemunerationsPage } from '@/_pages/cast-portal/ui/CastMonthlyRemunerationsPage';
import {
  dailyRemunerationApi,
  monthlyRemunerationApi,
  selfMonthlyRemunerationApi,
} from '@/entities/order';

jest.mock('next/navigation', () => ({ useParams: () => ({ storeId: '1' }) }));
jest.mock('@/entities/order', () => ({
  ...jest.requireActual('@/entities/order'),
  dailyRemunerationApi: { store: jest.fn(), self: jest.fn() },
  monthlyRemunerationApi: { monthly: jest.fn() },
  selfMonthlyRemunerationApi: { monthly: jest.fn() },
}));
jest.mock('@/features/monthly-remuneration-pdf', () => ({
  MonthlyPdfActions: () => <button>月次PDF</button>,
}));
jest.mock('@/widgets/order-correction-history', () => ({
  OrderCorrectionHistoryModal: ({ orderId }: { orderId: string }) => (
    <div role="dialog">{orderId}</div>
  ),
}));
jest.mock('@/_pages/cast-portal/ui/RemunerationDetail', () => ({
  RemunerationDetail: ({ id, onBack }: { id: string; onBack: () => void }) => (
    <div role="dialog">
      {id}
      <button onClick={onBack}>一覧へ戻る</button>
    </div>
  ),
}));
jest.mock('@/_pages/store-monthly-remunerations/ui/PersonPicker', () => ({
  PersonPicker: ({ onChange }: { onChange: (value: unknown) => void }) => (
    <select
      aria-label="対象人物"
      onChange={e => onChange({ person_id: Number(e.target.value), name: `本人${e.target.value}` })}
    >
      <option value="">選択</option>
      <option value="1">本人1</option>
      <option value="2">本人2</option>
    </select>
  ),
}));
jest.mock('@/_pages/cast-portal/ui/RemunerationStorePicker', () => ({
  RemunerationStorePicker: ({ onChange }: { onChange: (value: unknown) => void }) => (
    <select
      aria-label="対象店舗"
      onChange={e =>
        onChange({ store_id: Number(e.target.value), store_name: `店舗${e.target.value}` })
      }
    >
      <option value="">選択</option>
      <option value="1">店舗1</option>
      <option value="2">店舗2</option>
    </select>
  ),
}));

const row = {
  order_id: 'old-order',
  business_date: '2026-09-30',
  service_summary: '保存されたサービス',
  accrued_remuneration: 7000,
  completion_invalidated: false,
};
const base = {
  person_id: 1,
  name: '本人1',
  store_id: 1,
  store_name: '店舗1',
  total_remuneration: 14000,
  orders: { content: [row], number: 0, size: 1, total_elements: 2, total_pages: 2 },
};
const daily = { ...base, business_date: '2026-09-30' };
const monthly = { ...base, month: '2026-09' };

function deferred<T>() {
  let resolve!: (value: T) => void;
  const promise = new Promise<T>(done => {
    resolve = done;
  });
  return { promise, resolve };
}

beforeEach(() => {
  jest.resetAllMocks();
  jest.mocked(dailyRemunerationApi.store).mockResolvedValue(daily);
  jest.mocked(dailyRemunerationApi.self).mockResolvedValue(daily);
  jest.mocked(monthlyRemunerationApi.monthly).mockResolvedValue(monthly);
  jest.mocked(selfMonthlyRemunerationApi.monthly).mockResolvedValue(monthly);
});

for (const self of [false, true]) {
  const scope = self ? '本人' : '店舗';
  const api = () => jest.mocked(self ? dailyRemunerationApi.self : dailyRemunerationApi.store);
  const renderPage = () =>
    render(self ? <CastMonthlyRemunerationsPage /> : <MonthlyRemunerationsPage />);
  const select = (id: string) =>
    fireEvent.change(screen.getByLabelText(self ? '対象店舗' : '対象人物'), {
      target: { value: id },
    });
  const submit = () => fireEvent.click(screen.getByRole('button', { name: '照会' }));
  async function searchDay() {
    fireEvent.click(screen.getByRole('button', { name: '日別' }));
    select('1');
    fireEvent.change(screen.getByLabelText('営業日'), { target: { value: '2026-09-30' } });
    submit();
    await screen.findByLabelText('日別報酬合計');
  }

  it(`${scope}: 日別の合計とページング、月別へ戻るとPDFを表示する`, async () => {
    renderPage();
    await searchDay();
    expect(screen.getByLabelText('日別報酬合計')).toHaveTextContent('¥14,000');
    expect(screen.queryByText('月次PDF')).not.toBeInTheDocument();
    fireEvent.click(screen.getAllByRole('button', { name: '次へ' })[0]);
    await waitFor(() => expect(api()).toHaveBeenLastCalledWith(1, '2026-09-30', 1));
    fireEvent.click(screen.getByRole('button', { name: '月別' }));
    expect(screen.queryByLabelText('日別報酬合計')).not.toBeInTheDocument();
    fireEvent.change(screen.getByLabelText('対象月'), { target: { value: '2026-09' } });
    submit();
    await screen.findByText('月次PDF');
  });

  it(`${scope}: 古い日別応答を日付・対象・モード切替後に表示しない`, async () => {
    const pending = deferred<typeof daily>();
    api().mockImplementationOnce(() => pending.promise);
    renderPage();
    fireEvent.click(screen.getByRole('button', { name: '日別' }));
    select('1');
    fireEvent.change(screen.getByLabelText('営業日'), { target: { value: '2026-09-30' } });
    submit();
    await waitFor(() => expect(api()).toHaveBeenCalledTimes(1));
    select('2');
    fireEvent.change(screen.getByLabelText('営業日'), { target: { value: '2026-10-01' } });
    api().mockResolvedValue({
      ...daily,
      person_id: 2,
      store_id: 2,
      business_date: '2026-10-01',
      total_remuneration: 21000,
    });
    submit();
    await screen.findByLabelText('日別報酬合計');
    await act(async () => pending.resolve(daily));
    expect(screen.getByLabelText('日別報酬合計')).toHaveTextContent('¥21,000');
    expect(api()).toHaveBeenLastCalledWith(2, '2026-10-01', 0);
    fireEvent.click(screen.getByRole('button', { name: '月別' }));
    expect(screen.queryByText('保存されたサービス')).not.toBeInTheDocument();
    expect(screen.queryByLabelText('日別報酬合計')).not.toBeInTheDocument();
  });

  it(`${scope}: 条件の編集で旧結果を隠し、新条件の失敗から再試行する`, async () => {
    renderPage();
    await searchDay();
    fireEvent.change(screen.getByLabelText('営業日'), { target: { value: '2026-10-01' } });
    expect(screen.queryByLabelText('日別報酬合計')).not.toBeInTheDocument();
    api().mockRejectedValueOnce(new Error('offline'));
    submit();
    await screen.findByText('日別給与明細を取得できませんでした。');
    fireEvent.click(screen.getByRole('button', { name: /再試行/ }));
    await screen.findByLabelText('日別報酬合計');
    expect(api()).toHaveBeenLastCalledWith(1, '2026-10-01', 0);
  });

  it(`${scope}: 不正な日付は送信しない`, async () => {
    renderPage();
    fireEvent.click(screen.getByRole('button', { name: '日別' }));
    select('1');
    fireEvent.change(screen.getByLabelText('営業日'), { target: { value: '2026-02-30' } });
    submit();
    await screen.findByText('営業日は YYYY-MM-DD 形式の有効な日付で入力してください');
    expect(api()).not.toHaveBeenCalled();
  });
}
