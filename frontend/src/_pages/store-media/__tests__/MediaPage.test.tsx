import { act, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { MediaPage } from '..';
import { mediumApi, type MediumSummary } from '@/entities/medium';
import { useMe } from '@/entities/user';
import type { PermissionCode } from '@/entities/user';
import { notify } from '@/shared/notify';

jest.mock('@/widgets/admin-shell', () => ({
  DeniedPage: () => <p role="alert">権限の無い画面</p>,
}));

jest.mock('@/entities/user', () => ({
  ...jest.requireActual('@/entities/user'),
  useCurrentStoreId: () => '1',
}));
jest.mock('@/entities/medium', () => ({
  ...jest.requireActual('@/entities/medium/model/types'),
  mediumApi: {
    list: jest.fn(),
    create: jest.fn(),
    rename: jest.fn(),
    stop: jest.fn(),
    resume: jest.fn(),
    delete: jest.fn(),
  },
}));
jest.mock('@/entities/user/model/MeContext', () => ({
  ...jest.requireActual('@/entities/user/model/MeContext'),
  useMe: jest.fn(),
}));
jest.mock('@/shared/notify', () => ({
  notify: { success: jest.fn(), error: jest.fn(), warning: jest.fn() },
}));

const api = jest.mocked(mediumApi);
const medium: MediumSummary = { id: 'm1', name: '求人誌', status: 'ACTIVE', version: 3 };
const conflict = (error: string) => ({ response: { status: 409, data: { error } } });
const permission = (...permissions: PermissionCode[]) =>
  jest.mocked(useMe).mockReturnValue({
    me: {
      email: 'staff@example.com',
      user_type: 'STAFF',
      permissions,
      line_linked: false,
      email_verified: false,
    },
    isLoading: false,
    failure: null,
    reload: jest.fn(),
    setMe: jest.fn(),
  });

beforeEach(() => {
  jest.resetAllMocks();
  permission('ADVERTISING_COST_VIEW', 'ADVERTISING_COST_MANAGE');
  api.list.mockResolvedValue([medium]);
});

const openRename = async () => {
  fireEvent.click(await screen.findByRole('button', { name: '「求人誌」の名前を変更' }));
  return screen.findByRole('dialog');
};

test('閲覧権限が無ければ一覧を取りに行かない', () => {
  permission();
  render(<MediaPage />);
  expect(screen.getByRole('alert')).toHaveTextContent('権限の無い画面');
  expect(api.list).not.toHaveBeenCalled();
});

test('閲覧だけなら操作列を出さない', async () => {
  permission('ADVERTISING_COST_VIEW');
  render(<MediaPage />);
  expect(await screen.findByText('求人誌')).toBeVisible();
  expect(screen.queryByRole('button', { name: '媒体を登録' })).not.toBeInTheDocument();
  expect(screen.queryByRole('button', { name: /停止/ })).not.toBeInTheDocument();
});

test('停止の応答で行だけを差し替え、次の操作は応答の版を送る', async () => {
  api.stop.mockResolvedValue({ ...medium, status: 'STOPPED', version: 4 });
  api.resume.mockResolvedValue({ ...medium, version: 5 });
  render(<MediaPage />);
  fireEvent.click(await screen.findByRole('button', { name: '「求人誌」を停止' }));
  expect(await screen.findByText('停止中')).toBeVisible();
  expect(api.stop).toHaveBeenCalledWith('m1', 3);
  expect(notify.success).toHaveBeenCalledWith('媒体を停止しました');
  fireEvent.click(screen.getByRole('button', { name: '「求人誌」を再開' }));
  await waitFor(() => expect(api.resume).toHaveBeenCalledWith('m1', 4));
  expect(api.list).toHaveBeenCalledTimes(1);
});

test('処理中の停止ボタンはフォーカスを保ったまま無効を示し、二度目の押下を送らない', async () => {
  let respond: (value: MediumSummary) => void = () => {};
  api.stop.mockReturnValue(new Promise(resolve => (respond = resolve)));
  render(<MediaPage />);
  const stop = await screen.findByRole('button', { name: '「求人誌」を停止' });
  fireEvent.click(stop);
  await waitFor(() => expect(stop).toHaveAttribute('aria-disabled', 'true'));
  expect(stop).not.toBeDisabled();

  fireEvent.click(stop);
  await act(async () => respond({ ...medium, status: 'STOPPED', version: 4 }));
  expect(await screen.findByText('停止中')).toBeVisible();
  expect(api.stop).toHaveBeenCalledTimes(1);
  expect(notify.success).toHaveBeenCalledTimes(1);
});

test('停止の 409 は一覧を取り直し、取り直した版で操作させる', async () => {
  api.stop.mockRejectedValueOnce(conflict('媒体が更新されています。再読み込みしてください'));
  render(<MediaPage />);
  const stop = await screen.findByRole('button', { name: '「求人誌」を停止' });
  api.list.mockResolvedValue([{ ...medium, name: '求人誌', version: 7 }]);
  fireEvent.click(stop);
  await waitFor(() => expect(api.list).toHaveBeenCalledTimes(2));
  expect(notify.error).toHaveBeenCalledWith('媒体が更新されています。再読み込みしてください');

  api.stop.mockResolvedValue({ ...medium, status: 'STOPPED', version: 8 });
  fireEvent.click(await screen.findByRole('button', { name: '「求人誌」を停止' }));
  await waitFor(() => expect(api.stop).toHaveBeenLastCalledWith('m1', 7));
});

test('削除の 409 も一覧を取り直す', async () => {
  api.delete.mockRejectedValue(conflict('記録から参照されている媒体は削除できません'));
  render(<MediaPage />);
  fireEvent.click(await screen.findByRole('button', { name: '「求人誌」を削除' }));
  fireEvent.click(
    within(await screen.findByRole('alertdialog')).getByRole('button', { name: '削除する' })
  );
  await waitFor(() => expect(api.list).toHaveBeenCalledTimes(2));
  expect(api.delete).toHaveBeenCalledWith('m1', 3);
  expect(notify.error).toHaveBeenCalledWith('記録から参照されている媒体は削除できません');
});

test('登録は前後の空白を除いて送り、一覧を取り直して閉じる', async () => {
  api.create.mockResolvedValue('m2');
  render(<MediaPage />);
  fireEvent.click(await screen.findByRole('button', { name: '媒体を登録' }));
  const dialog = await screen.findByRole('dialog');
  fireEvent.change(within(dialog).getByLabelText('媒体名'), { target: { value: '  紹介  ' } });
  fireEvent.click(within(dialog).getByRole('button', { name: '保存する' }));
  await waitFor(() => expect(api.create).toHaveBeenCalledWith('紹介'));
  await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
  expect(api.list).toHaveBeenCalledTimes(2);
  expect(notify.success).toHaveBeenCalledWith('媒体を登録しました');
});

test('空白だけの名前は送らずに入力横へ示す', async () => {
  render(<MediaPage />);
  fireEvent.click(await screen.findByRole('button', { name: '媒体を登録' }));
  const dialog = await screen.findByRole('dialog');
  fireEvent.change(within(dialog).getByLabelText('媒体名'), { target: { value: '   ' } });
  fireEvent.click(within(dialog).getByRole('button', { name: '保存する' }));
  expect(await within(dialog).findByText('媒体名を入力してください')).toBeVisible();
  expect(api.create).not.toHaveBeenCalled();
});

test('改名の応答で行を差し替えて閉じる', async () => {
  api.rename.mockResolvedValue({ ...medium, name: '求人サイト', version: 4 });
  render(<MediaPage />);
  const dialog = await openRename();
  expect(within(dialog).getByLabelText('媒体名')).toHaveValue('求人誌');
  fireEvent.change(within(dialog).getByLabelText('媒体名'), { target: { value: '求人サイト' } });
  fireEvent.click(within(dialog).getByRole('button', { name: '保存する' }));
  await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
  expect(api.rename).toHaveBeenCalledWith('m1', '求人サイト', 3);
  expect(screen.getByText('求人サイト')).toBeVisible();
  expect(api.list).toHaveBeenCalledTimes(1);
});

test('改名の 409 は入力を残したまま一覧を取り直し、取り直した版で送り直させる', async () => {
  api.rename.mockRejectedValueOnce(conflict('同じ名前の媒体が既に登録されています'));
  render(<MediaPage />);
  const dialog = await openRename();
  fireEvent.change(within(dialog).getByLabelText('媒体名'), { target: { value: '重複名' } });
  let finishList: (rows: MediumSummary[]) => void = () => {};
  api.list.mockReturnValueOnce(new Promise(resolve => (finishList = resolve)));
  fireEvent.click(within(dialog).getByRole('button', { name: '保存する' }));
  await waitFor(() => expect(api.list).toHaveBeenCalledTimes(2));
  // 取り直しの応答待ちは古い行が残っているが、その版では送らせない
  expect(
    within(screen.getByRole('dialog')).getByRole('button', { name: '保存する' })
  ).toBeDisabled();
  await act(async () => finishList([{ ...medium, version: 6 }]));
  expect(notify.error).toHaveBeenCalledWith('同じ名前の媒体が既に登録されています');
  expect(screen.getByRole('dialog')).toBeVisible();
  expect(within(screen.getByRole('dialog')).getByLabelText('媒体名')).toHaveValue('重複名');

  api.rename.mockResolvedValue({ ...medium, name: '別名', version: 7 });
  fireEvent.change(within(screen.getByRole('dialog')).getByLabelText('媒体名'), {
    target: { value: '別名' },
  });
  fireEvent.click(within(screen.getByRole('dialog')).getByRole('button', { name: '保存する' }));
  await waitFor(() => expect(api.rename).toHaveBeenLastCalledWith('m1', '別名', 6));
});

test('改名の 404 は一覧を取り直し、ダイアログを削除済みの案内に置き換える', async () => {
  api.rename.mockRejectedValue({
    response: { status: 404, data: { error: '媒体が見つかりません' } },
  });
  render(<MediaPage />);
  const dialog = await openRename();
  api.list.mockResolvedValue([]);
  fireEvent.click(within(dialog).getByRole('button', { name: '保存する' }));
  expect(await screen.findByText('この媒体は削除されています。')).toBeVisible();
  expect(screen.queryByRole('button', { name: '保存する' })).not.toBeInTheDocument();
  fireEvent.click(screen.getByRole('button', { name: '閉じる' }));
  await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
});

test('改名後の取り直しが失敗したら、入力を残したままダイアログ内で一覧を再試行させる', async () => {
  api.rename.mockRejectedValue(conflict('同じ名前の媒体が既に登録されています'));
  render(<MediaPage />);
  const dialog = await openRename();
  fireEvent.change(within(dialog).getByLabelText('媒体名'), { target: { value: '重複名' } });
  api.list.mockRejectedValue(new Error('network'));
  fireEvent.click(within(dialog).getByRole('button', { name: '保存する' }));
  await waitFor(() => expect(api.list).toHaveBeenCalledTimes(2));
  expect(
    await within(screen.getByRole('dialog')).findByText('媒体一覧の取得に失敗しました')
  ).toBeVisible();
  expect(screen.queryByText('この媒体は削除されています。')).not.toBeInTheDocument();
  const dialog2 = screen.getByRole('dialog');
  expect(within(dialog2).getByLabelText('媒体名')).toHaveValue('重複名');
  expect(within(dialog2).getByRole('button', { name: '保存する' })).toBeDisabled();

  api.list.mockResolvedValue([{ ...medium, version: 6 }]);
  fireEvent.click(within(dialog2).getByRole('button', { name: '再試行' }));
  await waitFor(() =>
    expect(within(dialog2).getByRole('button', { name: '保存する' })).toBeEnabled()
  );
  expect(within(dialog2).getByLabelText('媒体名')).toHaveValue('重複名');
  api.rename.mockResolvedValue({ ...medium, name: '重複名', version: 7 });
  fireEvent.click(within(dialog2).getByRole('button', { name: '保存する' }));
  await waitFor(() => expect(api.rename).toHaveBeenLastCalledWith('m1', '重複名', 6));
});
