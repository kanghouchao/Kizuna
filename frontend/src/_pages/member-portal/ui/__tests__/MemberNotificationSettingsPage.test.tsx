import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { MemberNotificationSettingsPage } from '../MemberNotificationSettingsPage';
import { memberApi } from '@/entities/member';
import { notify } from '@/shared/notify';

jest.mock('@/entities/member', () => ({
  memberApi: { notificationSettings: jest.fn(), chooseNotification: jest.fn() },
}));

jest.mock('@/shared/notify', () => ({
  notify: { success: jest.fn(), error: jest.fn(), warning: jest.fn() },
}));

const mockedSettings = memberApi.notificationSettings as jest.Mock;
const mockedChoose = memberApi.chooseNotification as jest.Mock;

function group(name: string) {
  return screen.getByRole('radiogroup', { name });
}

describe('MemberNotificationSettingsPage', () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  it('未確認の用途はどちらも選ばれておらず、未選択であることを示す', async () => {
    mockedSettings.mockResolvedValue({ business_status: 'UNKNOWN', marketing_status: 'DENIED' });

    render(<MemberNotificationSettingsPage />);

    const business = await screen.findByRole('radiogroup', { name: 'ご利用に関するお知らせ' });
    expect(within(business).getByRole('radio', { name: '受け取る' })).not.toBeChecked();
    expect(within(business).getByRole('radio', { name: '受け取らない' })).not.toBeChecked();
    expect(screen.getAllByText('まだ選択されていません。')).toHaveLength(1);
    const marketing = group('キャンペーン・おすすめのご案内');
    expect(within(marketing).getByRole('radio', { name: '受け取らない' })).toBeChecked();
    expect(
      screen.getByRole('heading', { name: 'ご利用に関するお知らせ', level: 2 })
    ).toBeInTheDocument();
  });

  it('選んだ時点で保存し、応答の値を映して成功を通知する', async () => {
    mockedSettings.mockResolvedValue({ business_status: 'UNKNOWN', marketing_status: 'UNKNOWN' });
    mockedChoose.mockResolvedValue({ business_status: 'ALLOWED', marketing_status: 'UNKNOWN' });

    render(<MemberNotificationSettingsPage />);

    const business = await screen.findByRole('radiogroup', { name: 'ご利用に関するお知らせ' });
    fireEvent.click(within(business).getByRole('radio', { name: '受け取る' }));

    await waitFor(() =>
      expect(within(business).getByRole('radio', { name: '受け取る' })).toBeChecked()
    );
    expect(mockedChoose).toHaveBeenCalledWith('BUSINESS', 'ALLOWED');
    expect(notify.success).toHaveBeenCalledWith('通知の設定を保存しました');
    expect(screen.getAllByText('まだ選択されていません。')).toHaveLength(1);
  });

  it('保存に失敗したら選択を保存済みの値に戻し、失敗を通知する', async () => {
    mockedSettings.mockResolvedValue({ business_status: 'DENIED', marketing_status: 'UNKNOWN' });
    mockedChoose.mockRejectedValue(new Error('boom'));

    render(<MemberNotificationSettingsPage />);

    const business = await screen.findByRole('radiogroup', { name: 'ご利用に関するお知らせ' });
    fireEvent.click(within(business).getByRole('radio', { name: '受け取る' }));

    await waitFor(() =>
      expect(notify.warning).toHaveBeenCalledWith('通知の設定を保存できませんでした')
    );
    expect(within(business).getByRole('radio', { name: '受け取らない' })).toBeChecked();
    expect(within(business).getByRole('radio', { name: '受け取る' })).not.toBeChecked();
    expect(notify.success).not.toHaveBeenCalled();
  });

  it('取得に失敗したら領域で失敗を名乗り、再試行で復帰できる', async () => {
    mockedSettings.mockRejectedValueOnce(new Error('boom'));
    mockedSettings.mockResolvedValueOnce({
      business_status: 'ALLOWED',
      marketing_status: 'UNKNOWN',
    });

    render(<MemberNotificationSettingsPage />);

    const region = await screen.findByRole('alert');
    expect(within(region).getByText('通知の設定を取得できませんでした')).toBeInTheDocument();
    fireEvent.click(within(region).getByRole('button', { name: '再試行' }));

    const business = await screen.findByRole('radiogroup', { name: 'ご利用に関するお知らせ' });
    expect(within(business).getByRole('radio', { name: '受け取る' })).toBeChecked();
  });

  it('取得完了までは読み込み中を表示する', () => {
    mockedSettings.mockReturnValue(new Promise(() => {}));

    render(<MemberNotificationSettingsPage />);

    expect(screen.getByText('読み込み中...')).toBeInTheDocument();
  });
});
