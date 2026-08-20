import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import OrderListPage from '../ui/OrdersPage';
import CreateOrderPage from '../ui/OrderCreatePage';
import { Order, OrderApplicationRow, orderApi, orderApplicationApi } from '@/entities/order';
import { notify } from '@/shared/notify';

jest.mock('@/entities/order', () => ({
  // 表示ラベル等の定数は本物を使う（API だけを差し替える）
  ...jest.requireActual('@/entities/order/model/types'),
  orderApi: {
    get: jest.fn(),
    create: jest.fn(),
    update: jest.fn(),
    cancel: jest.fn(),
    listReceptionists: jest.fn(),
    listCastCandidates: jest.fn(),
    listWorkQueue: jest.fn(),
    listArchive: jest.fn(),
    complete: jest.fn(),
    completionPreview: jest.fn(),
  },
  orderApplicationApi: {
    list: jest.fn(),
    confirm: jest.fn(),
    decline: jest.fn(),
  },
}));

jest.mock('next/navigation', () => ({
  useRouter: () => ({ push: jest.fn(), back: jest.fn() }),
  useParams: () => ({ storeId: '1' }),
}));

jest.mock('@/shared/notify', () => ({
  notify: { success: jest.fn(), error: jest.fn(), warning: jest.fn() },
}));

const mockedOrderApi = orderApi as jest.Mocked<typeof orderApi>;
const mockedApplicationApi = orderApplicationApi as jest.Mocked<typeof orderApplicationApi>;

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

/** 受付箱の未処理申請 1 件。fixture は手書きで、型との照合は tsc の側で効く。 */
function pendingApplication(overrides: Partial<OrderApplicationRow> = {}): OrderApplicationRow {
  return {
    id: 'a1',
    business_date: '2026-07-05',
    requester_declared_name: '高橋美咲',
    requester_member_code: '000123456789',
    pax: 2,
    status: 'PENDING',
    expired: false,
    ...overrides,
  };
}

const EMPTY_ARCHIVE = { rows: [], page: 0, pageCount: 0, total: 0 };

function stubQueue(...rows: Order[]) {
  mockedOrderApi.listWorkQueue.mockResolvedValue({ rows, nextCursor: null });
}

function stubInbox(...rows: OrderApplicationRow[]) {
  mockedApplicationApi.list.mockResolvedValue({ rows, nextCursor: null });
}

beforeEach(() => {
  jest.clearAllMocks();
  mockedOrderApi.listWorkQueue.mockResolvedValue({ rows: [], nextCursor: null });
  mockedOrderApi.listArchive.mockResolvedValue(EMPTY_ARCHIVE);
  mockedApplicationApi.list.mockResolvedValue({ rows: [], nextCursor: null });
  mockedOrderApi.listReceptionists.mockResolvedValue([]);
});

describe('作業キューの描画', () => {
  it('対応が要る受注をカードで出し、状態と内容を名乗ること', async () => {
    stubQueue(confirmedOrder());
    render(<OrderListPage />);

    expect(await screen.findByText('山田太郎')).toBeInTheDocument();
    expect(screen.getByText('確定')).toBeInTheDocument();
    expect(screen.getByText(/指名 花子/)).toBeInTheDocument();
    expect(screen.getByText(/2 名/)).toBeInTheDocument();
  });

  it('作業キューの群は確定済みだけで、未処理の申請は受付箱の読み口へ要求すること', async () => {
    render(<OrderListPage />);

    await waitFor(() => expect(mockedOrderApi.listWorkQueue).toHaveBeenCalled());
    // すべての受注は確定で出生する（ADR 0017）。終端を混ぜると、完了が積み上がった店舗で
    // 対応が要る受注が取得窓から落ちる
    expect(mockedOrderApi.listWorkQueue).toHaveBeenCalledWith(
      expect.objectContaining({ statuses: ['CONFIRMED'] })
    );
    expect(mockedApplicationApi.list).toHaveBeenCalledWith(
      expect.objectContaining({ statuses: ['PENDING'] })
    );
  });

  it('受付箱の申請には確定・謝絶が出て、作業キューの受注は完了・取消を持つこと', async () => {
    stubInbox(pendingApplication());
    stubQueue(confirmedOrder());
    render(<OrderListPage />);

    expect(await screen.findByRole('button', { name: '確定' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '謝絶' })).toBeInTheDocument();
    expect(screen.getByText('高橋美咲')).toBeInTheDocument();
    expect(screen.getByText(/会員コード: 000123456789/)).toBeInTheDocument();
    // 確定済みの側は完了と取消を持つ
    expect(screen.getByRole('button', { name: '完了' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '取消' })).toBeInTheDocument();
  });

  it('失効した申請は失効を名乗り、確定・謝絶を出さないこと', async () => {
    // サーバも拒否する操作を出し続けない。行は導出のまま残る（状態は PENDING のまま動かない）
    stubInbox(pendingApplication({ expired: true }));
    render(<OrderListPage />);

    expect(await screen.findByText('失効')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '確定' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '謝絶' })).not.toBeInTheDocument();
  });

  it('確定は申請内容を予填したモーダルで行い、申請が受付箱から外れて作業キューを取り直すこと', async () => {
    stubInbox(pendingApplication());
    mockedApplicationApi.confirm.mockResolvedValue(confirmedOrder({ id: 'order-9' }));
    render(<OrderListPage />);

    fireEvent.click(await screen.findByRole('button', { name: '確定' }));

    const dialog = await screen.findByRole('dialog');
    // 申請内容が予填される。ここで直した値は受注にだけ現れ、申請原文は動かない
    await waitFor(() => expect(within(dialog).getByLabelText('人数')).toHaveValue(2));
    fireEvent.change(within(dialog).getByLabelText('人数'), { target: { value: '5' } });
    fireEvent.click(within(dialog).getByRole('button', { name: '確定する' }));

    await waitFor(() =>
      expect(mockedApplicationApi.confirm).toHaveBeenCalledWith(
        'a1',
        expect.objectContaining({ business_date: '2026-07-05', pax: 5 })
      )
    );
    // 申請は受付箱から外れ、生まれた受注は作業キューの取り直しで現れる
    await waitFor(() => expect(screen.queryByText('高橋美咲')).not.toBeInTheDocument());
    await waitFor(() => expect(mockedOrderApi.listWorkQueue).toHaveBeenCalledTimes(2));
  });

  it('識別子の無い申請の謝絶は要求を組まず、理由を名乗ること', async () => {
    // `?? ''` で素通しすると POST /store/order-applications//refusal が飛び、届いた先の 404 が
    // 「謝絶に失敗しました」と見分けが付かなくなる
    stubInbox(pendingApplication({ id: undefined }));
    render(<OrderListPage />);

    fireEvent.click(await screen.findByRole('button', { name: '謝絶' }));
    fireEvent.change(screen.getByLabelText('謝絶の理由'), { target: { value: '満席' } });
    fireEvent.click(screen.getByRole('button', { name: '謝絶する' }));

    await waitFor(() =>
      expect(notify.error).toHaveBeenCalledWith(
        expect.stringContaining('予約申請の識別子が取得できていません')
      )
    );
    expect(mockedApplicationApi.decline).not.toHaveBeenCalled();
  });

  it('謝絶は理由が空のまま実行できず、理由を添えると申請が受付箱から外れること', async () => {
    stubInbox(pendingApplication());
    mockedApplicationApi.decline.mockResolvedValue(undefined);
    render(<OrderListPage />);

    fireEvent.click(await screen.findByRole('button', { name: '謝絶' }));
    // 検証で押せなくしない — 灰色のボタンは何が足りないかを言わない（DESIGN.md）
    fireEvent.click(screen.getByRole('button', { name: '謝絶する' }));
    expect(await screen.findByText('謝絶の理由を入力してください')).toBeInTheDocument();
    expect(mockedApplicationApi.decline).not.toHaveBeenCalled();

    fireEvent.change(screen.getByLabelText('謝絶の理由'), { target: { value: '満席' } });
    fireEvent.click(screen.getByRole('button', { name: '謝絶する' }));

    await waitFor(() =>
      expect(mockedApplicationApi.decline).toHaveBeenCalledWith('a1', { reason: '満席' })
    );
    await waitFor(() => expect(screen.queryByText('高橋美咲')).not.toBeInTheDocument());
  });

  it('受付箱の取得の失敗を空表示と区別すること', async () => {
    mockedApplicationApi.list.mockRejectedValue(new Error('boom'));
    render(<OrderListPage />);

    // 「申請なし」に見せると未処理を見落とす
    expect(await screen.findByRole('alert')).toHaveTextContent('予約申請を取得できませんでした');
    expect(screen.queryByText('未処理の予約申請はありません')).not.toBeInTheDocument();
  });

  it('取得の失敗を空表示と区別すること', async () => {
    mockedOrderApi.listWorkQueue.mockRejectedValue(new Error('boom'));
    render(<OrderListPage />);

    // 「受注なし」に見せると未対応を見落とす
    expect(await screen.findByRole('alert')).toHaveTextContent('受注を取得できませんでした');
    expect(screen.queryByText('条件に合う受注がありません')).not.toBeInTheDocument();
  });

  it('顧客未設定の受注は録入された連絡先で呼ぶこと', async () => {
    stubQueue(
      confirmedOrder({
        customer_id: undefined,
        customer_name: undefined,
        contact_name: '匿名希望',
      })
    );
    render(<OrderListPage />);

    expect(await screen.findByText('匿名希望')).toBeInTheDocument();
    expect(screen.getByText('（顧客未設定）')).toBeInTheDocument();
  });
});

describe('検索と並び替え', () => {
  it('検索は適用してから取得へ渡り、群を跨いで同じ条件が当たること', async () => {
    render(<OrderListPage />);
    await waitFor(() => expect(mockedOrderApi.listWorkQueue).toHaveBeenCalled());

    fireEvent.change(screen.getByLabelText('お客様名'), { target: { value: '山田' } });
    // 入力しただけでは取得へ行かない（適用済みの条件だけを読む）
    expect(mockedOrderApi.listWorkQueue).toHaveBeenCalledTimes(1);

    fireEvent.click(screen.getByRole('button', { name: '検索' }));

    await waitFor(() =>
      expect(mockedOrderApi.listWorkQueue).toHaveBeenLastCalledWith(
        expect.objectContaining({ customer_name: '山田' })
      )
    );
    // アーカイブにも同じ条件が当たる — 群ごとに違う条件だと同じ画面が 2 つの母集合を主張する
    await waitFor(() =>
      expect(mockedOrderApi.listArchive).toHaveBeenLastCalledWith(
        expect.objectContaining({ customer_name: '山田' })
      )
    );
  });

  it('並び替えの向きを変えると、その条件で取り直すこと', async () => {
    render(<OrderListPage />);
    await waitFor(() => expect(mockedOrderApi.listWorkQueue).toHaveBeenCalled());

    fireEvent.click(screen.getByRole('button', { name: /昇順/ }));

    // 取り直さない形だと「並び替えても一覧が動かない」退行が静かに通る
    await waitFor(() =>
      expect(mockedOrderApi.listWorkQueue).toHaveBeenLastCalledWith(
        expect.objectContaining({ sort_key: 'BUSINESS_DATE', desc: true })
      )
    );
    await waitFor(() =>
      expect(mockedOrderApi.listArchive).toHaveBeenLastCalledWith(
        expect.objectContaining({ desc: true })
      )
    );
  });
});

describe('カード内の取消（二段）', () => {
  it('取消を押すと理由の入力に変わり、理由が空のまま実行すると欄の傍が理由を求めること', async () => {
    stubQueue(confirmedOrder());
    render(<OrderListPage />);

    fireEvent.click(await screen.findByRole('button', { name: '取消' }));

    // 検証で押せなくしない — 灰色のボタンは何が足りないかを言わない（DESIGN.md）
    fireEvent.click(screen.getByRole('button', { name: '取消する' }));

    expect(await screen.findByText('取消の理由を入力してください')).toBeInTheDocument();
    expect(mockedOrderApi.cancel).not.toHaveBeenCalled();
  });

  it('空白だけの理由も「書いていない」と同じに扱うこと', async () => {
    stubQueue(confirmedOrder());
    render(<OrderListPage />);

    fireEvent.click(await screen.findByRole('button', { name: '取消' }));
    fireEvent.change(screen.getByLabelText('取消の理由'), { target: { value: '   ' } });
    fireEvent.click(screen.getByRole('button', { name: '取消する' }));

    expect(await screen.findByText('取消の理由を入力してください')).toBeInTheDocument();
    expect(mockedOrderApi.cancel).not.toHaveBeenCalled();
  });

  it('理由を添えて取消すと専用の口を叩き、その受注が群から外れること', async () => {
    stubQueue(confirmedOrder());
    mockedOrderApi.cancel.mockResolvedValue(undefined);
    render(<OrderListPage />);

    fireEvent.click(await screen.findByRole('button', { name: '取消' }));
    fireEvent.change(screen.getByLabelText('取消の理由'), { target: { value: '客都合' } });
    fireEvent.click(screen.getByRole('button', { name: '取消する' }));

    await waitFor(() =>
      expect(mockedOrderApi.cancel).toHaveBeenCalledWith('o1', { reason: '客都合' })
    );
    await waitFor(() => expect(screen.queryByText('山田太郎')).not.toBeInTheDocument());
  });

  it('やめるを押すと理由を捨てて元の操作に戻ること', async () => {
    stubQueue(confirmedOrder());
    render(<OrderListPage />);

    fireEvent.click(await screen.findByRole('button', { name: '取消' }));
    fireEvent.change(screen.getByLabelText('取消の理由'), { target: { value: '書きかけ' } });
    fireEvent.click(screen.getByRole('button', { name: 'やめる' }));

    expect(screen.queryByLabelText('取消の理由')).not.toBeInTheDocument();
    expect(mockedOrderApi.cancel).not.toHaveBeenCalled();
  });
});

describe('一覧内の編集モーダル', () => {
  it('確定済みの編集は 1 件を読み直し、指名と受付担当を毎回運んで保存すること', async () => {
    stubQueue(confirmedOrder());
    mockedOrderApi.get.mockResolvedValue(confirmedOrder());
    mockedOrderApi.update.mockResolvedValue(confirmedOrder({ pax: 5 }));
    render(<OrderListPage />);

    fireEvent.click(await screen.findByRole('button', { name: '編集' }));

    // 一覧の行を種にすると、他の操作者が直した後の画面で陳腐化した値を送り返す
    await waitFor(() => expect(mockedOrderApi.get).toHaveBeenCalledWith('o1'));
    const dialog = await screen.findByRole('dialog');
    await waitFor(() => expect(within(dialog).getByLabelText('人数')).toHaveValue(2));

    fireEvent.change(within(dialog).getByLabelText('人数'), { target: { value: '5' } });
    fireEvent.click(within(dialog).getByRole('button', { name: '保存' }));

    await waitFor(() => expect(mockedOrderApi.update).toHaveBeenCalled());
    // 触った欄と、省略が「外す」と区別できない 2 項目だけ。全項目を毎回運ぶと、この画面を開いている
    // 間に別の操作者が直した受注へ、触ってもいない項目を開いた時点の値で押し戻してしまう
    const [, body] = mockedOrderApi.update.mock.calls[0];
    expect(Object.keys(body).sort()).toEqual(['cast_id', 'pax', 'receptionist_id']);
    expect(body).toEqual({ pax: 5, receptionist_id: 3, cast_id: 'cast-1' });
  });

  it('文字列の欄は空にした結果も送ること（空文字が「空にする」の表し方）', async () => {
    stubQueue(confirmedOrder({ remarks: '消したい備考' }));
    mockedOrderApi.get.mockResolvedValue(confirmedOrder({ remarks: '消したい備考' }));
    mockedOrderApi.update.mockResolvedValue(confirmedOrder());
    render(<OrderListPage />);

    fireEvent.click(await screen.findByRole('button', { name: '編集' }));
    const dialog = await screen.findByRole('dialog');
    await waitFor(() => expect(within(dialog).getByLabelText('備考')).toHaveValue('消したい備考'));

    fireEvent.change(within(dialog).getByLabelText('備考'), { target: { value: '' } });
    fireEvent.click(within(dialog).getByRole('button', { name: '保存' }));

    // 項目ごと落とすと、消したはずの備考が残る（サーバは送られない項目を「変更しない」と読む）
    await waitFor(() =>
      expect(mockedOrderApi.update).toHaveBeenCalledWith(
        'o1',
        expect.objectContaining({ remarks: '' })
      )
    );
  });

  it('同じ受注を開き直したとき、取り直しの前に前回の内容を出さないこと', async () => {
    stubQueue(confirmedOrder());
    mockedOrderApi.get.mockResolvedValue(confirmedOrder());
    render(<OrderListPage />);

    fireEvent.click(await screen.findByRole('button', { name: '編集' }));
    const first = await screen.findByRole('dialog');
    await waitFor(() => expect(within(first).getByLabelText('人数')).toHaveValue(2));
    fireEvent.click(within(first).getByRole('button', { name: 'キャンセル' }));

    // 2 度目は取得を宙吊りにする。取得の口は取りに行かない間も持っている値を残すので、播種済みの
    // 印を消さないと、取り直しの完了を待たずに陳腐化した内容のフォームが出て保存できてしまう
    mockedOrderApi.get.mockReturnValue(new Promise<Order>(() => {}));
    fireEvent.click(await screen.findByRole('button', { name: '編集' }));

    const reopened = await screen.findByRole('dialog');
    await waitFor(() => expect(within(reopened).getByText('読み込み中...')).toBeInTheDocument());
    expect(within(reopened).queryByLabelText('人数')).not.toBeInTheDocument();
  });

  it('顧客の着いた受注では連絡先を編集させず、顧客詳細への導線を出すこと', async () => {
    stubQueue(confirmedOrder());
    mockedOrderApi.get.mockResolvedValue(confirmedOrder());
    render(<OrderListPage />);

    fireEvent.click(await screen.findByRole('button', { name: '編集' }));
    const dialog = await screen.findByRole('dialog');

    // 受注 1 件を直したつもりの変更が同じ顧客の他の受注へ波及しないため、台帳の項目は読み取り
    await waitFor(() =>
      expect(within(dialog).getByRole('link', { name: /顧客詳細を開く/ })).toHaveAttribute(
        'href',
        '/store/1/customers/c1'
      )
    );
    expect(within(dialog).queryByLabelText('電話番号')).not.toBeInTheDocument();
  });

  it('顧客の着いていない受注では連絡先を訂正でき、その 2 項目を送ること', async () => {
    const unlinked = confirmedOrder({
      customer_id: undefined,
      customer_name: undefined,
      contact_name: '誤記の名前',
    });
    stubQueue(unlinked);
    mockedOrderApi.get.mockResolvedValue(unlinked);
    mockedOrderApi.update.mockResolvedValue(unlinked);
    render(<OrderListPage />);

    fireEvent.click(await screen.findByRole('button', { name: '編集' }));
    const dialog = await screen.findByRole('dialog');
    await waitFor(() =>
      expect(within(dialog).getByLabelText('お客様名')).toHaveValue('誤記の名前')
    );

    fireEvent.change(within(dialog).getByLabelText('お客様名'), {
      target: { value: '正しい名前' },
    });
    fireEvent.click(within(dialog).getByRole('button', { name: '保存' }));

    await waitFor(() =>
      expect(mockedOrderApi.update).toHaveBeenCalledWith(
        'o1',
        expect.objectContaining({ contact_name: '正しい名前' })
      )
    );
  });
});

describe('アーカイブ', () => {
  it('たたまれた状態では行を出さず、開くと結末を名乗ること', async () => {
    mockedOrderApi.listArchive.mockImplementation(async params =>
      params.statuses[0] === 'CANCELLED'
        ? {
            rows: [
              confirmedOrder({
                id: 'x1',
                status: 'CANCELLED',
                cancelled_reason: '客都合。当日夕方に連絡あり',
                cancelled_by_name: '田中店長',
                cancelled_at: '2026-07-03T17:42:00+09:00',
              }),
            ],
            page: 0,
            pageCount: 1,
            total: 1,
          }
        : EMPTY_ARCHIVE
    );
    render(<OrderListPage />);

    const toggle = await screen.findByRole('button', { name: /取消 \d+ 件/ });
    expect(screen.queryByText(/客都合。当日夕方に連絡あり/)).not.toBeInTheDocument();

    fireEvent.click(toggle);

    // 結末を確かめるために詳細を開かなくて済むよう、行が理由・実行者・時刻を名乗る
    expect(await screen.findByText(/客都合。当日夕方に連絡あり/)).toBeInTheDocument();
    expect(screen.getByText(/田中店長/)).toBeInTheDocument();
    // 時刻は閲覧者の時間帯へ直して出す。応答の文字列を切って出すと、末尾の +09:00 が落ちた壁時計が
    // そのまま日本時間の顔をして並ぶ（実行環境の時間帯に依らないよう、切った形が無いことで見る）
    expect(screen.queryByText(/2026-07-03 17:42/)).not.toBeInTheDocument();
  });

  it('受注が移ってきた群を取り直すこと', async () => {
    stubQueue(confirmedOrder());
    mockedOrderApi.cancel.mockResolvedValue(undefined);
    render(<OrderListPage />);

    // 起動時に 2 群ぶん取りに行く。ここから増えた分がアーカイブの取り直し
    await waitFor(() => expect(mockedOrderApi.listArchive).toHaveBeenCalledTimes(2));

    fireEvent.click(await screen.findByRole('button', { name: '取消' }));
    fireEvent.change(screen.getByLabelText('取消の理由'), { target: { value: '客都合' } });
    fireEvent.click(screen.getByRole('button', { name: '取消する' }));

    // 件数の控えはたたんだままでも出ているので、取り直さないと「アーカイブに入ったのか」が
    // 画面のどこからも読めない。取り直すのは行き先の群だけ
    await waitFor(() => expect(mockedOrderApi.listArchive).toHaveBeenCalledTimes(3));
    const [lastCall] = mockedOrderApi.listArchive.mock.calls.slice(-1);
    expect(lastCall[0].statuses).toEqual(['CANCELLED']);
  });

  it('完了の行は会計金額と付与ポイントを名乗ること', async () => {
    mockedOrderApi.listArchive.mockImplementation(async params =>
      params.statuses[0] === 'COMPLETED'
        ? {
            rows: [
              confirmedOrder({
                id: 'x2',
                status: 'COMPLETED',
                total_fee: 28000,
                auto_grant_points: 280,
              }),
            ],
            page: 0,
            pageCount: 1,
            total: 1,
          }
        : EMPTY_ARCHIVE
    );
    render(<OrderListPage />);

    fireEvent.click(await screen.findByRole('button', { name: /完了/ }));

    expect(await screen.findByText(/会計 ¥28,000/)).toBeInTheDocument();
    expect(screen.getByText(/付与 280pt/)).toBeInTheDocument();
  });
});

describe('オーダー一覧ページ固有の要素', () => {
  it('見出し（h1）・副題・主アクションのリンク先を備えること', async () => {
    render(<OrderListPage />);

    expect(await screen.findByRole('heading', { level: 1 })).toHaveTextContent('オーダー一覧');
    expect(screen.getByRole('link', { name: /新規オーダー登録/ })).toHaveAttribute(
      'href',
      '/store/1/orders/create'
    );
  });
});

describe('新規オーダー登録', () => {
  beforeEach(() => {
    mockedOrderApi.listReceptionists.mockResolvedValue([{ id: 3, display_name: '山田次郎' }]);
    mockedOrderApi.listCastCandidates.mockResolvedValue([{ id: 'cast-1', name: '花子' }]);
  });

  it('受付担当を選ばなければ項目ごと送らないこと（サーバが実行者本人に解決する）', async () => {
    mockedOrderApi.create.mockResolvedValue(confirmedOrder());
    render(<CreateOrderPage />);

    fireEvent.change(screen.getByLabelText('お客様名'), { target: { value: '新規客' } });
    // キャストは必須。候補から選ばずに送ると欄の傍で止まる
    const form = screen.getByRole('button', { name: '登録する' });
    fireEvent.click(form);

    await waitFor(() => expect(screen.getByText(/キャストを候補から選択/)).toBeInTheDocument());
    expect(mockedOrderApi.create).not.toHaveBeenCalled();
  });

  it('受付経路の選択肢に Web 申請を出さないこと', async () => {
    render(<CreateOrderPage />);

    // WEB は会員ポータルの申請だけが名乗る値（後端も拒否する）
    expect(screen.queryByText('Web 申請')).not.toBeInTheDocument();
  });
});
