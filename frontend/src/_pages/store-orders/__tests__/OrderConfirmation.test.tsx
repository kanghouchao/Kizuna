import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { OrderPreview } from '@/entities/order';
import { useOrderConfirmation } from '../ui/useOrderConfirmation';

let mockStore = '1';
jest.mock('@/entities/user', () => ({
  ...jest.requireActual('@/entities/user'),
  useStoreContext: () => ({
    stores: [{ id: 1, name: 'テスト店舗' }],
    currentStoreId: mockStore,
    currentStore: { id: 1, name: 'テスト店舗' },
    loadFailed: false,
    reload: jest.fn(),
    switchStore: jest.fn(),
  }),
}));
beforeEach(() => {
  mockStore = '1';
});
const first: OrderPreview = {
  point_basis_amount: 12000,
  total_duration_minutes: 60,
  total_remuneration: 7000,
  requires_attention: false,
  unresolved_special_service_count: 0,
  special_services: [],
  confirmation_token: 'first',
  course: {
    service_id: '1',
    revision_id: 'r1',
    revision_number: 1,
    name: '基本',
    duration_minutes: 60,
    price: 12000,
    remuneration: 7000,
    adoption_basis: 'CURRENT_SETTING',
  },
  fee_lines: [],
  total_fee: 10000,
};
function Screen({
  preview,
  save,
}: {
  preview: () => Promise<OrderPreview>;
  save: (token: string) => void;
}) {
  const confirmation = useOrderConfirmation();
  return (
    <>
      {confirmation.dialog}
      <button
        onClick={async () => {
          const token = await confirmation.confirm(preview);
          if (token) save(token);
        }}
      >
        試算する
      </button>
    </>
  );
}
const dialog = () => screen.getByRole('dialog', { name: '採用条件の確認' });
const changes = () => screen.queryByRole('region', { name: '前回の確認からの変更' });
const changeRow = (label: RegExp) => within(changes()!).getByRole('row', { name: label });

async function confirmOnce(save: jest.Mock) {
  fireEvent.click(screen.getByText('試算する'));
  fireEvent.click(await screen.findByRole('button', { name: 'この内容を確認して保存' }));
  await waitFor(() => expect(save).toHaveBeenCalledTimes(1));
  fireEvent.click(screen.getByText('試算する'));
  await screen.findByRole('button', { name: 'この内容を確認して保存' });
}

test('明示確認の前に保存せず、再試算の差異を対照表で示す', async () => {
  const preview = jest
    .fn()
    .mockResolvedValueOnce(first)
    .mockResolvedValueOnce({
      ...first,
      confirmation_token: 'second',
      total_fee: 13000,
      course: { ...first.course, price: 15000, remuneration: 9000 },
    });
  const save = jest.fn();
  render(<Screen preview={preview} save={save} />);
  fireEvent.click(screen.getByText('試算する'));
  const fees = await screen.findByRole('region', { name: '料金明細' });
  expect(within(fees).getByRole('row', { name: /基本/ })).toHaveTextContent('¥12,000');
  expect(save).not.toHaveBeenCalled();
  fireEvent.click(screen.getByRole('button', { name: 'この内容を確認して保存' }));
  await waitFor(() => expect(save).toHaveBeenCalledWith('first'));
  fireEvent.click(screen.getByText('試算する'));
  await screen.findByRole('region', { name: '前回の確認からの変更' });
  expect(within(changes()!).getByRole('status')).toHaveTextContent('前回の確認内容から変更');
  const remuneration = changeRow(/コースの固定報酬/);
  expect(remuneration).toHaveAttribute('data-changed', 'true');
  expect(remuneration).toHaveTextContent(/¥7,000.*¥9,000/);
  expect(changeRow(/^総時間/)).toHaveAttribute('data-changed', 'false');
  fireEvent.click(within(dialog()).getByRole('button', { name: '入力に戻る' }));
  expect(save).toHaveBeenCalledTimes(1);
});

test('前回と確認トークンも特殊サービスも同じなら対照表を出さない', async () => {
  const preview = jest.fn().mockResolvedValue(first);
  const save = jest.fn();
  render(<Screen preview={preview} save={save} />);
  await confirmOnce(save);
  expect(screen.getByRole('region', { name: '料金明細' })).toBeInTheDocument();
  expect(changes()).not.toBeInTheDocument();
});

test('店舗が変わると以前の確認内容と未完了の操作を引き継がない', async () => {
  const save = jest.fn();
  const preview = jest.fn().mockResolvedValue(first);
  const { rerender } = render(<Screen preview={preview} save={save} />);
  await confirmOnce(save);
  mockStore = '2';
  rerender(<Screen preview={preview} save={save} />);
  expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
  preview.mockResolvedValue({
    ...first,
    confirmation_token: 'other',
    course: { ...first.course, name: '別店舗' },
  });
  fireEvent.click(screen.getByText('試算する'));
  const fees = await screen.findByRole('region', { name: '料金明細' });
  expect(within(fees).getByRole('row', { name: /別店舗/ })).toBeInTheDocument();
  expect(changes()).not.toBeInTheDocument();
  expect(save).toHaveBeenCalledTimes(1);
});

test('加算の改定差異は費用・報酬・版本を示し、再確認するまで保存しない', async () => {
  const line = {
    kind: 'SURCHARGE' as const,
    name: '指名',
    amount: 1000,
    remuneration: 500,
    revision_number: 1,
    system_owned: false,
  };
  const load = jest
    .fn()
    .mockResolvedValueOnce({ ...first, fee_lines: [line] })
    .mockResolvedValueOnce({
      ...first,
      confirmation_token: 'revised',
      total_remuneration: 8000,
      fee_lines: [{ ...line, amount: 2000, remuneration: 1000, revision_number: 2 }],
    });
  const save = jest.fn();
  render(<Screen preview={load} save={save} />);
  await confirmOnce(save);
  const row = changeRow(/^明細1/);
  expect(row).toHaveAttribute('data-changed', 'true');
  const [, before, after] = within(row).getAllByRole('cell');
  expect(before).toHaveTextContent('指名 / 版1 / 料金 ¥1,000 / 固定報酬 ¥500');
  expect(after).toHaveTextContent('指名 / 版2 / 料金 ¥2,000 / 固定報酬 ¥1,000');
  expect(save).toHaveBeenCalledTimes(1);
  fireEvent.click(screen.getByRole('button', { name: 'この内容を確認して保存' }));
  await waitFor(() => expect(save).toHaveBeenLastCalledWith('revised'));
});

const special = {
  service_id: 's1',
  revision_id: 'sr1',
  revision_number: 1,
  terms_version: 1,
  name: '特殊',
  charge_type: 'PAID' as const,
  price: 2000,
  remuneration: 1500,
  adoption_basis: 'ACCEPTED_TERMS' as const,
  enrollment_id: 'cast1',
  consent_version: 1,
  requires_attention: false,
};

test('トークンが同じでも受諾状態の差を表示し、歴史の受諾を未受諾にしない', async () => {
  const preview = jest
    .fn()
    .mockResolvedValueOnce({ ...first, special_services: [special] })
    .mockResolvedValueOnce({
      ...first,
      special_services: [{ ...special, current_consent_status: 'RECONFIRMATION_REQUIRED' }],
    });
  const save = jest.fn();
  render(<Screen preview={preview} save={save} />);
  await confirmOnce(save);
  const row = changeRow(/^特殊/);
  expect(row).toHaveAttribute('data-changed', 'true');
  expect(row).toHaveTextContent('受諾時の約定');
  expect(row).toHaveTextContent('再受諾待ち');
  expect(row).toHaveTextContent('担当在籍 cast1');
  expect(row).not.toHaveTextContent('未受諾');
});

test('拒否項目の警告と特殊サービスの受諾状態をラベルで示す', async () => {
  const preview = jest.fn().mockResolvedValue({
    ...first,
    requires_attention: true,
    special_services: [
      { ...special, current_consent_status: 'ACCEPTED' },
      {
        ...special,
        service_id: 's2',
        name: '撮影',
        requires_attention: true,
        current_consent_status: 'REJECTED',
      },
    ],
  });
  render(<Screen preview={preview} save={jest.fn()} />);
  fireEvent.click(screen.getByText('試算する'));
  expect(await screen.findByRole('alert')).toHaveTextContent('拒否項目が残っています');
  const list = screen.getByRole('region', { name: '特殊サービス' });
  const items = within(list).getAllByRole('listitem');
  expect(items[0]).toHaveTextContent('特殊 版1');
  expect(items[0]).toHaveTextContent('受諾済み');
  expect(items[1]).toHaveTextContent('撮影 版1');
  expect(items[1]).toHaveTextContent('本人拒否・要対応');
});

test('ポイント利用上限と通常付与基準をポイントの節で確認してから保存する', async () => {
  const save = jest.fn();
  const preview = jest.fn().mockResolvedValue({
    ...first,
    point_basis_amount: 16000,
    total_fee: 13000,
    points: {
      member_linked: true,
      redemption_eligible: true,
      member_code: 'M001',
      point_balance: 5000,
      usage_unit: 100,
      use_points: 3000,
      grant_points: 160,
    },
  });
  render(<Screen preview={preview} save={save} />);
  fireEvent.click(screen.getByText('試算する'));
  const points = await screen.findByRole('region', { name: 'ポイント' });
  // ラベルと値は隣り合う行として読めればよい（区切りの要素は問わない）
  for (const pair of [
    '会員M001',
    '利用資格利用可能',
    '現在残高5,000pt',
    '利用3,000pt',
    '付与予定160pt',
    '利用単位100pt',
    '利用上限・通常付与基準¥16,000',
  ])
    expect(points).toHaveTextContent(pair);
  expect(save).not.toHaveBeenCalled();
  fireEvent.click(screen.getByRole('button', { name: 'この内容を確認して保存' }));
  await waitFor(() => expect(save).toHaveBeenCalledTimes(1));
});

test('ゲストの連絡先の取り込み結果を節で示す', async () => {
  const preview = jest.fn().mockResolvedValue({
    ...first,
    contact_imports: [{ type: 'PHONE', value: '090-1234-5678', marketing_result: 'ALLOWED' }],
  });
  render(<Screen preview={preview} save={jest.fn()} />);
  fireEvent.click(screen.getByText('試算する'));
  const contacts = await screen.findByRole('region', { name: '連絡先の取り込み' });
  expect(contacts).toHaveTextContent('電話');
  expect(contacts).toHaveTextContent('090-1234-5678');
  expect(contacts).toHaveTextContent('販促許可を記録');
});

test('コースと明細を 1 枚の明細表に並べ、控除の行と合計を示す', async () => {
  const preview = jest.fn().mockResolvedValue({
    ...first,
    total_duration_minutes: 90,
    total_remuneration: 9000,
    total_fee: 13500,
    fee_lines: [
      {
        kind: 'BASE_COURSE',
        name: '基本',
        amount: 12000,
        remuneration: 7000,
        duration_minutes: 60,
        revision_number: 1,
        system_owned: false,
      },
      {
        kind: 'EXTENSION',
        name: '延長',
        amount: 3000,
        remuneration: 2000,
        duration_minutes: 30,
        system_owned: false,
      },
      { kind: 'DISCOUNT', name: '優待', amount: 1500, remuneration: 0, system_owned: false },
    ],
  });
  render(<Screen preview={preview} save={jest.fn()} />);
  fireEvent.click(screen.getByText('試算する'));
  const fees = await screen.findByRole('region', { name: '料金明細' });
  const table = within(fees).getByRole('table');
  expect(within(table).getAllByRole('row', { name: /基本/ })).toHaveLength(1);
  const course = within(table).getByRole('row', { name: /基本/ });
  expect(course).toHaveTextContent('60分');
  expect(course).toHaveTextContent('版1');
  expect(course).toHaveTextContent('¥12,000');
  expect(course).toHaveTextContent('¥7,000');
  expect(within(table).getByRole('row', { name: /優待/ })).toHaveTextContent('-¥1,500');
  expect(within(table).getByRole('row', { name: /延長/ })).not.toHaveTextContent('-¥');
  const total = within(table).getByRole('row', { name: /合計/ });
  expect(total).toHaveTextContent('90分');
  expect(total).toHaveTextContent('¥13,500');
  expect(total).toHaveTextContent('¥9,000');
});

test('差分の表は明細のコース行をコースの行と重ねて出さない', async () => {
  const base = {
    kind: 'BASE_COURSE' as const,
    name: '基本',
    amount: 12000,
    remuneration: 7000,
    duration_minutes: 60,
    revision_number: 1,
    system_owned: false,
  };
  const extension = {
    kind: 'EXTENSION' as const,
    name: '延長',
    amount: 3000,
    remuneration: 2000,
    duration_minutes: 30,
    system_owned: false,
  };
  const preview = jest
    .fn()
    .mockResolvedValueOnce({ ...first, fee_lines: [base, extension] })
    .mockResolvedValueOnce({
      ...first,
      confirmation_token: 'second',
      course: { ...first.course, price: 15000 },
      fee_lines: [{ ...base, amount: 15000 }, extension],
    });
  const save = jest.fn();
  render(<Screen preview={preview} save={save} />);
  await confirmOnce(save);
  expect(changeRow(/コース料金/)).toHaveAttribute('data-changed', 'true');
  const line = changeRow(/^明細1/);
  expect(line).toHaveTextContent('延長');
  expect(line).toHaveAttribute('data-changed', 'false');
  expect(within(changes()!).queryByRole('row', { name: /^明細2/ })).not.toBeInTheDocument();
});
