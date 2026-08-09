import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemberReservationNewPage } from '../MemberReservationNewPage';
import { memberOrderApi } from '@/entities/order';
import { shiftApi } from '@/entities/shift';
import { platformStoreApi } from '@/entities/store';

const mockPush = jest.fn();
let searchParams = new URLSearchParams();

jest.mock('next/navigation', () => ({
  useRouter: () => ({ push: mockPush }),
  useSearchParams: () => searchParams,
}));

jest.mock('@/entities/order', () => ({
  memberOrderApi: { create: jest.fn() },
}));
jest.mock('@/entities/shift', () => ({
  shiftApi: { confirmedCasts: jest.fn() },
}));
jest.mock('@/entities/store', () => ({
  platformStoreApi: { lookupByDomain: jest.fn() },
}));

const mockedLookup = platformStoreApi.lookupByDomain as jest.Mock;
const mockedCasts = shiftApi.confirmedCasts as jest.Mock;
const mockedCreate = memberOrderApi.create as jest.Mock;

const submitButton = () => screen.getByRole('button', { name: 'この内容で申請する' });

describe('MemberReservationNewPage', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    searchParams = new URLSearchParams('store=store1.kizuna.test');
    mockedCasts.mockResolvedValue([]);
  });

  it('公式サイトから引き継いだドメインを照会して店舗をプリセレクトする', async () => {
    mockedLookup.mockResolvedValue({ id: '1', name: 'サンプル店舗' });

    render(<MemberReservationNewPage />);

    expect(await screen.findByText('サンプル店舗')).toBeInTheDocument();
    expect(mockedLookup).toHaveBeenCalledWith('store1.kizuna.test');
  });

  it('店舗を特定できないときはフォームを出さず案内だけ表示する', async () => {
    mockedLookup.mockRejectedValue({ response: { status: 404 } });

    render(<MemberReservationNewPage />);

    expect(
      await screen.findByText(
        '店舗が特定できませんでした。店舗公式サイトの予約ボタンからお進みください。'
      )
    ).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'この内容で申請する' })).not.toBeInTheDocument();
  });

  it('照会自体の失敗は「店舗が無い」と区別し、再読み込みで復帰できる', async () => {
    // 瞬断を 404 と同じ案内に見せると、正しい導線から来た会員が再試行できないまま行き止まる
    mockedLookup.mockRejectedValueOnce(new Error('network'));
    mockedLookup.mockResolvedValueOnce({ id: '1', name: 'サンプル店舗' });

    render(<MemberReservationNewPage />);

    expect(await screen.findByText('店舗情報を取得できませんでした。')).toBeInTheDocument();
    expect(
      screen.queryByText(
        '店舗が特定できませんでした。店舗公式サイトの予約ボタンからお進みください。'
      )
    ).not.toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: '再読み込み' }));

    expect(await screen.findByRole('button', { name: 'この内容で申請する' })).toBeInTheDocument();
  });

  it('店舗パラメータが変わったら、照会が終わるまで前の店舗のフォームを残さない', async () => {
    mockedLookup.mockResolvedValueOnce({ id: '1', name: 'サンプル店舗' });

    const { rerender } = render(<MemberReservationNewPage />);
    expect(await screen.findByRole('button', { name: 'この内容で申請する' })).toBeInTheDocument();

    // 次の照会は解決させない（照会中の相を保ったまま観測する）
    mockedLookup.mockReturnValueOnce(new Promise(() => {}));
    searchParams = new URLSearchParams('store=store2.kizuna.test');
    rerender(<MemberReservationNewPage />);

    await waitFor(() =>
      expect(screen.queryByRole('button', { name: 'この内容で申請する' })).not.toBeInTheDocument()
    );
    expect(screen.getByText('読み込み中...')).toBeInTheDocument();
  });

  it('店舗パラメータが無いときも案内だけ表示する', async () => {
    searchParams = new URLSearchParams();

    render(<MemberReservationNewPage />);

    expect(
      await screen.findByText(
        '店舗が特定できませんでした。店舗公式サイトの予約ボタンからお進みください。'
      )
    ).toBeInTheDocument();
    expect(mockedLookup).not.toHaveBeenCalled();
  });

  it('利用日を選ぶとその日の確定シフトのキャストを指名候補に出す', async () => {
    mockedLookup.mockResolvedValue({ id: '1', name: 'サンプル店舗' });
    mockedCasts.mockResolvedValue([{ cast_id: 'c1', cast_name: 'さくら', start_time: '18:00:00' }]);

    render(<MemberReservationNewPage />);

    fireEvent.change(await screen.findByLabelText('利用日'), { target: { value: '2026-08-10' } });

    await waitFor(() =>
      expect(mockedCasts).toHaveBeenCalledWith({ store_id: 1, date: '2026-08-10' })
    );
    expect(await screen.findByRole('option', { name: 'さくら（18:00〜）' })).toBeInTheDocument();
  });

  it('照会で得た店舗 ID と人数を添えて申請する', async () => {
    mockedLookup.mockResolvedValue({ id: '7', name: 'サンプル店舗' });
    mockedCreate.mockResolvedValue({});

    render(<MemberReservationNewPage />);

    fireEvent.change(await screen.findByLabelText('利用日'), { target: { value: '2026-08-10' } });
    await waitFor(() => expect(submitButton()).toBeEnabled());
    const pax = screen.getByLabelText('人数');
    fireEvent.change(pax, { target: { value: '' } });
    fireEvent.change(pax, { target: { value: '3' } });
    fireEvent.click(screen.getByRole('button', { name: 'この内容で申請する' }));

    await waitFor(() =>
      expect(mockedCreate).toHaveBeenCalledWith(
        expect.objectContaining({ store_id: 7, business_date: '2026-08-10', pax: 3 })
      )
    );
    expect(mockPush).toHaveBeenCalledWith('/member/reservations/');
  });

  it('人数が未入力なら申請せずに検証エラーを出す', async () => {
    mockedLookup.mockResolvedValue({ id: '1', name: 'サンプル店舗' });

    render(<MemberReservationNewPage />);

    fireEvent.change(await screen.findByLabelText('利用日'), { target: { value: '2026-08-10' } });
    await waitFor(() => expect(submitButton()).toBeEnabled());
    fireEvent.change(screen.getByLabelText('人数'), { target: { value: '' } });
    fireEvent.click(submitButton());

    expect(await screen.findByText('人数を入力してください')).toBeInTheDocument();
    expect(mockedCreate).not.toHaveBeenCalled();
  });

  it('出勤情報の取得に失敗したら「出勤なし」ではなくエラーを出し、再読み込みで復帰できる', async () => {
    // 失敗を空候補に見せると、指名するつもりの会員が誤った空き情報のまま指名なしで申請してしまう
    mockedLookup.mockResolvedValue({ id: '1', name: 'サンプル店舗' });
    mockedCasts.mockRejectedValueOnce(new Error('network'));
    mockedCasts.mockResolvedValueOnce([
      { cast_id: 'c1', cast_name: 'さくら', start_time: '18:00:00' },
    ]);

    render(<MemberReservationNewPage />);

    fireEvent.change(await screen.findByLabelText('利用日'), { target: { value: '2026-08-10' } });

    expect(await screen.findByText('出勤情報を取得できませんでした。')).toBeInTheDocument();
    expect(screen.queryByText('この日に出勤予定のキャストはいません。')).not.toBeInTheDocument();

    // 誰が出勤するか不明のまま送信できると、指名するつもりの会員が「指名なし」で申請してしまう
    expect(submitButton()).toBeDisabled();

    fireEvent.click(screen.getByRole('button', { name: '再読み込み' }));

    expect(await screen.findByRole('option', { name: 'さくら（18:00〜）' })).toBeInTheDocument();
    expect(screen.queryByText('出勤情報を取得できませんでした。')).not.toBeInTheDocument();
    // 再読み込みが通れば送信できる（失敗が行き止まりにならない）
    expect(submitButton()).toBeEnabled();
  });

  it('指名候補の取得中は「出勤なし」を出さず、送信も抑止する', async () => {
    // 未解決のまま空表示にすると、指名するつもりの会員が候補を見ないまま指名なしで申請できてしまう
    mockedLookup.mockResolvedValue({ id: '1', name: 'サンプル店舗' });
    mockedCasts.mockReturnValue(new Promise(() => {}));

    render(<MemberReservationNewPage />);

    fireEvent.change(await screen.findByLabelText('利用日'), { target: { value: '2026-08-10' } });

    expect(await screen.findByText('出勤情報を確認しています...')).toBeInTheDocument();
    expect(screen.queryByText('この日に出勤予定のキャストはいません。')).not.toBeInTheDocument();
    expect(submitButton()).toBeDisabled();
  });

  it('利用日を選ぶ前は送信を抑止しない（待っているものが無い）', async () => {
    mockedLookup.mockResolvedValue({ id: '1', name: 'サンプル店舗' });

    render(<MemberReservationNewPage />);

    expect(await screen.findByRole('button', { name: 'この内容で申請する' })).toBeEnabled();
  });

  it('ご要望が 500 文字を超えると申請せずに検証エラーを出す', async () => {
    // エラーを描画しないと、送信ボタンが何も起こさないように見える（API 呼び出しも失敗トーストも無い）
    mockedLookup.mockResolvedValue({ id: '1', name: 'サンプル店舗' });

    render(<MemberReservationNewPage />);

    fireEvent.change(await screen.findByLabelText('利用日'), { target: { value: '2026-08-10' } });
    await waitFor(() => expect(submitButton()).toBeEnabled());
    fireEvent.change(screen.getByLabelText('ご要望（任意）'), {
      target: { value: 'あ'.repeat(501) },
    });
    fireEvent.click(submitButton());

    expect(await screen.findByText('ご要望は 500 文字以内で入力してください')).toBeInTheDocument();
    expect(mockedCreate).not.toHaveBeenCalled();
  });

  // noValidate は type="number" の暗黙の step=1 まで止める。引き継ぎが無いと 1.5 が Integer の
  // pax へ届き、欄の傍ではなくサーバからの失敗として返ってくる。
  it('人数に小数を入れると文言を出して申請しないこと', async () => {
    mockedLookup.mockResolvedValue({ id: '1', name: 'サンプル店舗' });

    render(<MemberReservationNewPage />);

    fireEvent.change(await screen.findByLabelText('利用日'), { target: { value: '2026-08-10' } });
    await waitFor(() => expect(submitButton()).toBeEnabled());
    fireEvent.change(screen.getByLabelText('人数'), { target: { value: '1.5' } });
    fireEvent.click(submitButton());

    expect(await screen.findByText('人数は整数で入力してください')).toBeInTheDocument();
    expect(mockedCreate).not.toHaveBeenCalled();
  });

  it('人数が整数なら従来どおり申請できること', async () => {
    mockedLookup.mockResolvedValue({ id: '1', name: 'サンプル店舗' });
    mockedCreate.mockResolvedValue({});

    render(<MemberReservationNewPage />);

    fireEvent.change(await screen.findByLabelText('利用日'), { target: { value: '2026-08-10' } });
    await waitFor(() => expect(submitButton()).toBeEnabled());
    fireEvent.change(screen.getByLabelText('人数'), { target: { value: '2' } });
    fireEvent.click(submitButton());

    await waitFor(() => expect(mockedCreate).toHaveBeenCalledTimes(1));
    expect(mockedCreate.mock.calls[0][0].pax).toBe(2);
  });
});
