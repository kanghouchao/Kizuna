import { act, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { remunerationApi, type Statement } from '../../api';
import { readTokenClaims, storePath } from '@/shared/lib';
import { RemunerationStatementPanel } from '../RemunerationStatementPanel';
import { RemunerationEditor } from '../RemunerationEditor';
import { RemunerationManagement } from '../RemunerationManagement';
jest.mock('../../api', () => ({
  remunerationApi: {
    statement: jest.fn(),
    createBonus: jest.fn(),
    guarantees: jest.fn(),
    bonuses: jest.fn(),
    changes: jest.fn(),
  },
}));
jest.mock('@/shared/lib', () => ({
  ...jest.requireActual('@/shared/lib'),
  readTokenClaims: jest.fn(),
}));
const originalRandomUUID = crypto.randomUUID;
afterEach(() => {
  Object.defineProperty(crypto, 'randomUUID', { configurable: true, value: originalRandomUUID });
});
const statement = jest.mocked(remunerationApi.statement);
const page = { content: [], number: 0, size: 20, total_elements: 0, total_pages: 0 };
const data: Statement = {
  store_id: 1,
  store_name: '店舗',
  person_id: 2,
  name: '本人',
  month: '2026-09',
  generated_at: '2026-10-01T00:00:00Z',
  order_total: 7000,
  known_guarantee_total: 0,
  bonus_total: 1000,
  days: [
    {
      business_date: '2026-09-30',
      order_amount: 7000,
      closed_duration: 'PT3H',
      attendance_incomplete: true,
      guarantee_status: 'PENDING_ATTENDANCE',
      bonus_amount: 1000,
    },
  ],
  orders: page,
  bonus_awards: page,
};
beforeEach(() => {
  jest.clearAllMocks();
  window.history.replaceState({}, '', storePath('1', '/orders/monthly-remunerations'));
  jest
    .mocked(readTokenClaims)
    .mockReturnValue({ subject: 'actor', authorities: [], userType: 'STAFF', storeBridge: true });
  statement.mockResolvedValue(data);
});
it('未終了時は既知小計を示し完全合計を捏造しない', async () => {
  render(<RemunerationStatementPanel scope={{ scope: 'self', storeId: 1 }} month="2026-09" />);
  fireEvent.click(await screen.findByRole('button', { name: '保証・ボーナスを含む月次明細' }));
  expect(await screen.findByText('退勤記録待ち')).toBeInTheDocument();
  expect(screen.getByText(/現在計算できる保証の小計/)).toBeInTheDocument();
  expect(screen.queryByText('¥8,000')).not.toBeInTheDocument();
  expect(screen.queryByRole('button', { name: 'ボーナスを記録' })).not.toBeInTheDocument();
});
it('停止日の未終了表示はゼロの保証合計と両立する', async () => {
  statement.mockResolvedValue({
    ...data,
    guarantee_total: 0,
    total: 8000,
    days: [{ ...data.days[0], guarantee_status: 'STOPPED', guarantee_amount: 0 }],
  });
  render(<RemunerationStatementPanel scope={{ scope: 'self', storeId: 1 }} month="2026-09" />);
  fireEvent.click(await screen.findByRole('button', { name: '保証・ボーナスを含む月次明細' }));
  expect(await screen.findByText('保証停止')).toBeInTheDocument();
  expect(screen.getByText('未終了の記録あり')).toBeInTheDocument();
  expect(screen.getByText('¥8,000')).toBeInTheDocument();
  expect(screen.queryByText(/現在計算できる保証の小計/)).not.toBeInTheDocument();
});
it('参照権限のない店舗利用者に追加金額の導線を出さない', async () => {
  render(<RemunerationStatementPanel scope={{ scope: 'store', personId: 2 }} month="2026-09" />);
  expect(screen.queryByRole('button')).not.toBeInTheDocument();
  expect(statement).not.toHaveBeenCalled();
});
it('取得失敗時は旧合計を残さず領域内で再試行する', async () => {
  render(<RemunerationStatementPanel scope={{ scope: 'self', storeId: 1 }} month="2026-09" />);
  fireEvent.click(await screen.findByRole('button', { name: '保証・ボーナスを含む月次明細' }));
  await screen.findByText('退勤記録待ち');
  statement.mockRejectedValueOnce(new Error('failed'));
  fireEvent.click(screen.getByRole('button', { name: '内訳を再照会' }));
  expect(await screen.findByRole('alert')).toHaveTextContent('取得できません');
  expect(screen.queryByText('¥7,000')).not.toBeInTheDocument();
});
it('HTTP環境でもボーナス保存に帰属日と理由とUUIDを送信する', async () => {
  Object.defineProperty(crypto, 'randomUUID', { configurable: true, value: undefined });
  jest.mocked(remunerationApi.createBonus).mockResolvedValue();
  const saved = jest.fn();
  render(
    <RemunerationEditor
      open
      personId={2}
      personName="本人"
      scope={JSON.stringify(['actor', '1'])}
      target={{ kind: 'bonus' }}
      onClose={jest.fn()}
      onSaved={saved}
    />
  );
  fireEvent.change(screen.getByLabelText('帰属日'), { target: { value: '2026-09-30' } });
  fireEvent.change(screen.getByLabelText('付与額（円）'), { target: { value: '1000' } });
  fireEvent.change(screen.getByLabelText('理由・説明'), { target: { value: '付与理由' } });
  fireEvent.click(screen.getByRole('button', { name: '保存する' }));
  await waitFor(() => expect(saved).toHaveBeenCalled());
  expect(remunerationApi.createBonus).toHaveBeenCalledWith(2, {
    award_date: '2026-09-30',
    amount: 1000,
    reason: '付与理由',
    request_id: expect.stringMatching(
      /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/
    ),
  });
});

it('必須入力の不足をブラウザの吹き出しではなく日本語で示す', async () => {
  render(
    <RemunerationEditor
      open
      personId={2}
      personName="本人"
      scope={JSON.stringify(['actor', '1'])}
      target={{ kind: 'bonus' }}
      onClose={jest.fn()}
      onSaved={jest.fn()}
    />
  );
  fireEvent.click(screen.getByRole('button', { name: '保存する' }));
  expect(await screen.findByText('日付を入力してください')).toBeInTheDocument();
  expect(screen.getByText('金額を整数の円で入力してください')).toBeInTheDocument();
  expect(screen.getByText('理由を入力してください')).toBeInTheDocument();
  expect(remunerationApi.createBonus).not.toHaveBeenCalled();
});
it('履歴を開き直すと同一記録でも最新の先頭へ戻り別記録のカーソルを使わない', async () => {
  jest.mocked(remunerationApi.guarantees).mockResolvedValue({ version: 0, entries: page });
  const bonus = (id: string) => ({
    id,
    person_id: 2,
    award_date: '2026-09-30',
    amount: 1000,
    effective_amount: 1000,
    reason: id,
    version: 0,
  });
  jest
    .mocked(remunerationApi.bonuses)
    .mockResolvedValue({ ...page, content: [bonus('a'), bonus('b')] });
  const changes = jest.mocked(remunerationApi.changes);
  changes.mockResolvedValue({ content: [], next_cursor: 'older' });
  render(
    <RemunerationManagement
      personName="本人"
      personId={2}
      month="2026-09"
      refreshKey={0}
      onSaved={jest.fn()}
    />
  );
  fireEvent.click((await screen.findAllByRole('button', { name: 'ボーナスの変更履歴' }))[0]);
  fireEvent.click(await screen.findByRole('button', { name: '次の履歴' }));
  await waitFor(() => expect(changes).toHaveBeenLastCalledWith('bonus', 'a', 'older'));
  fireEvent.click(screen.getByRole('button', { name: 'Close' }));
  fireEvent.click(screen.getAllByRole('button', { name: 'ボーナスの変更履歴' })[1]);
  await waitFor(() => expect(changes).toHaveBeenLastCalledWith('bonus', 'b', undefined));
  fireEvent.click(await screen.findByRole('button', { name: '次の履歴' }));
  await waitFor(() => expect(changes).toHaveBeenLastCalledWith('bonus', 'b', 'older'));
  fireEvent.click(screen.getByRole('button', { name: 'Close' }));
  fireEvent.click(screen.getAllByRole('button', { name: 'ボーナスの変更履歴' })[1]);
  await waitFor(() => expect(changes).toHaveBeenLastCalledWith('bonus', 'b', undefined));
});

const fillBonus = () => {
  fireEvent.change(screen.getByLabelText('帰属日'), { target: { value: '2026-09-30' } });
  fireEvent.change(screen.getByLabelText('付与額（円）'), { target: { value: '1000' } });
  fireEvent.change(screen.getByLabelText('理由・説明'), { target: { value: '付与理由' } });
};
const allowBonus = () => {
  jest.mocked(readTokenClaims).mockReturnValue({
    subject: 'actor',
    authorities: ['PERM_BONUS_AWARD'],
    userType: 'STAFF',
    storeBridge: true,
  });
  jest.mocked(remunerationApi.guarantees).mockResolvedValue({ version: 0, entries: page });
  jest.mocked(remunerationApi.bonuses).mockResolvedValue(page);
};
it('応答喪失後は編集を止め、権限エラー後も元の内容だけを再送して確定する', async () => {
  allowBonus();
  const create = jest.mocked(remunerationApi.createBonus);
  create
    .mockRejectedValueOnce(new Error('応答喪失'))
    .mockRejectedValueOnce({ response: { status: 403 } })
    .mockResolvedValue();
  const saved = jest.fn();
  render(
    <RemunerationManagement
      personId={2}
      personName="本人"
      month="2026-09"
      refreshKey={0}
      onSaved={saved}
    />
  );
  fireEvent.click(screen.getByRole('button', { name: 'ボーナスを記録' }));
  fillBonus();
  fireEvent.click(screen.getByRole('button', { name: '保存する' }));
  const retry = await screen.findByRole('button', { name: '元の内容で結果を確認' });
  expect(screen.getByLabelText('付与額（円）')).toBeDisabled();
  expect(screen.getByLabelText('理由・説明')).toBeDisabled();
  expect(screen.getByLabelText('帰属日')).toBeDisabled();
  const original = create.mock.calls[0];
  fireEvent.click(retry);
  await waitFor(() => expect(create).toHaveBeenCalledTimes(2));
  await waitFor(() => expect(retry).toBeEnabled());
  expect(screen.getByLabelText('付与額（円）')).toBeDisabled();
  expect(saved).not.toHaveBeenCalled();
  fireEvent.click(retry);
  await waitFor(() => expect(saved).toHaveBeenCalledTimes(1));
  expect(create.mock.calls[1]).toEqual(original);
  expect(create.mock.calls[2]).toEqual(original);
});
it('閉じ直しと本人切替を越えて原対象を復元し、確定後だけ新しい要求を作る', async () => {
  allowBonus();
  const create = jest.mocked(remunerationApi.createBonus);
  create.mockRejectedValueOnce(new Error('応答喪失')).mockResolvedValue();
  const saved = jest.fn();
  const view = render(
    <RemunerationManagement
      key="first"
      personId={2}
      personName="元の本人"
      month="2026-09"
      refreshKey={0}
      onSaved={saved}
    />
  );
  fireEvent.click(screen.getByRole('button', { name: 'ボーナスを記録' }));
  fillBonus();
  fireEvent.click(screen.getByRole('button', { name: '保存する' }));
  await screen.findByRole('button', { name: '元の内容で結果を確認' });
  fireEvent.click(screen.getByRole('button', { name: '閉じる' }));
  expect(screen.getByRole('button', { name: 'ボーナスを記録' })).toBeDisabled();
  fireEvent.click(screen.getByRole('button', { name: '未確認の送信を復元' }));
  expect(screen.getByLabelText('付与額（円）')).toHaveValue(1000);
  fireEvent.click(screen.getByRole('button', { name: '閉じる' }));
  view.rerender(
    <RemunerationManagement
      key="other"
      personId={3}
      personName="別の本人"
      month="2026-09"
      refreshKey={0}
      onSaved={saved}
    />
  );
  expect(screen.getByRole('button', { name: 'ボーナスを記録' })).toBeDisabled();
  expect(screen.getByText(/元の本人 の送信結果を確認するまで/)).toBeInTheDocument();
  fireEvent.click(screen.getByRole('button', { name: '未確認の送信を復元' }));
  expect(screen.getByLabelText('理由・説明')).toHaveValue('付与理由');
  fireEvent.click(screen.getByRole('button', { name: '元の内容で結果を確認' }));
  await waitFor(() => expect(saved).toHaveBeenCalledTimes(1));
  expect(create.mock.calls[1]).toEqual(create.mock.calls[0]);
  fireEvent.click(screen.getByRole('button', { name: 'ボーナスを記録' }));
  fillBonus();
  fireEvent.click(screen.getByRole('button', { name: '保存する' }));
  await waitFor(() => expect(saved).toHaveBeenCalledTimes(2));
  expect(create.mock.calls[2][0]).toBe(3);
  expect(create.mock.calls[2][1].request_id).not.toBe(create.mock.calls[0][1].request_id);
});
it('送信中の連打と閉じる操作は新しい要求を作らない', async () => {
  allowBonus();
  let complete!: () => void;
  const create = jest.mocked(remunerationApi.createBonus).mockImplementationOnce(
    () =>
      new Promise<void>(resolve => {
        complete = resolve;
      })
  );
  const saved = jest.fn();
  render(
    <RemunerationManagement
      personId={2}
      personName="本人"
      month="2026-09"
      refreshKey={0}
      onSaved={saved}
    />
  );
  fireEvent.click(screen.getByRole('button', { name: 'ボーナスを記録' }));
  fillBonus();
  const save = screen.getByRole('button', { name: '保存する' });
  fireEvent.click(save);
  fireEvent.click(save);
  await screen.findByRole('button', { name: '送信中...' });
  expect(create).toHaveBeenCalledTimes(1);
  expect(screen.getByRole('button', { name: '閉じる' })).toBeDisabled();
  fireEvent.keyDown(screen.getByRole('dialog'), { key: 'Escape' });
  expect(screen.getByRole('dialog')).toBeInTheDocument();
  await act(async () => complete());
  expect(saved).toHaveBeenCalledTimes(1);
});
it('初回の明確な拒否では未確認扱いにせず入力を修正できる', async () => {
  allowBonus();
  const create = jest
    .mocked(remunerationApi.createBonus)
    .mockRejectedValueOnce({ response: { status: 400 } })
    .mockResolvedValue();
  const saved = jest.fn();
  render(
    <RemunerationManagement
      personId={2}
      personName="本人"
      month="2026-09"
      refreshKey={0}
      onSaved={saved}
    />
  );
  fireEvent.click(screen.getByRole('button', { name: 'ボーナスを記録' }));
  fillBonus();
  fireEvent.click(screen.getByRole('button', { name: '保存する' }));
  await waitFor(() => expect(create).toHaveBeenCalledTimes(1));
  await waitFor(() => expect(screen.getByRole('button', { name: '保存する' })).toBeEnabled());
  expect(screen.getByLabelText('付与額（円）')).toBeEnabled();
  fireEvent.change(screen.getByLabelText('付与額（円）'), { target: { value: '2000' } });
  fireEvent.click(screen.getByRole('button', { name: '保存する' }));
  await waitFor(() => expect(saved).toHaveBeenCalledTimes(1));
  expect(create.mock.calls[1][1].amount).toBe(2000);
});

it('別の操作者や店舗には未確認内容を見せず、元の作用域へ戻ると復元する', async () => {
  allowBonus();
  const create = jest
    .mocked(remunerationApi.createBonus)
    .mockRejectedValueOnce(new Error('応答喪失'))
    .mockResolvedValue();
  const saved = jest.fn();
  const view = render(
    <RemunerationManagement
      key="original"
      personId={2}
      personName="機密対象"
      month="2026-09"
      refreshKey={0}
      onSaved={saved}
    />
  );
  fireEvent.click(screen.getByRole('button', { name: 'ボーナスを記録' }));
  fillBonus();
  fireEvent.click(screen.getByRole('button', { name: '保存する' }));
  await screen.findByRole('button', { name: '元の内容で結果を確認' });
  const unload = new Event('beforeunload', { cancelable: true });
  window.dispatchEvent(unload);
  expect(unload.defaultPrevented).toBe(true);
  jest.mocked(readTokenClaims).mockReturnValue({
    subject: 'other',
    authorities: ['PERM_BONUS_AWARD'],
    userType: 'STAFF',
    storeBridge: true,
  });
  view.rerender(
    <RemunerationManagement
      key="actor"
      personId={3}
      personName="別本人"
      month="2026-09"
      refreshKey={0}
      onSaved={saved}
    />
  );
  expect(screen.queryByText(/機密対象/)).not.toBeInTheDocument();
  expect(screen.queryByRole('button', { name: '未確認の送信を復元' })).not.toBeInTheDocument();
  allowBonus();
  window.history.replaceState({}, '', storePath('2', '/orders/monthly-remunerations'));
  view.rerender(
    <RemunerationManagement
      key="store"
      personId={3}
      personName="別本人"
      month="2026-09"
      refreshKey={0}
      onSaved={saved}
    />
  );
  expect(screen.queryByRole('button', { name: '未確認の送信を復元' })).not.toBeInTheDocument();
  window.history.replaceState({}, '', storePath('1', '/orders/monthly-remunerations'));
  view.rerender(
    <RemunerationManagement
      key="returned"
      personId={2}
      personName="機密対象"
      month="2026-09"
      refreshKey={0}
      onSaved={saved}
    />
  );
  fireEvent.click(screen.getByRole('button', { name: '未確認の送信を復元' }));
  fireEvent.click(screen.getByRole('button', { name: '元の内容で結果を確認' }));
  await waitFor(() => expect(saved).toHaveBeenCalledTimes(1));
  expect(create.mock.calls[1]).toEqual(create.mock.calls[0]);
  const complete = new Event('beforeunload', { cancelable: true });
  window.dispatchEvent(complete);
  expect(complete.defaultPrevented).toBe(false);
});
