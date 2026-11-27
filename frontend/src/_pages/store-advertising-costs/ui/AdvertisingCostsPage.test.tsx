import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { mediumApi } from '@/entities/medium';
import { useMe } from '@/entities/user';
import { storeSessionToken } from '@/shared/api';
import { notify } from '@/shared/notify';
import { advertisingApi, type Cost } from '../api/advertising';
import AdvertisingCostsPage from './AdvertisingCostsPage';

jest.mock('@/widgets/admin-shell', () => ({
  DeniedPage: () => <p role="alert">権限の無い画面</p>,
}));
jest.mock('@/entities/user', () => ({
  ...jest.requireActual('@/entities/user'),
  useCurrentStoreId: () => '1',
}));
jest.mock('@/entities/user/model/MeContext', () => ({
  ...jest.requireActual('@/entities/user/model/MeContext'),
  useMe: jest.fn(),
}));
jest.mock('@/entities/medium', () => ({ mediumApi: { options: jest.fn() } }));
jest.mock('../api/advertising', () => ({
  ...jest.requireActual('../api/advertising'),
  advertisingApi: { list: jest.fn(), month: jest.fn(), mutate: jest.fn() },
}));
jest.mock('@/shared/notify', () => ({
  notify: { success: jest.fn(), error: jest.fn(), warning: jest.fn() },
}));
const api = jest.mocked(advertisingApi);
const REPLAY = '同じ要求で結果を確認';
const cost: Cost = {
  id: 'cost-1',
  store_id: 1,
  month: '2026-11',
  category: 'SALES',
  medium: { id: 'medium-a', name: '求人媒体A' },
  agency_name: null,
  plan_name: null,
  inquiry_count: 3,
  amount: 100,
  version: 2,
  updated_at: '2026-11-01T00:00:00Z',
};
const httpError = (code: number) =>
  Object.assign(new Error('rejected'), { response: { status: code } });
// 未確認の操作は本人ごとにタブへ残るので、テストごとに別の本人で始める
let userId = 100;
beforeEach(() => {
  jest.resetAllMocks();
  storeSessionToken({ token: 't', expires_at: 1, user_id: ++userId, refresh_expires_at: 2 });
  jest.mocked(useMe).mockReturnValue({
    me: {
      email: 'staff@example.com',
      user_type: 'STAFF',
      permissions: ['ADVERTISING_COST_VIEW', 'ADVERTISING_COST_MANAGE'],
      line_linked: false,
      email_verified: false,
    },
    isLoading: false,
    failure: null,
    reload: jest.fn(),
    setMe: jest.fn(),
  });
  jest.mocked(mediumApi.options).mockResolvedValue([{ id: 'medium-a', name: '求人媒体A' }]);
  api.list.mockResolvedValue({
    content: [cost],
    number: 0,
    size: 20,
    total_pages: 1,
    total_elements: 1,
  });
  api.month.mockResolvedValue({
    store_id: 1,
    month: '2026-11',
    version: 1,
    entry_count: 1,
    sales_amount: 100,
    recruitment_amount: 0,
    recorded_total_amount: 100,
    generated_at: '2026-11-01T00:00:00Z',
  });
});
async function submitEdit() {
  render(<AdvertisingCostsPage />);
  fireEvent.click(await screen.findByRole('button', { name: '編集' }));
  const dialog = await screen.findByRole('dialog');
  fireEvent.change(within(dialog).getByLabelText('理由'), { target: { value: '月額改定' } });
  const save = within(dialog).getByRole('button', { name: '保存する' });
  await waitFor(() => expect(save).toBeEnabled());
  fireEvent.click(save);
  await waitFor(() => expect(api.mutate).toHaveBeenCalledTimes(1));
  return dialog;
}
const replayButtons = () => screen.queryAllByRole('button', { name: REPLAY, hidden: true });
// 再確認は、失敗の反映が終わるまで押せない
async function pressReplay(dialog: HTMLElement) {
  const replay = await within(dialog).findByRole('button', { name: REPLAY });
  await waitFor(() => expect(replay).toBeEnabled());
  fireEvent.click(replay);
}

test('結果不明になったら、開いたままのダイアログの中で同じ要求を再確認できること', async () => {
  api.mutate.mockRejectedValueOnce(new Error('network')).mockResolvedValueOnce(undefined as never);
  const dialog = await submitEdit();
  await within(dialog).findByRole('button', { name: REPLAY });
  const notice = within(dialog).getByRole('status');
  expect(notice).toHaveTextContent('操作結果をまだ確認できません');
  // 送信の操作は本文の末尾にあるので、先頭に出た案内を見える位置へ送る
  expect(jest.mocked(Element.prototype.scrollIntoView).mock.instances).toContain(notice);
  expect(replayButtons()).toHaveLength(1);
  await pressReplay(dialog);
  await waitFor(() => expect(notify.success).toHaveBeenCalledWith('操作結果を確認しました。'));
  await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
  const [[, sent, key], [, resent, resentKey]] = api.mutate.mock.calls;
  expect([resent, resentKey]).toEqual([sent, key]);
  expect(replayButtons()).toHaveLength(0);
});

test('結果不明のままダイアログを閉じたら、ページで同じ要求を再確認できること', async () => {
  api.mutate.mockRejectedValueOnce(new Error('network'));
  const dialog = await submitEdit();
  await within(dialog).findByRole('button', { name: REPLAY });
  fireEvent.click(within(dialog).getByRole('button', { name: '閉じる' }));
  await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
  await waitFor(() => expect(screen.getByRole('button', { name: REPLAY })).toBeEnabled());
  expect(screen.getByRole('button', { name: '編集' })).toBeDisabled();
});

test('業務拒否ではダイアログと入力を残し、再確認を出さないこと', async () => {
  api.mutate.mockRejectedValueOnce(httpError(422));
  const dialog = await submitEdit();
  await waitFor(() => expect(notify.error).toHaveBeenCalled());
  await waitFor(() => expect(within(dialog).getByLabelText('理由')).toBeEnabled());
  expect(dialog).toBeInTheDocument();
  expect(within(dialog).getByLabelText('理由')).toHaveValue('月額改定');
  expect(replayButtons()).toHaveLength(0);
});

test('再確認が業務拒否されたら、入力を残したまま編集に戻れること', async () => {
  api.mutate.mockRejectedValueOnce(new Error('network')).mockRejectedValueOnce(httpError(422));
  const dialog = await submitEdit();
  await pressReplay(dialog);
  await waitFor(() => expect(within(dialog).getByLabelText('理由')).toBeEnabled());
  expect(dialog).toBeInTheDocument();
  expect(within(dialog).getByLabelText('理由')).toHaveValue('月額改定');
  expect(replayButtons()).toHaveLength(0);
});

test.each([
  ['最初の送信', [httpError(404)]],
  ['再確認', [new Error('network'), httpError(404)]],
])('%sで対象が無ければ、ダイアログを見つからない案内に置き換えること', async (_, failures) => {
  failures.forEach(failure => api.mutate.mockRejectedValueOnce(failure));
  const dialog = await submitEdit();
  if (failures.length > 1) await pressReplay(dialog);
  expect(await within(dialog).findByRole('alert')).toHaveTextContent('広告費が見つかりません');
  expect(replayButtons()).toHaveLength(0);
});
