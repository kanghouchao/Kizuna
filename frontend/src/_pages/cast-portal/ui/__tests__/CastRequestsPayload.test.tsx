import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { CastRequestsPage } from '../CastRequestsPage';
import { shiftApi } from '@/entities/shift';

jest.mock('@/entities/shift', () => ({
  shiftApi: {
    myStores: jest.fn(),
    myShiftRequests: jest.fn(),
    submitShiftRequest: jest.fn(),
  },
}));

const mockedMyStores = shiftApi.myStores as jest.Mock;
const mockedMyShiftRequests = shiftApi.myShiftRequests as jest.Mock;
const mockedSubmit = shiftApi.submitShiftRequest as jest.Mock;

const STORES = [
  { store_id: 1, store_name: '店舗A' },
  { store_id: 2, store_name: '店舗B' },
];

/** 提出フォームの初期日付（明日）をローカルタイム基準で組み立てる。 */
function tomorrowStr(): string {
  const d = new Date();
  d.setDate(d.getDate() + 1);
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(
    d.getDate()
  ).padStart(2, '0')}`;
}

/**
 * 店舗セレクタ。native <select> と shadcn Select のどちらでも role=combobox かつ
 * アクセシブル名「店舗」で引けるため、DOM 形状の差だけを吸収して操作の意味は同一に保つ。
 */
function storeCombobox(): Promise<HTMLElement> {
  return screen.findByRole('combobox', { name: '店舗' });
}

/**
 * 所属店舗の読み込み完了を待つ。Select は form 内で隠し input を併走させ
 * 同じ店舗名を選択中ラベルと option の二箇所に描くため、件数を問わない findAll で待つ。
 */
function waitStoresLoaded() {
  return screen.findAllByText('店舗A');
}

/** 店舗を選ぶ。native は値変更、shadcn Select は開いて項目を押す。 */
async function pickStore(name: string, value: string) {
  const combobox = await storeCombobox();
  if (combobox instanceof HTMLSelectElement) {
    fireEvent.change(combobox, { target: { value } });
    return;
  }
  fireEvent.click(combobox);
  const option = await screen.findByRole('option', { name });
  // Base UI の Item は pointerdown を経ていない mouse click を無視する
  fireEvent.pointerDown(option);
  fireEvent.click(option);
}

/** セレクタが今どの店舗を表示しているか。閉じた状態でのみ呼ぶこと。 */
async function selectedStoreLabel(): Promise<string> {
  const combobox = await storeCombobox();
  if (combobox instanceof HTMLSelectElement) {
    return combobox.options[combobox.selectedIndex]?.textContent ?? '';
  }
  return combobox.textContent ?? '';
}

function submitForm() {
  fireEvent.click(screen.getByRole('button', { name: '提出する' }));
}

describe('出勤希望フォームの店舗セレクト配線と送信ペイロード', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    mockedMyStores.mockResolvedValue(STORES);
    mockedMyShiftRequests.mockResolvedValue({ rows: [], nextCursor: null });
    mockedSubmit.mockResolvedValue({ id: 'sr1', status: 'PENDING' });
  });

  it('未操作の既定値がキー集合ごとそのまま送られること', async () => {
    render(<CastRequestsPage />);
    await waitStoresLoaded();

    submitForm();

    await waitFor(() => expect(mockedSubmit).toHaveBeenCalledTimes(1));
    const body = mockedSubmit.mock.calls[0][0];
    // note のキーは値が undefined でも残る。toStrictEqual はキーの欠落を検出する。
    expect(body).toStrictEqual({
      store_id: 1,
      work_date: tomorrowStr(),
      start_time: '18:00:00',
      end_time: '23:00:00',
      note: undefined,
    });
    expect(typeof body.store_id).toBe('number');
  });

  it('店舗を切り替えると選んだ店舗 ID が数値で送られること', async () => {
    render(<CastRequestsPage />);
    await waitStoresLoaded();

    await pickStore('店舗B', '2');
    submitForm();

    await waitFor(() => expect(mockedSubmit).toHaveBeenCalledTimes(1));
    expect(mockedSubmit.mock.calls[0][0].store_id).toBe(2);
  });

  it('セレクタの表示が選択中の店舗に追従すること', async () => {
    render(<CastRequestsPage />);
    await waitStoresLoaded();
    expect(await selectedStoreLabel()).toBe('店舗A');

    await pickStore('店舗B', '2');

    await waitFor(async () => expect(await selectedStoreLabel()).toBe('店舗B'));
  });

  it('備考は入力された文字列がそのまま載ること', async () => {
    render(<CastRequestsPage />);
    await waitStoresLoaded();

    fireEvent.change(screen.getByLabelText('備考'), { target: { value: '遅れます' } });
    submitForm();

    await waitFor(() => expect(mockedSubmit).toHaveBeenCalledTimes(1));
    expect(mockedSubmit.mock.calls[0][0].note).toBe('遅れます');
  });

  it('提出後は選択店舗を保ったまま日時だけ既定へ戻ること', async () => {
    render(<CastRequestsPage />);
    await waitStoresLoaded();

    await pickStore('店舗B', '2');
    fireEvent.change(screen.getByLabelText('開始'), { target: { value: '20:00' } });
    submitForm();

    await waitFor(() => expect(mockedSubmit).toHaveBeenCalledTimes(1));
    expect(mockedSubmit.mock.calls[0][0]).toMatchObject({
      store_id: 2,
      start_time: '20:00:00',
    });

    await waitFor(() => expect(screen.getByLabelText('開始')).toHaveValue('18:00'));
    expect(await selectedStoreLabel()).toBe('店舗B');

    submitForm();

    await waitFor(() => expect(mockedSubmit).toHaveBeenCalledTimes(2));
    expect(mockedSubmit.mock.calls[1][0]).toStrictEqual({
      store_id: 2,
      work_date: tomorrowStr(),
      start_time: '18:00:00',
      end_time: '23:00:00',
      note: undefined,
    });
  });

  it('所属店舗が無いときは案内を出し提出させないこと', async () => {
    mockedMyStores.mockResolvedValue([]);
    render(<CastRequestsPage />);
    await waitFor(() => expect(mockedMyStores).toHaveBeenCalledTimes(1));

    expect(await screen.findByText('所属店舗がありません')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '提出する' })).toBeDisabled();

    submitForm();

    await waitFor(() => expect(mockedMyShiftRequests).toHaveBeenCalledTimes(1));
    expect(mockedSubmit).not.toHaveBeenCalled();
  });
});
