import { render, screen, fireEvent, waitFor, within } from '@testing-library/react';
import CustomersPage from '../ui/CustomersPage';
import CustomerCreatePage from '../ui/CustomerCreatePage';
import { CustomerForm } from '../ui/CustomerForm';
import { customerApi } from '@/entities/customer';
import { readTokenClaims } from '@/shared/lib';

// hasPermission は実物のまま（PERM_ 接頭辞の対応も検証対象に含める）
jest.mock('@/shared/lib', () => ({
  ...jest.requireActual('@/shared/lib'),
  readTokenClaims: jest.fn(),
}));

jest.mock('@/entities/customer', () => ({
  customerApi: {
    list: jest.fn(),
    create: jest.fn(),
    linkMember: jest.fn(),
    unlinkMember: jest.fn(),
    memberLinkHistory: jest.fn(),
    mergeComparison: jest.fn(),
    merge: jest.fn(),
  },
}));

jest.mock('@/shared/notify', () => ({
  notify: { success: jest.fn(), error: jest.fn(), warning: jest.fn() },
}));

jest.mock('next/navigation', () => ({
  useRouter: () => ({ push: jest.fn(), back: jest.fn() }),
  useParams: () => ({ storeId: '1' }),
}));

const mockedCustomerApi = customerApi as jest.Mocked<typeof customerApi>;
const mockedReadClaims = readTokenClaims as jest.MockedFunction<typeof readTokenClaims>;

describe('店側顧客画面と API JSON（snake_case）の整合', () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  it('一覧はバックエンドが実際に返す snake_case のフィールドを表示すること', async () => {
    // バックエンドは Jackson グローバル SNAKE_CASE（既知の実レスポンス形）
    mockedCustomerApi.list.mockResolvedValue({
      rows: [
        {
          id: '1',
          name: '山田太郎',
          phone_number: '090-1111-2222',
          line_id: 'yamada',
          classification: '常連',
          ng_type: '注意',
        },
      ],
      page: 0,
      pageCount: 1,
      total: 1,
    } as never);

    render(<CustomersPage />);

    expect(await screen.findByText('山田太郎')).toBeInTheDocument();
    expect(screen.getByText('090-1111-2222')).toBeInTheDocument();
    expect(screen.getByText('yamada')).toBeInTheDocument();
    // NG バッジは ng_type をそのまま表示する
    expect(screen.getByText('注意')).toBeInTheDocument();
  });

  it('新規登録はバックエンドの DTO に合わせ snake_case キーで POST すること', async () => {
    mockedCustomerApi.create.mockResolvedValue({} as never);

    render(<CustomerCreatePage />);
    fireEvent.change(screen.getByLabelText('名前 *'), { target: { value: '田中花子' } });
    fireEvent.click(screen.getByRole('button', { name: '保存する' }));

    await waitFor(() => expect(mockedCustomerApi.create).toHaveBeenCalledTimes(1));
    const body = mockedCustomerApi.create.mock.calls[0][0] as unknown as Record<string, unknown>;
    expect(body).toHaveProperty('name', '田中花子');
    // 未操作チェックボックスは boolean false のまま（挙動維持の錨）
    expect(body).toHaveProperty('has_pet', false);
    // 空文字フィールドは toCustomerRequest で undefined に落ちる
    expect(body).toHaveProperty('phone_number', undefined);
    // camelCase キーが混入しないこと
    expect(body).not.toHaveProperty('phoneNumber');
    expect(body).not.toHaveProperty('hasPet');
    expect(body).not.toHaveProperty('lineId');
  });

  it('名前が未入力なら文言を欄の傍に出し、create を呼ばないこと', async () => {
    mockedCustomerApi.create.mockResolvedValue({} as never);

    render(<CustomerCreatePage />);
    fireEvent.click(screen.getByRole('button', { name: '保存する' }));

    const message = await screen.findByText('名前を入力してください');
    const input = screen.getByLabelText('名前 *');
    expect(input).toHaveAttribute('aria-invalid', 'true');
    expect(input).toHaveAttribute('aria-describedby', expect.stringContaining(message.id));
    expect(mockedCustomerApi.create).not.toHaveBeenCalled();
    // 検証を理由にボタンを塞がない
    expect(screen.getByRole('button', { name: '保存する' })).toBeEnabled();
  });

  it('編集時の has_pet:true が controlled チェックボックスに反映されること', () => {
    render(<CustomerForm initialData={{ name: '太郎', has_pet: true }} onSubmit={jest.fn()} />);

    expect(screen.getByRole('checkbox')).toBeChecked();
    // ラベル文字クリックでトグルできること（label→control の関連付け維持）
    fireEvent.click(screen.getByText('ペットあり'));
    expect(screen.getByRole('checkbox')).not.toBeChecked();
  });
});

describe('顧客一覧ページ固有の要素', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    mockedCustomerApi.list.mockResolvedValue({
      rows: [],
      page: 0,
      pageCount: 0,
      total: 0,
    } as never);
  });

  it('見出し（h1）・副題・主アクションのリンク先を備えること', async () => {
    render(<CustomersPage />);
    await screen.findByText('顧客が登録されていません');

    expect(screen.getByRole('heading', { level: 1, name: '顧客管理' })).toBeInTheDocument();
    expect(screen.getByText('顧客情報の登録・編集ができます。')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: '新規顧客登録' })).toHaveAttribute(
      'href',
      '/store/1/customers/create'
    );
  });

  it('会員列は紐づけ済み・未紐づけを状態バッジで出し分けること', async () => {
    mockedCustomerApi.list.mockResolvedValue({
      rows: [
        { id: '1', name: '紐づけ太郎', member_linked: true, linked_member_code: '123456789012' },
        { id: '2', name: '未紐づけ次郎', member_linked: false },
      ],
      page: 0,
      pageCount: 1,
      total: 2,
    } as never);

    render(<CustomersPage />);

    expect(await screen.findByText('紐づけ済み')).toBeInTheDocument();
    expect(screen.getByText('未紐づけ')).toBeInTheDocument();
  });

  it('検索の送信で入力中の絞り込み条件を 1 ページ目から取り直すこと', async () => {
    render(<CustomersPage />);
    await screen.findByText('顧客が登録されていません');

    fireEvent.change(screen.getByPlaceholderText('名前・電話番号・LINE ID で検索...'), {
      target: { value: '山田' },
    });
    fireEvent.change(screen.getByPlaceholderText('区分'), { target: { value: '常連' } });
    fireEvent.click(screen.getByRole('button', { name: '検索' }));

    await waitFor(() =>
      expect(mockedCustomerApi.list).toHaveBeenLastCalledWith({
        page: 0,
        size: 20,
        sort: 'createdAt,id,desc',
        search: '山田',
        classification: '常連',
      })
    );
  });

  it('統合権限が無ければ重複候補への導線を出さないこと', async () => {
    // 強制はサーバ側。ここは押しても 403 になる導線を描かないための出し分け
    mockedReadClaims.mockReturnValue({ authorities: [], userType: 'STAFF', storeBridge: true });

    render(<CustomersPage />);
    await screen.findByText('顧客が登録されていません');

    expect(screen.queryByRole('link', { name: '重複候補' })).not.toBeInTheDocument();
  });

  it('統合権限を持つ利用者には重複候補への導線を出すこと', async () => {
    mockedReadClaims.mockReturnValue({
      authorities: ['PERM_CUSTOMER_MERGE'],
      userType: 'STAFF',
      storeBridge: true,
    });

    render(<CustomersPage />);
    await screen.findByText('顧客が登録されていません');

    expect(screen.getByRole('link', { name: '重複候補' })).toHaveAttribute(
      'href',
      '/store/1/customers/duplicates'
    );
  });
});

describe('顧客一覧からの統合', () => {
  /** 一覧の行は「絞り込んで選ぶ」ための項目しか持たない（住所も受注件数も無い）。 */
  const listRows = [
    { id: 'c1', name: '山田太郎', phone_number: '090-1111-2222' },
    { id: 'c2', name: 'ヤマダタロウ', phone_number: '090-1111-2222' },
    { id: 'c3', name: '別人三郎', phone_number: '090-3333-4444' },
  ];

  /** 見比べの読み口が返す 2 行。一覧に無い材料（住所・受注件数・紐づけ）を持つ。 */
  const comparisonPair = [
    {
      id: 'c1',
      name: '山田太郎',
      phone_number: '090-1111-2222',
      address: '東京都渋谷区1-1',
      member_linked: false,
      order_count: 3,
    },
    {
      id: 'c2',
      name: 'ヤマダタロウ',
      phone_number: '090-1111-2222',
      address: '東京都新宿区2-2',
      member_linked: true,
      order_count: 0,
    },
  ];

  beforeEach(() => {
    jest.clearAllMocks();
    mockedReadClaims.mockReturnValue({
      authorities: ['PERM_CUSTOMER_MERGE', 'PERM_CUSTOMER_MANAGE'],
      userType: 'STAFF',
      storeBridge: true,
    });
    mockedCustomerApi.list.mockResolvedValue({
      rows: listRows,
      page: 0,
      pageCount: 1,
      total: listRows.length,
    } as never);
    mockedCustomerApi.mergeComparison.mockResolvedValue(comparisonPair as never);
    mockedCustomerApi.merge.mockResolvedValue({
      surviving_customer_id: 'c1',
      moved_order_count: 3,
      moved_link_count: 0,
    } as never);
  });

  /** 2 行を選び、見比べが出るところまで進める。 */
  async function selectPair() {
    fireEvent.click(await screen.findByLabelText('山田太郎 を見比べる'));
    fireEvent.click(screen.getByLabelText('ヤマダタロウ を見比べる'));
    return screen.findByRole('table', { name: '2 行の比較' });
  }

  it('統合権限が無ければ選択列を出さないこと', async () => {
    // 強制はサーバ側（読み口も実行も CUSTOMER_MERGE）。ここは押しても 403 になる導線を描かない
    mockedReadClaims.mockReturnValue({ authorities: [], userType: 'STAFF', storeBridge: true });

    render(<CustomersPage />);
    await screen.findByText('山田太郎');

    expect(screen.queryByLabelText('山田太郎 を見比べる')).not.toBeInTheDocument();
  });

  it('2 行を選ぶと、一覧が持たない材料を専用の読み口から引いて並べること', async () => {
    render(<CustomersPage />);
    const comparison = await selectPair();

    expect(mockedCustomerApi.mergeComparison).toHaveBeenCalledWith('c1', 'c2');
    // 住所も受注件数も一覧の型には無い。別人かどうかの判断はここで分かれる
    expect(within(comparison).getByText('東京都渋谷区1-1')).toBeInTheDocument();
    expect(within(comparison).getByText('東京都新宿区2-2')).toBeInTheDocument();
    expect(within(comparison).getByText('3 件')).toBeInTheDocument();
    expect(within(comparison).getByText('紐づけ済み')).toBeInTheDocument();
  });

  it('3 行目の選択を塞ぎ、まとめて畳む導線を持たないこと', async () => {
    render(<CustomersPage />);
    await selectPair();

    // 3 行以上を一度に畳む導線は持たない（ADR 0010）。Base UI の Checkbox は span なので
    // disabled 属性ではなく aria-disabled で表れる
    const third = screen.getByLabelText('別人三郎 を見比べる');
    expect(third).toHaveAttribute('aria-disabled', 'true');
    fireEvent.click(third);
    expect(screen.getByText(/2 件を選択中/)).toBeInTheDocument();
    expect(third).toHaveAttribute('aria-checked', 'false');
    expect(
      screen.queryByRole('button', { name: /まとめて統合|一括|自動統合/ })
    ).not.toBeInTheDocument();
  });

  it('存続行を選ぶまでは統合できないこと', async () => {
    render(<CustomersPage />);
    await selectPair();

    expect(screen.getByText('台帳に残す行を選んでください。')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '統合する' })).toBeDisabled();
  });

  it('確認を経てから統合し、成功後は選択を捨てて一覧を取り直すこと', async () => {
    render(<CustomersPage />);
    await selectPair();
    fireEvent.click(screen.getByLabelText('山田太郎 を残す'));
    fireEvent.click(screen.getByRole('button', { name: '統合する' }));

    // 「統合する」は確認を開くだけ。ここで走ってしまうと取り返しがつかない
    expect(await screen.findByText('顧客を統合しますか？')).toBeInTheDocument();
    expect(screen.getByText(/統合は取り消せません/)).toBeInTheDocument();
    // 転記の期限は「今」。統合後は被統合行にしかない値を読む経路が無い
    expect(screen.getByText(/統合後どこからも読めなくなります/)).toBeInTheDocument();
    expect(mockedCustomerApi.merge).not.toHaveBeenCalled();

    const dialog = screen.getByRole('dialog');
    fireEvent.click(within(dialog).getByRole('button', { name: '統合する' }));

    await waitFor(() => expect(mockedCustomerApi.merge).toHaveBeenCalledWith('c1', 'c2'));
    // 畳んだ行が選ばれたままだと、次の操作が既に墓標の行を指す
    await waitFor(() => expect(screen.queryByText(/件を選択中/)).not.toBeInTheDocument());
    expect(mockedCustomerApi.list).toHaveBeenCalledTimes(2);
  });

  it('統合の実行中は選択を変えられないこと', async () => {
    // 実行中に選択が変わると見比べる区画ごと消え、取り返しのつかない操作の確認が在途のまま
    // 画面から失せる
    let finishMerge = () => {};
    mockedCustomerApi.merge.mockReturnValue(
      new Promise(resolve => {
        finishMerge = () =>
          resolve({ surviving_customer_id: 'c1', moved_order_count: 3, moved_link_count: 0 });
      }) as never
    );

    render(<CustomersPage />);
    await selectPair();
    fireEvent.click(screen.getByLabelText('山田太郎 を残す'));
    fireEvent.click(screen.getByRole('button', { name: '統合する' }));
    const dialog = await screen.findByRole('dialog');
    fireEvent.click(within(dialog).getByRole('button', { name: '統合する' }));
    await waitFor(() => expect(mockedCustomerApi.merge).toHaveBeenCalled());

    const selected = screen.getByLabelText('ヤマダタロウ を見比べる');
    expect(selected).toHaveAttribute('aria-disabled', 'true');
    fireEvent.click(selected);
    expect(screen.getByRole('dialog')).toBeInTheDocument();

    finishMerge();
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
  });

  it('選択がページ送りを跨いでも残ること', async () => {
    // 統合したい 2 行が同じページに並ぶとは限らない。選択が今見えている行に依存していると、
    // 桁外れのグループは一生統合できない
    mockedCustomerApi.list
      .mockResolvedValueOnce({ rows: listRows, page: 0, pageCount: 2, total: 21 } as never)
      .mockResolvedValueOnce({
        rows: [{ id: 'c9', name: '次頁太郎' }],
        page: 1,
        pageCount: 2,
        total: 21,
      } as never);

    render(<CustomersPage />);
    fireEvent.click(await screen.findByLabelText('山田太郎 を見比べる'));
    fireEvent.click(screen.getByRole('button', { name: '2' }));

    expect(await screen.findByText('次頁太郎')).toBeInTheDocument();
    expect(screen.queryByText('山田太郎')).not.toBeInTheDocument();
    expect(screen.getByText(/1 件を選択中/)).toBeInTheDocument();
  });

  it('見比べる行が引けない 404 では、再試行ではなく選び直しを出すこと', async () => {
    // 他者の統合が先に確定した場合。押しても永久に成功しない再試行を出さない
    mockedCustomerApi.mergeComparison.mockRejectedValue({ response: { status: 404 } });

    render(<CustomersPage />);
    fireEvent.click(await screen.findByLabelText('山田太郎 を見比べる'));
    fireEvent.click(screen.getByLabelText('ヤマダタロウ を見比べる'));

    const failed = await screen.findByRole('alert');
    expect(within(failed).getByText(/選んだ顧客が見つかりません/)).toBeInTheDocument();
    expect(within(failed).queryByRole('button', { name: '再試行' })).not.toBeInTheDocument();
    // 出口は選び直し。区画そのものが持っていないと、行き止まりのまま画面に残る
    fireEvent.click(within(failed).getByRole('button', { name: '選択を解除' }));
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  });
});
