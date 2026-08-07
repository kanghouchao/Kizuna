import { act, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { toast } from 'react-hot-toast';
import type { PermissionResponse, RoleResponse } from '@/entities/user';
import { platformRoleApi } from '@/entities/user';
import { RoleFormModal } from '../RoleFormModal';

jest.mock('@/entities/user', () => ({
  platformRoleApi: {
    get: jest.fn(),
    create: jest.fn(),
    update: jest.fn(),
    permissions: jest.fn(),
  },
}));

jest.mock('react-hot-toast', () => ({
  toast: { success: jest.fn(), error: jest.fn() },
}));

const mockedRoleApi = platformRoleApi as jest.Mocked<typeof platformRoleApi>;
const mockedToast = toast as jest.Mocked<typeof toast>;

const role = (override: Partial<RoleResponse> = {}): RoleResponse => ({
  id: 5,
  name: '受付担当',
  system: false,
  permissions: ['ORDER_MANAGE'],
  version: 3,
  ...override,
});

const renderModal = (props: Partial<React.ComponentProps<typeof RoleFormModal>> = {}) => {
  const onClose = jest.fn();
  const onSaved = jest.fn();
  render(<RoleFormModal editingId={5} onClose={onClose} onSaved={onSaved} {...props} />);
  return { onClose, onSaved };
};

describe('ロール編集モーダル', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    mockedRoleApi.permissions.mockResolvedValue([
      { code: 'ORDER_MANAGE', console: 'STORE' },
      { code: 'CUSTOMER_MANAGE', console: 'STORE' },
      { code: 'STAFF_MANAGE', console: 'PLATFORM' },
    ]);
    mockedRoleApi.get.mockResolvedValue(role());
    mockedRoleApi.update.mockResolvedValue({} as never);
    mockedRoleApi.create.mockResolvedValue({} as never);
  });

  // 一覧は権限個数までの要約しか持たないため、編集の中身は id での個別取得が正
  it('編集は詳細を id で取得してフォームを初期化する', async () => {
    renderModal();

    expect(await screen.findByLabelText('ORDER_MANAGE')).toBeChecked();
    expect(mockedRoleApi.get).toHaveBeenCalledWith(5);
    expect(screen.getByLabelText('ロール名')).toHaveValue('受付担当');
  });

  it('詳細取得に失敗したら読み込み中に固着せず、再試行で回復できる', async () => {
    // 失敗のまま「読み込み中...」を出し続けると、閉じて開き直す以外の回復手段が無くなる
    mockedRoleApi.get.mockRejectedValueOnce({ response: { status: 500 } });
    renderModal();

    expect(await screen.findByText('ロール情報の取得に失敗しました')).toBeInTheDocument();
    expect(screen.queryByText('読み込み中...')).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: '保存する' })).toBeDisabled();

    fireEvent.click(screen.getByRole('button', { name: '再試行' }));

    expect(await screen.findByLabelText('ORDER_MANAGE')).toBeChecked();
    expect(screen.getByLabelText('ロール名')).toHaveValue('受付担当');
    expect(screen.getByRole('button', { name: '保存する' })).toBeEnabled();
  });

  it('詳細が届くまで名称入力を無効化する（到着時の reset が入力を上書きしないように）', async () => {
    let resolveGet: (r: RoleResponse) => void = () => {};
    mockedRoleApi.get.mockImplementationOnce(
      () =>
        new Promise<RoleResponse>(resolve => {
          resolveGet = resolve;
        })
    );
    renderModal();

    expect(screen.getByLabelText('ロール名')).toBeDisabled();

    await act(async () => resolveGet(role()));

    expect(screen.getByLabelText('ロール名')).toBeEnabled();
    expect(screen.getByLabelText('ロール名')).toHaveValue('受付担当');
  });

  it('409 の取り直し中は保存を無効化し、完了後は新しい version で送る', async () => {
    // 取り直し完了前に保存が押せると、陳腐な version の再送で同じ 409 を繰り返す
    mockedRoleApi.update.mockRejectedValueOnce({ response: { status: 409 } });
    let resolveReload: (r: RoleResponse) => void = () => {};
    mockedRoleApi.get.mockResolvedValueOnce(role()).mockImplementationOnce(
      () =>
        new Promise<RoleResponse>(resolve => {
          resolveReload = resolve;
        })
    );
    renderModal();
    await screen.findByLabelText('ORDER_MANAGE');

    fireEvent.click(screen.getByRole('button', { name: '保存する' }));

    await waitFor(() => expect(mockedRoleApi.get).toHaveBeenCalledTimes(2));
    expect(screen.getByRole('button', { name: '保存する' })).toBeDisabled();

    await act(async () => resolveReload(role({ version: 4 })));

    await waitFor(() => expect(screen.getByRole('button', { name: '保存する' })).toBeEnabled());
    fireEvent.click(screen.getByRole('button', { name: '保存する' }));

    await waitFor(() => expect(mockedRoleApi.update).toHaveBeenCalledTimes(2));
    expect(mockedRoleApi.update.mock.calls[1][1]).toEqual(expect.objectContaining({ version: 4 }));
  });

  it('権限目録を console ごとに見出し付きで並べる', async () => {
    renderModal();

    expect(await screen.findByText('プラットフォーム')).toBeInTheDocument();
    expect(screen.getByText('店舗')).toBeInTheDocument();
    // 権限ラベルはバックエンドのコードをそのまま出す（日本語名は持たない）
    expect(screen.getByLabelText('ORDER_MANAGE')).toBeChecked();
    expect(screen.getByLabelText('CUSTOMER_MANAGE')).not.toBeChecked();
  });

  it('未知の console の権限も落とさず、コードそのままの見出しで末尾に出す', async () => {
    // 組はこちらの表ではなく目録から作る。表で作ると、バックエンドが Console を
    // 増やした日にその権限が静かに画面から消える（付与できなくなる）。
    // 型に無い値を敢えて流す（この試験の主題が「フロントがまだ知らない値」そのもののため）
    mockedRoleApi.permissions.mockResolvedValue([
      { code: 'ORDER_MANAGE', console: 'STORE' },
      { code: 'FUTURE_MANAGE', console: 'BILLING' } as unknown as PermissionResponse,
    ]);
    renderModal();

    expect(await screen.findByLabelText('FUTURE_MANAGE')).toBeInTheDocument();
    expect(screen.getByText('BILLING')).toBeInTheDocument();
  });

  it('編集保存は詳細応答の version を往復する', async () => {
    renderModal();
    await screen.findByLabelText('ORDER_MANAGE');

    fireEvent.click(screen.getByRole('button', { name: '保存する' }));

    await waitFor(() => expect(mockedRoleApi.update).toHaveBeenCalledTimes(1));
    expect(mockedRoleApi.update.mock.calls[0][0]).toBe(5);
    expect(mockedRoleApi.update.mock.calls[0][1]).toEqual({
      name: '受付担当',
      permissions: ['ORDER_MANAGE'],
      version: 3,
    });
  });

  it('新規作成は詳細を取得せず、version も送らない', async () => {
    renderModal({ editingId: null });
    await screen.findByLabelText('ORDER_MANAGE');

    fireEvent.change(screen.getByLabelText('ロール名'), { target: { value: '新ロール' } });
    fireEvent.click(screen.getByLabelText('CUSTOMER_MANAGE'));
    fireEvent.click(screen.getByRole('button', { name: '保存する' }));

    await waitFor(() => expect(mockedRoleApi.create).toHaveBeenCalledTimes(1));
    expect(mockedRoleApi.create.mock.calls[0][0]).toEqual({
      name: '新ロール',
      permissions: ['CUSTOMER_MANAGE'],
    });
    expect(mockedRoleApi.get).not.toHaveBeenCalled();
  });

  // 閉じると unmount で isSubmitting が消え、開き直した複製から二重送信できてしまうため、
  // 送信中はキャンセルも Escape も閉じない
  it('送信中は閉じられない', async () => {
    mockedRoleApi.update.mockImplementationOnce(() => new Promise(() => {}));
    const { onClose } = renderModal();
    await screen.findByLabelText('ORDER_MANAGE');

    fireEvent.click(screen.getByRole('button', { name: '保存する' }));
    await screen.findByRole('button', { name: '保存中...' });

    expect(screen.getByRole('button', { name: 'キャンセル' })).toBeDisabled();
    fireEvent.keyDown(document, { key: 'Escape' });
    expect(onClose).not.toHaveBeenCalled();
  });

  it('権限を全て外すと、その組の傍に文言を出し保存 API を呼ばない', async () => {
    renderModal();
    await screen.findByLabelText('ORDER_MANAGE');

    fireEvent.click(screen.getByLabelText('ORDER_MANAGE'));
    const submitButton = screen.getByRole('button', { name: '保存する' });
    fireEvent.click(submitButton);

    const message = await screen.findByText('権限を 1 つ以上選択してください');
    expect(mockedToast.error).not.toHaveBeenCalled();
    expect(mockedRoleApi.update).not.toHaveBeenCalled();
    // 必須は「N のうち 1 つ以上」＝組の性質なので、指摘も個々の項目ではなく組に紐づく
    const group = screen.getByRole('group', { name: '権限' });
    expect(group).toHaveAttribute('aria-invalid', 'true');
    expect(group.getAttribute('aria-describedby')).toContain(message.id);
    expect(submitButton).toBeEnabled();
    // handleSubmit は登録済みの ref を焦点にする。組の先頭まで ref が届いていないと、
    // 他の症状を出さずに焦点移動だけが失われる
    expect(document.activeElement).toBe(screen.getByLabelText('STAFF_MANAGE'));
  });

  it('ロール名が空なら欄の傍に文言を出し保存 API を呼ばない', async () => {
    renderModal({ editingId: null });
    await screen.findByLabelText('ORDER_MANAGE');

    fireEvent.click(screen.getByLabelText('ORDER_MANAGE'));
    fireEvent.click(screen.getByRole('button', { name: '保存する' }));

    expect(await screen.findByText('ロール名を入力してください')).toBeInTheDocument();
    expect(mockedRoleApi.create).not.toHaveBeenCalled();
  });

  it('409 は詳細と一覧を取り直してモーダルを開いたままにする（version 固着で詰まないこと）', async () => {
    // 詳細を取り直さないと古い version を抱えたままになり、再試行が同じ 409 を繰り返す。
    mockedRoleApi.update.mockRejectedValue({ response: { status: 409 } });
    const { onClose, onSaved } = renderModal();
    await screen.findByLabelText('ORDER_MANAGE');

    fireEvent.click(screen.getByRole('button', { name: '保存する' }));

    await waitFor(() =>
      expect(mockedToast.error).toHaveBeenCalledWith(
        '他の管理者が更新しました。最新の内容を確認してください'
      )
    );
    expect(onSaved).toHaveBeenCalledTimes(1);
    await waitFor(() => expect(mockedRoleApi.get).toHaveBeenCalledTimes(2));
    expect(onClose).not.toHaveBeenCalled();
    expect(screen.getByRole('dialog')).toBeInTheDocument();
  });

  it('409 以外の失敗は再取得せずサーバ文言を出す', async () => {
    mockedRoleApi.update.mockRejectedValue({
      response: { status: 400, data: { error: 'このロール名は既に使われています' } },
    });
    const { onClose, onSaved } = renderModal();
    await screen.findByLabelText('ORDER_MANAGE');

    fireEvent.click(screen.getByRole('button', { name: '保存する' }));

    await waitFor(() =>
      expect(mockedToast.error).toHaveBeenCalledWith('このロール名は既に使われています')
    );
    expect(onSaved).not.toHaveBeenCalled();
    expect(onClose).not.toHaveBeenCalled();
  });
});
