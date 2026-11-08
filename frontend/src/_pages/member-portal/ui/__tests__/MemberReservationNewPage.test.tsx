import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemberReservationNewPage } from '../MemberReservationNewPage';
import { memberOrderApplicationApi } from '@/entities/order';
import { shiftApi } from '@/entities/shift';
import { adminStoreApi } from '@/entities/store';

const mockPush = jest.fn();
let searchParams = new URLSearchParams();

jest.mock('next/navigation', () => ({
  useRouter: () => ({ push: mockPush }),
  useSearchParams: () => searchParams,
}));

jest.mock('@/entities/order', () => ({
  memberOrderApplicationApi: { create: jest.fn(), list: jest.fn() },
}));
jest.mock('@/entities/shift', () => ({
  shiftApi: { confirmedCasts: jest.fn() },
}));
jest.mock('@/entities/store', () => ({
  adminStoreApi: { lookupByDomain: jest.fn() },
}));

const mockedLookup = adminStoreApi.lookupByDomain as jest.Mock;
const mockedCasts = shiftApi.confirmedCasts as jest.Mock;
const mockedCreate = memberOrderApplicationApi.create as jest.Mock;
const mockedHistory = memberOrderApplicationApi.list as jest.Mock;

const GUIDANCE = '店舗が特定できませんでした。店舗公式サイトの予約ボタンからお進みください。';

/** 過去の予約申請を 20 件ずつ返すサーバの代役。 */
const historyServer = (rows: Array<{ store_id?: number; store_name?: string }>) => (page: number) =>
  Promise.resolve({
    rows: rows.slice(page * 20, page * 20 + 20).map((row, i) => ({
      id: `a${page}-${i}`,
      status: 'CONFIRMED',
      expired: false,
      ...row,
    })),
    page,
    pageCount: Math.max(1, Math.ceil(rows.length / 20)),
    total: rows.length,
  });

const submitButton = () => screen.getByRole('button', { name: 'この内容で申請する' });

/** 名乗る名前は必須。既存の検証・送信の観測はこの欄を埋めた状態から始める。 */
const fillDeclaredName = (value = '名乗り太郎') =>
  fireEvent.change(screen.getByLabelText('店舗へ名乗るお名前'), { target: { value } });

const castSelect = () => screen.getByRole('combobox', { name: '指名（任意）' });

/** 指名の Select を開いて候補を引く。候補は開いている間だけ描かれる。 */
const findCastOption = async (name: string) => {
  fireEvent.click(castSelect());
  return screen.findByRole('option', { name });
};

describe('MemberReservationNewPage', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    searchParams = new URLSearchParams('store=store1.kizuna.test');
    mockedCasts.mockResolvedValue([]);
    mockedHistory.mockImplementation(historyServer([]));
  });

  it('公式サイトから引き継いだドメインを照会して店舗をプリセレクトする', async () => {
    mockedLookup.mockResolvedValue({ id: '1', name: 'サンプル店舗' });

    render(<MemberReservationNewPage />);

    // 店舗名は題名帯（h1「予約申請」）ではなく、フォームの見出しとして出す
    expect(
      await screen.findByRole('heading', { level: 2, name: 'サンプル店舗' })
    ).toBeInTheDocument();
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

    expect(await screen.findByText('店舗情報を取得できませんでした')).toBeInTheDocument();
    expect(
      screen.queryByText(
        '店舗が特定できませんでした。店舗公式サイトの予約ボタンからお進みください。'
      )
    ).not.toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: '再試行' }));

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
    expect(screen.getByRole('status', { name: '読み込み中' })).toBeInTheDocument();
  });

  it('店舗パラメータが外れたら前の店舗のフォームを残さない', async () => {
    // 取りに行く理由が無くなっても、直前に読めた店舗宛のフォームが送信可能なまま残ってはいけない
    mockedLookup.mockResolvedValueOnce({ id: '1', name: 'サンプル店舗' });

    const { rerender } = render(<MemberReservationNewPage />);
    expect(await screen.findByRole('button', { name: 'この内容で申請する' })).toBeInTheDocument();

    searchParams = new URLSearchParams();
    rerender(<MemberReservationNewPage />);

    await waitFor(() =>
      expect(screen.queryByRole('button', { name: 'この内容で申請する' })).not.toBeInTheDocument()
    );
    expect(
      await screen.findByText(
        '店舗が特定できませんでした。店舗公式サイトの予約ボタンからお進みください。'
      )
    ).toBeInTheDocument();
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
    await waitFor(() => expect(submitButton()).toBeEnabled());
    // 指名は共通の Select（端末標準の select は会員の段の大きさに揃えられない）
    expect(castSelect()).not.toBeInstanceOf(HTMLSelectElement);
    expect(await findCastOption('さくら（18:00〜）')).toBeInTheDocument();
    expect(screen.getByRole('option', { name: '指名なし' })).toBeInTheDocument();
  });

  it('指名の Select で選んだキャストを添えて申請する', async () => {
    mockedLookup.mockResolvedValue({ id: '1', name: 'サンプル店舗' });
    mockedCasts.mockResolvedValue([{ cast_id: 'c1', cast_name: 'さくら', start_time: '18:00:00' }]);
    mockedCreate.mockResolvedValue({});

    render(<MemberReservationNewPage />);

    fireEvent.change(await screen.findByLabelText('利用日'), { target: { value: '2026-08-10' } });
    fillDeclaredName();
    await waitFor(() => expect(submitButton()).toBeEnabled());
    // Base UI の Item は pointerdown を経ていない click を無視する
    const option = await findCastOption('さくら（18:00〜）');
    fireEvent.pointerDown(option);
    fireEvent.click(option);
    await waitFor(() => expect(castSelect()).toHaveTextContent('さくら（18:00〜）'));
    fireEvent.click(submitButton());

    await waitFor(() =>
      expect(mockedCreate).toHaveBeenCalledWith(expect.objectContaining({ cast_id: 'c1' }))
    );
  });

  it('指名を選ばなければ指名なしで申請する', async () => {
    mockedLookup.mockResolvedValue({ id: '1', name: 'サンプル店舗' });
    mockedCasts.mockResolvedValue([{ cast_id: 'c1', cast_name: 'さくら', start_time: '18:00:00' }]);
    mockedCreate.mockResolvedValue({});

    render(<MemberReservationNewPage />);

    fireEvent.change(await screen.findByLabelText('利用日'), { target: { value: '2026-08-10' } });
    fillDeclaredName();
    await waitFor(() => expect(submitButton()).toBeEnabled());
    expect(castSelect()).toHaveTextContent('指名なし');
    fireEvent.click(submitButton());

    await waitFor(() => expect(mockedCreate).toHaveBeenCalledTimes(1));
    expect(mockedCreate.mock.calls[0][0].cast_id).toBeUndefined();
  });

  it('照会で得た店舗 ID・名乗る名前・人数を添えて申請する', async () => {
    mockedLookup.mockResolvedValue({ id: '7', name: 'サンプル店舗' });
    mockedCreate.mockResolvedValue({});

    render(<MemberReservationNewPage />);

    fireEvent.change(await screen.findByLabelText('利用日'), { target: { value: '2026-08-10' } });
    fillDeclaredName();
    await waitFor(() => expect(submitButton()).toBeEnabled());
    const pax = screen.getByLabelText('人数');
    fireEvent.change(pax, { target: { value: '' } });
    fireEvent.change(pax, { target: { value: '3' } });
    fireEvent.click(screen.getByRole('button', { name: 'この内容で申請する' }));

    await waitFor(() =>
      expect(mockedCreate).toHaveBeenCalledWith(
        expect.objectContaining({
          store_id: 7,
          declared_name: '名乗り太郎',
          business_date: '2026-08-10',
          pax: 3,
        })
      )
    );
    expect(mockPush).toHaveBeenCalledWith('/member/reservations/');
  });

  it('申請が通った後は画面が移るまで送信ボタンを戻さない（二重申請を防ぐ）', async () => {
    mockedLookup.mockResolvedValue({ id: '7', name: 'サンプル店舗' });
    mockedCreate.mockResolvedValue({});

    render(<MemberReservationNewPage />);

    fireEvent.change(await screen.findByLabelText('利用日'), { target: { value: '2026-08-10' } });
    fillDeclaredName();
    await waitFor(() => expect(submitButton()).toBeEnabled());
    fireEvent.click(submitButton());

    await waitFor(() => expect(mockPush).toHaveBeenCalledWith('/member/reservations/'));
    expect(submitButton()).toBeDisabled();
  });

  // 名乗る名前は確定時に店舗の顧客台帳行の氏名になる。空で通すとサーバ側の 400 として
  // 返ってきて、どの欄が足りないのか画面に現れない。
  it('名乗る名前が未入力なら申請せずに検証エラーを出す', async () => {
    mockedLookup.mockResolvedValue({ id: '1', name: 'サンプル店舗' });

    render(<MemberReservationNewPage />);

    fireEvent.change(await screen.findByLabelText('利用日'), { target: { value: '2026-08-10' } });
    await waitFor(() => expect(submitButton()).toBeEnabled());
    fireEvent.click(submitButton());

    expect(await screen.findByText('店舗へ名乗るお名前を入力してください')).toBeInTheDocument();
    expect(mockedCreate).not.toHaveBeenCalled();
  });

  // required は空白だけを「入っている」と見る。サーバ側の @NotBlank へ届かせると、欄の傍ではなく
  // 送信失敗の通知として返り、どこを直せばよいのか画面に現れない。
  it('名乗る名前が空白だけなら申請せずに検証エラーを出す', async () => {
    mockedLookup.mockResolvedValue({ id: '1', name: 'サンプル店舗' });

    render(<MemberReservationNewPage />);

    fireEvent.change(await screen.findByLabelText('利用日'), { target: { value: '2026-08-10' } });
    fillDeclaredName('　  ');
    await waitFor(() => expect(submitButton()).toBeEnabled());
    fireEvent.click(submitButton());

    expect(await screen.findByText('店舗へ名乗るお名前を入力してください')).toBeInTheDocument();
    expect(mockedCreate).not.toHaveBeenCalled();
  });

  it('人数が未入力なら申請せずに検証エラーを出す', async () => {
    mockedLookup.mockResolvedValue({ id: '1', name: 'サンプル店舗' });

    render(<MemberReservationNewPage />);

    fireEvent.change(await screen.findByLabelText('利用日'), { target: { value: '2026-08-10' } });
    fillDeclaredName();
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

    expect(await screen.findByText('出勤情報を取得できませんでした')).toBeInTheDocument();
    expect(screen.queryByText('この日に出勤予定のキャストはいません。')).not.toBeInTheDocument();

    // 誰が出勤するか不明のまま送信できると、指名するつもりの会員が「指名なし」で申請してしまう
    expect(submitButton()).toBeDisabled();

    fireEvent.click(screen.getByRole('button', { name: '再試行' }));

    await waitFor(() => expect(submitButton()).toBeEnabled());
    expect(screen.queryByText('出勤情報を取得できませんでした')).not.toBeInTheDocument();
    expect(await findCastOption('さくら（18:00〜）')).toBeInTheDocument();
  });

  it('利用日を変えたら、次の候補が届くまで前の日のキャストを残さない', async () => {
    // 前の日の候補が残ると、その日に出勤しないキャストを指名したまま申請できてしまう
    mockedLookup.mockResolvedValue({ id: '1', name: 'サンプル店舗' });
    mockedCasts.mockResolvedValueOnce([
      { cast_id: 'c1', cast_name: 'さくら', start_time: '18:00:00' },
    ]);

    render(<MemberReservationNewPage />);

    const date = await screen.findByLabelText('利用日');
    fireEvent.change(date, { target: { value: '2026-08-10' } });
    await waitFor(() => expect(submitButton()).toBeEnabled());
    const option = await findCastOption('さくら（18:00〜）');
    fireEvent.pointerDown(option);
    fireEvent.click(option);
    await waitFor(() => expect(castSelect()).toHaveTextContent('さくら（18:00〜）'));

    // 次の日の取得は解決させない（取得中の相を保ったまま観測する）
    mockedCasts.mockReturnValue(new Promise(() => {}));
    fireEvent.change(date, { target: { value: '2026-08-11' } });

    expect(await screen.findByText('出勤情報を確認しています...')).toBeInTheDocument();
    // 前の日の選択も候補も残さない
    await waitFor(() => expect(castSelect()).toHaveTextContent('指名なし'));
    fireEvent.click(castSelect());
    expect(await screen.findByRole('option', { name: '指名なし' })).toBeInTheDocument();
    expect(screen.queryByRole('option', { name: 'さくら（18:00〜）' })).not.toBeInTheDocument();
  });

  it('利用日を消したら、前の日の取得失敗が送信を塞いだままにならない', async () => {
    // 失敗の文言は利用日が入っている間しか出ない。抑止だけ残ると、押せない理由がどこにも
    // 書かれていない送信ボタンになる（日付を選び直すのは普通の操作）
    mockedLookup.mockResolvedValue({ id: '1', name: 'サンプル店舗' });
    mockedCasts.mockRejectedValue(new Error('network'));

    render(<MemberReservationNewPage />);

    const date = await screen.findByLabelText('利用日');
    fireEvent.change(date, { target: { value: '2026-08-10' } });

    expect(await screen.findByText('出勤情報を取得できませんでした')).toBeInTheDocument();
    expect(submitButton()).toBeDisabled();

    fireEvent.change(date, { target: { value: '' } });

    await waitFor(() => expect(submitButton()).toBeEnabled());
    expect(screen.queryByText('出勤情報を取得できませんでした')).not.toBeInTheDocument();
  });

  it('店舗パラメータが外れたら、前の照会の失敗が居座らない', async () => {
    // 取りに行く先が無いまま再試行を出しても、押した先で何も起きない行き止まりになる
    mockedLookup.mockRejectedValue(new Error('network'));

    const { rerender } = render(<MemberReservationNewPage />);
    expect(await screen.findByText('店舗情報を取得できませんでした')).toBeInTheDocument();

    searchParams = new URLSearchParams();
    rerender(<MemberReservationNewPage />);

    expect(
      await screen.findByText(
        '店舗が特定できませんでした。店舗公式サイトの予約ボタンからお進みください。'
      )
    ).toBeInTheDocument();
    expect(screen.queryByText('店舗情報を取得できませんでした')).not.toBeInTheDocument();
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
    fillDeclaredName();
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
    fillDeclaredName();
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
    fillDeclaredName();
    await waitFor(() => expect(submitButton()).toBeEnabled());
    fireEvent.change(screen.getByLabelText('人数'), { target: { value: '2' } });
    fireEvent.click(submitButton());

    await waitFor(() => expect(mockedCreate).toHaveBeenCalledTimes(1));
    expect(mockedCreate.mock.calls[0][0].pax).toBe(2);
  });
  describe('?store= が無いとき（一覧の「新しく予約」から来たとき）', () => {
    beforeEach(() => {
      searchParams = new URLSearchParams();
    });

    it('過去に予約した店舗を重複なく並べ、選んだ店舗宛に申請する', async () => {
      mockedHistory.mockImplementation(
        historyServer([
          { store_id: 1, store_name: '店舗A' },
          { store_id: 2, store_name: '店舗B' },
          { store_id: 1, store_name: '店舗A' },
        ])
      );
      mockedCreate.mockResolvedValue({});

      render(<MemberReservationNewPage />);

      expect(await screen.findByRole('button', { name: '店舗A' })).toBeInTheDocument();
      expect(screen.getAllByRole('button', { name: /店舗[AB]/ })).toHaveLength(2);
      expect(screen.queryByRole('button', { name: 'この内容で申請する' })).not.toBeInTheDocument();

      fireEvent.click(screen.getByRole('button', { name: '店舗B' }));

      expect(await screen.findByRole('heading', { level: 2, name: '店舗B' })).toBeInTheDocument();
      fireEvent.change(screen.getByLabelText('利用日'), { target: { value: '2026-08-10' } });
      fillDeclaredName();
      await waitFor(() => expect(submitButton()).toBeEnabled());
      fireEvent.click(submitButton());

      await waitFor(() =>
        expect(mockedCreate).toHaveBeenCalledWith(expect.objectContaining({ store_id: 2 }))
      );
      expect(mockedLookup).not.toHaveBeenCalled();
    });

    it('選んだ後でも店舗を選び直せる', async () => {
      mockedHistory.mockImplementation(
        historyServer([
          { store_id: 1, store_name: '店舗A' },
          { store_id: 2, store_name: '店舗B' },
        ])
      );

      render(<MemberReservationNewPage />);

      fireEvent.click(await screen.findByRole('button', { name: '店舗B' }));
      fireEvent.click(await screen.findByRole('button', { name: '店舗を選び直す' }));

      expect(await screen.findByRole('button', { name: '店舗A' })).toBeInTheDocument();
      expect(screen.queryByRole('button', { name: 'この内容で申請する' })).not.toBeInTheDocument();
    });

    it('過去の予約が 1 店舗だけなら選ばせずにその店舗で進める', async () => {
      mockedHistory.mockImplementation(historyServer([{ store_id: 3, store_name: '店舗C' }]));

      render(<MemberReservationNewPage />);

      expect(await screen.findByRole('heading', { level: 2, name: '店舗C' })).toBeInTheDocument();
      expect(screen.getByRole('button', { name: 'この内容で申請する' })).toBeInTheDocument();
      // 選ぶ相手が居ないので、選び直しも出さない
      expect(screen.queryByRole('button', { name: '店舗を選び直す' })).not.toBeInTheDocument();
    });

    it('2 ページ目以降の予約の店舗も選択肢に入れる', async () => {
      const rows = [
        ...Array.from({ length: 20 }, () => ({ store_id: 1, store_name: '店舗A' })),
        { store_id: 2, store_name: '店舗B' },
      ];
      mockedHistory.mockImplementation(historyServer(rows));

      render(<MemberReservationNewPage />);

      expect(await screen.findByRole('button', { name: '店舗B' })).toBeInTheDocument();
      expect(mockedHistory.mock.calls.map(([page]) => page)).toEqual([0, 1]);
    });

    it('過去の予約が無ければ店舗公式サイトからの案内だけを出す', async () => {
      render(<MemberReservationNewPage />);

      expect(await screen.findByText(GUIDANCE)).toBeInTheDocument();
      expect(screen.queryByRole('button', { name: 'この内容で申請する' })).not.toBeInTheDocument();
    });

    it('過去の予約を読めなければ案内と取り違えず、再試行で復帰できる', async () => {
      mockedHistory.mockRejectedValueOnce(new Error('network'));
      mockedHistory.mockImplementation(historyServer([{ store_id: 3, store_name: '店舗C' }]));

      render(<MemberReservationNewPage />);

      expect(await screen.findByText('予約した店舗を取得できませんでした')).toBeInTheDocument();
      expect(screen.queryByText(GUIDANCE)).not.toBeInTheDocument();

      fireEvent.click(screen.getByRole('button', { name: '再試行' }));

      expect(await screen.findByRole('heading', { level: 2, name: '店舗C' })).toBeInTheDocument();
    });
  });

  it('?store= があるときは過去の予約を引かない', async () => {
    mockedLookup.mockResolvedValue({ id: '1', name: 'サンプル店舗' });

    render(<MemberReservationNewPage />);

    expect(await screen.findByRole('button', { name: 'この内容で申請する' })).toBeInTheDocument();
    expect(mockedHistory).not.toHaveBeenCalled();
  });
});
