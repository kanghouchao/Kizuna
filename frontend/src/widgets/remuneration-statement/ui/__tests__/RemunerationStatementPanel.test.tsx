import { act, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { remunerationApi, type Statement } from '../../api';
import { setCurrentStore } from '@/shared/lib';
import { useMe } from '@/entities/user';
import { storeSessionToken } from '@/shared/api';
import type { PermissionCode } from '@/entities/user';
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
jest.mock('@/entities/user/model/MeContext', () => ({
  ...jest.requireActual('@/entities/user/model/MeContext'),
  useMe: jest.fn(),
}));
jest.mock('@/entities/user/model/StoreContext', () => ({
  ...jest.requireActual('@/entities/user/model/StoreContext'),
  useStoreContext: () => ({ currentStoreId: '1' }),
}));
const mockedUseMe = useMe as jest.MockedFunction<typeof useMe>;
// 画面の /me と、このタブのトークンの利用者を同じ利用者に揃える
const meAs = (userId: number, permissions: PermissionCode[]) => {
  storeSessionToken({ token: 't', expires_at: 1, user_id: userId, refresh_expires_at: 2 });
  mockedUseMe.mockReturnValue({
    me: { email: `user${userId}@kizuna.test`, user_type: 'STAFF', permissions, line_linked: false },
    isLoading: false,
    failure: null,
    reload: jest.fn(),
    setMe: jest.fn(),
  });
};
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
  setCurrentStore('1');
  meAs(7, []);
  statement.mockResolvedValue(data);
});
it('未終了時は既知小計を示し完全合計を捏造しない', async () => {
  render(
    <RemunerationStatementPanel
      scope={{ scope: 'self', storeId: 1 }}
      month="2026-09"
      canView
      canManage={false}
    />
  );
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
  render(
    <RemunerationStatementPanel
      scope={{ scope: 'self', storeId: 1 }}
      month="2026-09"
      canView
      canManage={false}
    />
  );
  fireEvent.click(await screen.findByRole('button', { name: '保証・ボーナスを含む月次明細' }));
  expect(await screen.findByText('保証停止')).toBeInTheDocument();
  expect(screen.getByText('未終了の記録あり')).toBeInTheDocument();
  expect(screen.getByText('¥8,000')).toBeInTheDocument();
  expect(screen.queryByText(/現在計算できる保証の小計/)).not.toBeInTheDocument();
});
it('参照権限のない店舗利用者に追加金額の導線を出さない', async () => {
  render(
    <RemunerationStatementPanel
      scope={{ scope: 'store', personId: 2 }}
      month="2026-09"
      canView={false}
      canManage={false}
    />
  );
  expect(screen.queryByRole('button')).not.toBeInTheDocument();
  expect(statement).not.toHaveBeenCalled();
});
it.each([[true], [false]])(
  '店舗の明細は受注の担当者（canManage=%s）にだけ保証・ボーナスの管理を出す',
  async manages => {
    meAs(7, ['BONUS_AWARD']);
    jest.mocked(remunerationApi.guarantees).mockResolvedValue({ version: 0, entries: page });
    jest.mocked(remunerationApi.bonuses).mockResolvedValue(page);
    render(
      <RemunerationStatementPanel
        scope={{ scope: 'store', personId: 2 }}
        month="2026-09"
        canView
        canManage={manages}
      />
    );
    fireEvent.click(await screen.findByRole('button', { name: '保証・ボーナスを含む月次明細' }));
    await screen.findByText('退勤記録待ち');
    if (manages) await waitFor(() => expect(remunerationApi.guarantees).toHaveBeenCalled());
    else {
      expect(remunerationApi.guarantees).not.toHaveBeenCalled();
      expect(remunerationApi.bonuses).not.toHaveBeenCalled();
      expect(screen.queryByRole('button', { name: 'ボーナスを記録' })).not.toBeInTheDocument();
    }
  }
);
it('取得失敗時は旧合計を残さず領域内で再試行する', async () => {
  render(
    <RemunerationStatementPanel
      scope={{ scope: 'self', storeId: 1 }}
      month="2026-09"
      canView
      canManage={false}
    />
  );
  fireEvent.click(await screen.findByRole('button', { name: '保証・ボーナスを含む月次明細' }));
  await screen.findByText('退勤記録待ち');
  statement.mockRejectedValueOnce(new Error('failed'));
  fireEvent.click(screen.getByRole('button', { name: '内訳を再照会' }));
  expect(await screen.findByRole('alert')).toHaveTextContent('取得できません');
  expect(screen.queryByText('¥7,000')).not.toBeInTheDocument();
});
it('ボーナス保存に帰属日と理由と要求キーを送り、起こした店舗に固定する', async () => {
  jest.mocked(remunerationApi.createBonus).mockResolvedValue();
  const saved = jest.fn();
  render(
    <RemunerationEditor
      open
      personId={2}
      personName="本人"
      storeId="1"
      target={{ kind: 'bonus' }}
      onClose={jest.fn()}
      onSaved={saved}
    />
  );
  fillBonus();
  fireEvent.click(screen.getByRole('button', { name: '保存する' }));
  await waitFor(() => expect(saved).toHaveBeenCalled());
  expect(remunerationApi.createBonus).toHaveBeenCalledWith(
    2,
    {
      award_date: '2026-09-30',
      amount: 1000,
      reason: '付与理由',
      request_id: expect.stringMatching(/^[0-9a-f-]{36}$/),
    },
    { headers: { 'X-Store-ID': '1' } }
  );
});

it('必須入力の不足をブラウザの吹き出しではなく日本語で示す', async () => {
  render(
    <RemunerationEditor
      open
      personId={2}
      personName="本人"
      storeId="1"
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
it('履歴を開き直すと同一記録でも先頭ページへ戻り別記録のページ位置を引き継がない', async () => {
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
  changes.mockImplementation(async (_kind, _id, number = 0) => ({
    content: [],
    number,
    size: 20,
    total_pages: 2,
    total_elements: 21,
  }));
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
  fireEvent.click((await screen.findAllByRole('button', { name: '次へ' }))[0]);
  await waitFor(() => expect(changes).toHaveBeenLastCalledWith('bonus', 'a', 1));
  fireEvent.click(screen.getByRole('button', { name: 'Close' }));
  fireEvent.click(screen.getAllByRole('button', { name: 'ボーナスの変更履歴' })[1]);
  await waitFor(() => expect(changes).toHaveBeenLastCalledWith('bonus', 'b', 0));
  fireEvent.click((await screen.findAllByRole('button', { name: '次へ' }))[0]);
  await waitFor(() => expect(changes).toHaveBeenLastCalledWith('bonus', 'b', 1));
  fireEvent.click(screen.getByRole('button', { name: 'Close' }));
  fireEvent.click(screen.getAllByRole('button', { name: 'ボーナスの変更履歴' })[1]);
  await waitFor(() => expect(changes).toHaveBeenLastCalledWith('bonus', 'b', 0));
});

const fillBonus = () => {
  fireEvent.change(screen.getByLabelText('帰属日'), { target: { value: '2026-09-30' } });
  fireEvent.change(screen.getByLabelText('付与額（円）'), { target: { value: '1000' } });
  fireEvent.change(screen.getByLabelText('理由・説明'), { target: { value: '付与理由' } });
};
const allowBonus = () => {
  meAs(7, ['BONUS_AWARD']);
  jest.mocked(remunerationApi.guarantees).mockResolvedValue({ version: 0, entries: page });
  jest.mocked(remunerationApi.bonuses).mockResolvedValue(page);
};
it('結果不明の間は入力を止め、閉じた後も一覧から元の内容を復元して確認できる', async () => {
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
  expect(
    screen.getByText(/送信結果が未確認です。元の本人 の元の内容を保持しています。/)
  ).toBeInTheDocument();
  expect(screen.getByLabelText('付与額（円）')).toBeDisabled();
  fireEvent.click(screen.getByRole('button', { name: '閉じる' }));
  // 別の本人の画面からも、元の本人の未確認の送信を案内して新しい記録を止める
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
  expect(screen.getByLabelText('付与額（円）')).toHaveValue(1000);
  expect(screen.getByLabelText('理由・説明')).toHaveValue('付与理由');
  fireEvent.click(screen.getByRole('button', { name: '元の内容で結果を確認' }));
  await waitFor(() => expect(saved).toHaveBeenCalledTimes(1));
  expect(create.mock.calls[1]).toEqual(create.mock.calls[0]);
  expect(screen.getByRole('button', { name: 'ボーナスを記録' })).toBeEnabled();
});
it('送信中は閉じる操作を止める', async () => {
  allowBonus();
  let complete!: () => void;
  jest.mocked(remunerationApi.createBonus).mockImplementationOnce(
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
  fireEvent.click(screen.getByRole('button', { name: '保存する' }));
  await screen.findByRole('button', { name: '送信中...' });
  expect(screen.getByRole('button', { name: '閉じる' })).toBeDisabled();
  fireEvent.keyDown(screen.getByRole('dialog'), { key: 'Escape' });
  expect(screen.getByRole('dialog')).toBeInTheDocument();
  await act(async () => complete());
  expect(saved).toHaveBeenCalledTimes(1);
});
