import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import OrderEditPage from '../ui/OrderEditPage';
import { Order, orderApi } from '@/entities/order';

const mockPush = jest.fn();

jest.mock('@/entities/order', () => ({
  // 種別表などの定数は実物を通す。丸ごと差し替えると明細の欄が選択肢を組めない
  ...jest.requireActual('@/entities/order'),
  orderApi: {
    get: jest.fn(),
    update: jest.fn(),
    listReceptionists: jest.fn(),
    listCastCandidates: jest.fn(),
  },
}));

jest.mock('next/navigation', () => ({
  useRouter: () => ({ push: mockPush, back: jest.fn() }),
  useParams: () => ({ storeId: '1', id: 'o1' }),
}));

jest.mock('@/shared/notify', () => ({
  notify: { success: jest.fn(), error: jest.fn(), warning: jest.fn() },
}));

const mockedOrderApi = orderApi as jest.Mocked<typeof orderApi>;

/** 確定済みの受注 1 件。fixture は手書きで、Order 型との照合は tsc の側で効く（jest は型検査しない）。 */
function confirmedOrder(overrides: Partial<Order> = {}): Order {
  return {
    id: 'o1',
    business_date: '2026-07-03',
    arrival_scheduled_start_time: '19:30:00',
    customer_id: 'c1',
    customer_name: '山田太郎',
    cast_name: '花子',
    receptionist_name: '佐藤',
    pax: 2,
    course_minutes: 60,
    receptionist_id: 3,
    cast_id: 'cast-1',
    status: 'CONFIRMED',
    reception_route: 'PHONE',
    ...overrides,
  };
}

beforeEach(() => {
  jest.clearAllMocks();
  mockedOrderApi.listReceptionists.mockResolvedValue([]);
  mockedOrderApi.listCastCandidates.mockResolvedValue([]);
});

describe('受注の編集ページ', () => {
  it('確定済みの編集は 1 件を読み直し、指名と受付担当を毎回運んで保存すること', async () => {
    mockedOrderApi.get.mockResolvedValue(confirmedOrder());
    mockedOrderApi.update.mockResolvedValue(confirmedOrder({ pax: 5 }));
    render(<OrderEditPage />);

    // 一覧の行を種にすると、他の操作者が直した後の画面で陳腐化した値を送り返す
    await waitFor(() => expect(mockedOrderApi.get).toHaveBeenCalledWith('o1'));
    await waitFor(() => expect(screen.getByLabelText('人数')).toHaveValue(2));

    fireEvent.change(screen.getByLabelText('人数'), { target: { value: '5' } });
    fireEvent.click(screen.getByRole('button', { name: '保存' }));

    await waitFor(() => expect(mockedOrderApi.update).toHaveBeenCalled());
    // 触った欄と、省略が「外す」と区別できない 2 項目だけ。全項目を毎回運ぶと、この画面を開いている
    // 間に別の操作者が直した受注へ、触ってもいない項目を開いた時点の値で押し戻してしまう
    const [, body] = mockedOrderApi.update.mock.calls[0];
    expect(Object.keys(body).sort()).toEqual(['cast_id', 'pax', 'receptionist_id']);
    expect(body).toEqual({ pax: 5, receptionist_id: 3, cast_id: 'cast-1' });
    // 保存し終えたこの頁に留まる理由は無い。出口は一覧
    await waitFor(() => expect(mockPush).toHaveBeenCalledWith('/store/1/orders'));
  });

  it('キャンセルは保存せずに一覧へ戻ること', async () => {
    mockedOrderApi.get.mockResolvedValue(confirmedOrder());
    render(<OrderEditPage />);
    await waitFor(() => expect(screen.getByLabelText('人数')).toHaveValue(2));

    fireEvent.change(screen.getByLabelText('人数'), { target: { value: '5' } });
    fireEvent.click(screen.getByRole('button', { name: 'キャンセル' }));

    expect(mockedOrderApi.update).not.toHaveBeenCalled();
    expect(mockPush).toHaveBeenCalledWith('/store/1/orders');
  });

  it('文字列の欄は空にした結果も送ること（空文字が「空にする」の表し方）', async () => {
    mockedOrderApi.get.mockResolvedValue(confirmedOrder({ remarks: '消したい備考' }));
    mockedOrderApi.update.mockResolvedValue(confirmedOrder());
    render(<OrderEditPage />);

    await waitFor(() => expect(screen.getByLabelText('備考')).toHaveValue('消したい備考'));

    fireEvent.change(screen.getByLabelText('備考'), { target: { value: '' } });
    fireEvent.click(screen.getByRole('button', { name: '保存' }));

    // 項目ごと落とすと、消したはずの備考が残る（サーバは送られない項目を「変更しない」と読む）
    await waitFor(() =>
      expect(mockedOrderApi.update).toHaveBeenCalledWith(
        'o1',
        expect.objectContaining({ remarks: '' })
      )
    );
  });

  it('取得が終わるまでフォームを出さないこと', async () => {
    // 取得の到着はレンダーより後。播く前に欄を出すと、空欄のまま保存して内容を消せてしまう
    mockedOrderApi.get.mockReturnValue(new Promise<Order>(() => {}));
    render(<OrderEditPage />);

    expect(await screen.findByText('読み込み中...')).toBeInTheDocument();
    expect(screen.queryByLabelText('人数')).not.toBeInTheDocument();
  });

  it('404 では再試行を出さず、一覧への導線だけを出すこと', async () => {
    mockedOrderApi.get.mockRejectedValue({ response: { status: 404 } });
    render(<OrderEditPage />);

    const region = await screen.findByRole('alert');
    expect(within(region).getByText('この受注は見つかりませんでした。')).toBeInTheDocument();
    // 何度押しても取れないものを押させない
    expect(within(region).queryByRole('button', { name: '再試行' })).not.toBeInTheDocument();
    expect(within(region).getByRole('link', { name: 'オーダー一覧へ' })).toHaveAttribute(
      'href',
      '/store/1/orders'
    );
  });

  it('顧客の着いた受注では連絡先を編集させず、顧客詳細への導線を出すこと', async () => {
    mockedOrderApi.get.mockResolvedValue(confirmedOrder());
    render(<OrderEditPage />);

    // 受注 1 件を直したつもりの変更が同じ顧客の他の受注へ波及しないため、台帳の項目は読み取り
    await waitFor(() =>
      expect(screen.getByRole('link', { name: /顧客詳細を開く/ })).toHaveAttribute(
        'href',
        '/store/1/customers/c1'
      )
    );
    expect(screen.queryByLabelText('電話番号')).not.toBeInTheDocument();
  });

  it('顧客の着いていない受注では連絡先を訂正でき、その 2 項目を送ること', async () => {
    const unlinked = confirmedOrder({
      customer_id: undefined,
      customer_name: undefined,
      contact_name: '誤記の名前',
    });
    mockedOrderApi.get.mockResolvedValue(unlinked);
    mockedOrderApi.update.mockResolvedValue(unlinked);
    render(<OrderEditPage />);

    await waitFor(() => expect(screen.getByLabelText('お客様名')).toHaveValue('誤記の名前'));

    fireEvent.change(screen.getByLabelText('お客様名'), { target: { value: '正しい名前' } });
    fireEvent.click(screen.getByRole('button', { name: '保存' }));

    await waitFor(() =>
      expect(mockedOrderApi.update).toHaveBeenCalledWith(
        'o1',
        expect.objectContaining({ contact_name: '正しい名前' })
      )
    );
  });
});
