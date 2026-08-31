import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import Cookies from 'js-cookie';
import { EmergencyElevationSummary, emergencyElevationApi } from '@/entities/user';
import EmergencyElevationsPage from '../ui/EmergencyElevationsPage';

jest.mock('@/entities/user', () => ({
  emergencyElevationApi: { activate: jest.fn(), list: jest.fn(), revoke: jest.fn() },
  platformAuthApi: { stores: jest.fn(async () => [{ id: 1, name: '店舗A' }]) },
}));

jest.mock('@/shared/notify', () => ({
  notify: { success: jest.fn(), error: jest.fn(), warning: jest.fn() },
}));

const mockedApi = emergencyElevationApi as jest.Mocked<typeof emergencyElevationApi>;

const row = (override: Partial<EmergencyElevationSummary>): EmergencyElevationSummary => ({
  id: 1,
  activated_by_name: 'HQ管理者',
  target_store_id: 1,
  store_name: '店舗A',
  reason: '締め処理の代行',
  activated_at: '2026-08-31T10:00:00+09:00',
  expires_at: '2026-08-31T11:00:00+09:00',
  status: 'ACTIVE',
  ...override,
});

function pageOf(rows: EmergencyElevationSummary[]) {
  return { rows, nextCursor: null };
}

async function fillActivationForm(values: { reason?: string; password?: string }) {
  fireEvent.click(await screen.findByRole('combobox', { name: '対象店舗' }));
  const option = await screen.findByRole('option', { name: '店舗A' });
  fireEvent.pointerDown(option);
  fireEvent.click(option);
  if (values.reason !== undefined) {
    fireEvent.change(screen.getByLabelText('発動の理由'), { target: { value: values.reason } });
  }
  if (values.password !== undefined) {
    fireEvent.change(screen.getByLabelText('パスワード（再入力）'), {
      target: { value: values.password },
    });
  }
}

describe('緊急昇格ページ', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    Cookies.remove('token');
    mockedApi.list.mockResolvedValue(pageOf([row({})]));
  });

  it('理由が空のままでは発動フォームを提出できないこと', async () => {
    render(<EmergencyElevationsPage />);
    await fillActivationForm({ password: 'secret' });

    fireEvent.click(screen.getByRole('button', { name: '発動する' }));

    expect(await screen.findByText('発動の理由を入力してください')).toBeInTheDocument();
    expect(mockedApi.activate).not.toHaveBeenCalled();
  });

  it('発動成功でトークンを差し替え、有効期限を頁上に明示すること', async () => {
    // 固定時刻だと実行日によっては過去になり、書いた瞬間に cookie が失効して偽赤になる
    const expiresAt = Date.now() + 60 * 60 * 1000;
    mockedApi.activate.mockResolvedValue({ id: 9, token: 'elevated-jwt', expires_at: expiresAt });
    render(<EmergencyElevationsPage />);
    await fillActivationForm({ reason: '店長失聯のため締め処理を代行', password: 'secret' });

    fireEvent.click(screen.getByRole('button', { name: '発動する' }));

    await waitFor(() =>
      expect(mockedApi.activate).toHaveBeenCalledWith({
        store_id: 1,
        reason: '店長失聯のため締め処理を代行',
        password: 'secret',
      })
    );
    // 会話が昇格トークンへ切り替わる（以後の要求は昇格 claim で飛ぶ）
    await waitFor(() => expect(Cookies.get('token')).toBe('elevated-jwt'));
    // 期限の明示は数秒で消える通知ではなく、頁に残る面で行う
    expect(await screen.findByText(/まで有効です/)).toBeInTheDocument();
    expect(screen.getByRole('link', { name: '店舗コンソールへ入る' })).toBeInTheDocument();
    // 新しい発動が履歴に載るため取り直す
    await waitFor(() => expect(mockedApi.list).toHaveBeenCalledTimes(2));
  });

  it('履歴が発動者・店舗・理由・状態を並べ、撤回の口は有効な行にだけ出ること', async () => {
    mockedApi.list.mockResolvedValue(
      pageOf([
        row({}),
        row({ id: 2, reason: '期限切れの発動', status: 'EXPIRED' }),
        row({
          id: 3,
          reason: '撤回済みの発動',
          status: 'REVOKED',
          revoked_by_name: '別の管理者',
          revoked_at: '2026-08-31T10:30:00+09:00',
        }),
      ])
    );
    render(<EmergencyElevationsPage />);
    await screen.findByText('締め処理の代行');

    expect(screen.getByText('有効')).toBeInTheDocument();
    expect(screen.getByText('期限切れ')).toBeInTheDocument();
    expect(screen.getByText('撤回済み')).toBeInTheDocument();
    expect(screen.getByText(/別の管理者/)).toBeInTheDocument();
    // 期限切れ・撤回済みはサーバが撥ねるため、口ごと出さない
    expect(screen.getAllByRole('button', { name: '撤回' })).toHaveLength(1);
  });

  it('撤回は確認を挟んでから実行し、実行後は履歴を取り直すこと', async () => {
    mockedApi.revoke.mockResolvedValue(undefined);
    render(<EmergencyElevationsPage />);
    await screen.findByText('締め処理の代行');

    fireEvent.click(screen.getByRole('button', { name: '撤回' }));
    expect(mockedApi.revoke).not.toHaveBeenCalled();

    fireEvent.click(await screen.findByRole('button', { name: '撤回する' }));
    await waitFor(() => expect(mockedApi.revoke).toHaveBeenCalledWith(1));
    await waitFor(() => expect(mockedApi.list).toHaveBeenCalledTimes(2));
  });

  it('履歴の取得に失敗したら区画自身が失敗を名乗ること（空の履歴と見分けるため）', async () => {
    mockedApi.list.mockRejectedValue(new Error('network'));
    render(<EmergencyElevationsPage />);

    expect(await screen.findByText('発動履歴の取得に失敗しました')).toBeInTheDocument();
    expect(screen.queryByText('発動履歴がありません')).not.toBeInTheDocument();
  });
});
