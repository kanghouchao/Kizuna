import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { notify } from '@/shared/notify';
import { platformRoleApi, platformStaffApi } from '@/entities/user';
import { StaffCreateModal } from '../StaffCreateModal';

jest.mock('@/entities/user', () => ({
  platformRoleApi: { list: jest.fn() },
  platformStaffApi: { create: jest.fn() },
}));

jest.mock('@/shared/notify', () => ({
  notify: { success: jest.fn(), error: jest.fn(), warning: jest.fn() },
}));

const mockedStaffApi = platformStaffApi as jest.Mocked<typeof platformStaffApi>;
const mockedRoleApi = platformRoleApi as jest.Mocked<typeof platformRoleApi>;
const mockedNotify = notify as jest.Mocked<typeof notify>;

const stores = [{ id: 9, name: '店舗A' }];

const renderModal = (props: Partial<React.ComponentProps<typeof StaffCreateModal>> = {}) => {
  const onClose = jest.fn();
  const onCreated = jest.fn();
  const onReloadStores = jest.fn();
  render(
    <StaffCreateModal
      stores={stores}
      storesLoading={false}
      storesFailed={false}
      onReloadStores={onReloadStores}
      onClose={onClose}
      onCreated={onCreated}
      {...props}
    />
  );
  return { onClose, onCreated, onReloadStores };
};

const fillBasics = () => {
  fireEvent.change(screen.getByLabelText('メールアドレス'), {
    target: { value: 'new@example.com' },
  });
  fireEvent.change(screen.getByLabelText('初期パスワード'), { target: { value: 'secret' } });
  fireEvent.change(screen.getByLabelText('氏名'), { target: { value: '佐藤次郎' } });
};

describe('管理者新規作成モーダル', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    mockedRoleApi.list.mockResolvedValue([
      { id: 3, name: '店長', system: true, permission_count: 0 },
      { id: 4, name: '経理', system: false, permission_count: 0 },
    ]);
    mockedStaffApi.create.mockResolvedValue({} as never);
  });

  it('mount 時（= 開いた時点）にロール目録を取得する', async () => {
    renderModal();

    await screen.findByLabelText('店長');
    expect(mockedRoleApi.list).toHaveBeenCalledTimes(1);
  });

  it('入力値と選択状態を snake_case のまま作成 API へ送る', async () => {
    renderModal();
    await screen.findByLabelText('店長');

    fillBasics();
    fireEvent.click(screen.getByLabelText('店長'));
    fireEvent.click(screen.getByRole('button', { name: '追加する' }));

    await waitFor(() => expect(mockedStaffApi.create).toHaveBeenCalledTimes(1));
    expect(mockedStaffApi.create.mock.calls[0][0]).toEqual({
      email: 'new@example.com',
      password: 'secret',
      display_name: '佐藤次郎',
      role_ids: [3],
      store_scope_type: 'ALL_STORES',
      store_ids: [],
    });
  });

  it('個別店舗を選ぶと店舗集合が SPECIFIC_STORES と店舗 id で送られる', async () => {
    renderModal();
    await screen.findByLabelText('店長');

    fillBasics();
    fireEvent.click(screen.getByLabelText('店長'));
    fireEvent.click(screen.getByLabelText('個別店舗'));
    fireEvent.click(await screen.findByLabelText('店舗A'));
    fireEvent.click(screen.getByRole('button', { name: '追加する' }));

    await waitFor(() => expect(mockedStaffApi.create).toHaveBeenCalledTimes(1));
    expect(mockedStaffApi.create.mock.calls[0][0]).toMatchObject({
      store_scope_type: 'SPECIFIC_STORES',
      store_ids: [9],
    });
  });

  it('ロールが未選択なら、その組の傍に文言を出し作成 API を呼ばない', async () => {
    renderModal();
    await screen.findByLabelText('店長');

    fillBasics();
    const submitButton = screen.getByRole('button', { name: '追加する' });
    fireEvent.click(submitButton);

    const message = await screen.findByText('ロールを 1 つ以上選択してください');
    expect(mockedNotify.error).not.toHaveBeenCalled();
    expect(mockedStaffApi.create).not.toHaveBeenCalled();
    // 必須は「N のうち 1 つ以上」＝組の性質なので、指摘も個々の項目ではなく組に紐づく
    const group = screen.getByRole('group', { name: 'ロール' });
    expect(group).toHaveAttribute('aria-invalid', 'true');
    expect(group.getAttribute('aria-describedby')).toContain(message.id);
    expect(submitButton).toBeEnabled();
    // handleSubmit は登録済みの ref を焦点にする。組の先頭まで ref が届いていないと、
    // 他の症状を出さずに焦点移動だけが失われる
    expect(document.activeElement).toBe(screen.getByLabelText('店長'));
  });

  it('必須項目が空なら各欄の傍に文言を出し作成 API を呼ばない', async () => {
    renderModal();
    await screen.findByLabelText('店長');

    fireEvent.click(screen.getByLabelText('店長'));
    fireEvent.click(screen.getByRole('button', { name: '追加する' }));

    expect(await screen.findByText('メールアドレスを入力してください')).toBeInTheDocument();
    expect(screen.getByText('初期パスワードを入力してください')).toBeInTheDocument();
    expect(screen.getByText('氏名を入力してください')).toBeInTheDocument();
    expect(mockedStaffApi.create).not.toHaveBeenCalled();
  });

  // noValidate で type="email" の執行が止まるため、同じ検査を規則が引き継ぐ
  it('メールアドレスの形式が不正なら文言を出し作成 API を呼ばない', async () => {
    renderModal();
    await screen.findByLabelText('店長');

    fillBasics();
    fireEvent.change(screen.getByLabelText('メールアドレス'), {
      target: { value: 'not-an-email' },
    });
    fireEvent.click(screen.getByLabelText('店長'));
    fireEvent.click(screen.getByRole('button', { name: '追加する' }));

    expect(await screen.findByText('メールアドレスの形式が正しくありません')).toBeInTheDocument();
    expect(mockedStaffApi.create).not.toHaveBeenCalled();
  });

  it('作成成功で onCreated と onClose を呼ぶ', async () => {
    const { onClose, onCreated } = renderModal();
    await screen.findByLabelText('店長');

    fillBasics();
    fireEvent.click(screen.getByLabelText('店長'));
    fireEvent.click(screen.getByRole('button', { name: '追加する' }));

    await waitFor(() => expect(onCreated).toHaveBeenCalledTimes(1));
    expect(mockedNotify.success).toHaveBeenCalledWith('管理者を追加しました');
    expect(onClose).toHaveBeenCalledTimes(1);
  });

  it('キャンセルは作成せず閉じる', async () => {
    const { onClose } = renderModal();
    await screen.findByLabelText('店長');

    fireEvent.click(screen.getByRole('button', { name: 'キャンセル' }));

    expect(onClose).toHaveBeenCalledTimes(1);
    expect(mockedStaffApi.create).not.toHaveBeenCalled();
  });

  // 自動再試行が尽きても、モーダルを閉じ直さずに目録を取り直せる導線を残す
  // （空の SPECIFIC_STORES はサーバが 400 で拒むため、空のままでは正しい提出ができない）
  it('店舗目録が空のとき、個別店舗の欄に再読み込み導線を出す', async () => {
    const { onReloadStores } = renderModal({ stores: [] });
    await screen.findByLabelText('店長');

    fireEvent.click(screen.getByLabelText('個別店舗'));
    fireEvent.click(await screen.findByRole('button', { name: '再読み込み' }));

    expect(onReloadStores).toHaveBeenCalledTimes(1);
  });

  // 「選択肢がありません」と言い切ると、読めなかっただけの状態が目録の事実に化ける
  it('店舗目録の取得に失敗したら、個別店舗の欄が失敗を名乗り再試行できる', async () => {
    const { onReloadStores } = renderModal({ stores: [], storesFailed: true });
    await screen.findByLabelText('店長');

    fireEvent.click(screen.getByLabelText('個別店舗'));

    expect(await screen.findByRole('alert')).toHaveTextContent('店舗一覧の取得に失敗しました');
    expect(screen.queryByText('店舗の選択肢がありません。')).not.toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: '再試行' }));
    expect(onReloadStores).toHaveBeenCalledTimes(1);
  });

  it('ロール目録の取得に失敗したら、その組が失敗を名乗り再試行できる', async () => {
    // 空の組は「ロールが 1 つも無い」に見える
    mockedRoleApi.list.mockRejectedValueOnce(new Error('network'));
    mockedRoleApi.list.mockResolvedValueOnce([
      { id: 3, name: '店長', system: true, permission_count: 0 },
    ]);
    renderModal();

    const group = await screen.findByRole('group', { name: 'ロール' });
    expect(await within(group).findByRole('alert')).toHaveTextContent(
      'ロール一覧の取得に失敗しました'
    );

    fireEvent.click(within(group).getByRole('button', { name: '再試行' }));

    expect(await screen.findByLabelText('店長')).toBeInTheDocument();
    expect(within(group).queryByRole('alert')).not.toBeInTheDocument();
  });

  // 閉じると unmount で isSubmitting が消え、開き直した複製から二重送信できてしまうため、
  // 送信中はキャンセルも Escape も閉じない
  it('送信中は閉じられない', async () => {
    mockedStaffApi.create.mockImplementationOnce(() => new Promise(() => {}));
    const { onClose } = renderModal();
    await screen.findByLabelText('店長');

    fillBasics();
    fireEvent.click(screen.getByLabelText('店長'));
    fireEvent.click(screen.getByRole('button', { name: '追加する' }));
    await screen.findByRole('button', { name: '追加中...' });

    expect(screen.getByRole('button', { name: 'キャンセル' })).toBeDisabled();
    fireEvent.keyDown(document, { key: 'Escape' });
    expect(onClose).not.toHaveBeenCalled();
  });
});
