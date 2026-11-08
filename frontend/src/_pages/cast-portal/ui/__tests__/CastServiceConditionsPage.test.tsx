import { act, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { CastServiceConditionsPage } from '../CastServiceConditionsPage';
import { shiftApi } from '@/entities/shift';
import { ownServiceApi } from '@/entities/service';

jest.mock('@/shared/notify', () => ({
  notify: { success: jest.fn(), warning: jest.fn(), error: jest.fn() },
}));
jest.mock('@/entities/shift', () => ({
  shiftApi: { myStores: jest.fn() },
}));
jest.mock('@/entities/service', () => ({
  ownServiceApi: { list: jest.fn(), decide: jest.fn() },
  serviceKindLabels: { COURSE: 'コース', SPECIAL_SERVICE: '特殊サービス', SURCHARGE: '加算' },
}));
const item = {
  id: 's',
  store_id: '1',
  kind: 'SPECIAL_SERVICE' as const,
  name: '追加',
  duration_minutes: 30,
  charge_type: 'PAID' as const,
  price: 2000,
  remuneration: 1500,
  terms_version: 1,
  consent_status: 'NOT_ACCEPTED' as const,
  consent_version: 0,
};
type Item = Omit<typeof item, 'consent_status'> & {
  consent_status?: 'NOT_ACCEPTED' | 'ACCEPTED' | 'REJECTED' | 'RECONFIRMATION_REQUIRED';
};
const page = (row: Item = item) => ({ rows: [row], page: 0, pageCount: 1, total: 1 });
const card = (name: string) => screen.findByRole('region', { name });

beforeEach(() => {
  jest.clearAllMocks();
  jest.mocked(shiftApi.myStores).mockResolvedValue([{ store_id: 1, store_name: '店舗一' }]);
  jest.mocked(ownServiceApi.list).mockResolvedValue(page());
});

it('1 サービス 1 カードにお客様料金とあなたの報酬・状態を書式どおりに出す', async () => {
  render(<CastServiceConditionsPage />);
  const service = await card('追加');
  expect(within(service).getByRole('heading', { name: '追加' })).toBeInTheDocument();
  expect(within(service).getByText('店舗一')).toBeInTheDocument();
  expect(within(service).getByText('特殊サービス · 30分')).toBeInTheDocument();
  expect(within(service).getByText('受諾待ち')).toBeInTheDocument();
  expect(within(service).getByText('お客様料金').nextSibling).toHaveTextContent('¥2,000');
  expect(within(service).getByText('あなたの報酬').nextSibling).toHaveTextContent('¥1,500');
  expect(within(service).queryByText(/円/)).not.toBeInTheDocument();
});

it('受諾の前に下部シートで店舗・サービス・金額を示し、表示した版だけを送る', async () => {
  jest
    .mocked(ownServiceApi.decide)
    .mockResolvedValue({ ...item, consent_status: 'ACCEPTED', consent_version: 1 });
  render(<CastServiceConditionsPage />);
  fireEvent.click(within(await card('追加')).getByRole('button', { name: '受諾する' }));
  const sheet = await screen.findByRole('dialog', { name: 'サービス条件を受諾する' });
  expect(within(sheet).getByText('店舗').nextSibling).toHaveTextContent('店舗一');
  expect(within(sheet).getByText('サービス').nextSibling).toHaveTextContent('追加');
  expect(within(sheet).getByText('お客様料金').nextSibling).toHaveTextContent('¥2,000');
  expect(within(sheet).getByText('あなたの報酬').nextSibling).toHaveTextContent('¥1,500');
  expect(ownServiceApi.decide).not.toHaveBeenCalled();
  fireEvent.click(within(sheet).getByRole('button', { name: '確認して受諾する' }));
  await waitFor(() =>
    expect(ownServiceApi.decide).toHaveBeenCalledWith('1', 's', {
      terms_version: 1,
      consent_version: 0,
      decision: 'ACCEPTED',
    })
  );
});

it('「やめる」では送らずに下部シートを閉じる', async () => {
  render(<CastServiceConditionsPage />);
  fireEvent.click(within(await card('追加')).getByRole('button', { name: '拒否する' }));
  const sheet = await screen.findByRole('dialog', { name: 'サービス条件を拒否する' });
  fireEvent.click(within(sheet).getByRole('button', { name: 'やめる' }));
  await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
  expect(ownServiceApi.decide).not.toHaveBeenCalled();
});

it.each([
  ['ACCEPTED', '受諾済み', ['拒否する']],
  ['REJECTED', '拒否済み', ['受諾する']],
  ['RECONFIRMATION_REQUIRED', '受諾待ち', ['受諾する', '拒否する']],
] as const)('%s は「%s」と出し、今の意思と違う操作だけを出す', async (status, word, buttons) => {
  jest.mocked(ownServiceApi.list).mockResolvedValue(page({ ...item, consent_status: status }));
  render(<CastServiceConditionsPage />);
  const service = await card('追加');
  expect(within(service).getByText(word)).toBeInTheDocument();
  expect(
    within(service)
      .getAllByRole('button')
      .map(button => button.textContent)
  ).toEqual(buttons);
});

it('条件が改定されて受諾し直す必要があるときはそう書き添える', async () => {
  jest
    .mocked(ownServiceApi.list)
    .mockResolvedValue(page({ ...item, consent_status: 'RECONFIRMATION_REQUIRED' }));
  render(<CastServiceConditionsPage />);
  expect(
    within(await card('追加')).getByText('条件が変わりました。内容を確かめてください。')
  ).toBeInTheDocument();
});

it('受諾の対象でないサービスは状態も操作も出さない', async () => {
  jest.mocked(ownServiceApi.list).mockResolvedValue(page({ ...item, consent_status: undefined }));
  render(<CastServiceConditionsPage />);
  const service = await card('追加');
  expect(within(service).queryByRole('button')).not.toBeInTheDocument();
  expect(within(service).queryByText('受諾待ち')).not.toBeInTheDocument();
});

it('無料のサービスはお客様料金を「無料」と出す', async () => {
  jest
    .mocked(ownServiceApi.list)
    .mockResolvedValue(page({ ...item, charge_type: 'FREE' as never, price: 0 }));
  render(<CastServiceConditionsPage />);
  expect(within(await card('追加')).getByText('お客様料金').nextSibling).toHaveTextContent('無料');
});

it('競合のあとは自動で送り直さず、最新の内容を確かめるよう求める', async () => {
  jest.mocked(ownServiceApi.decide).mockRejectedValue({ response: { status: 409 } });
  render(<CastServiceConditionsPage />);
  fireEvent.click(within(await card('追加')).getByRole('button', { name: '受諾する' }));
  fireEvent.click(await screen.findByRole('button', { name: '確認して受諾する' }));
  await screen.findByText(
    '条件または意思が変更されています。最新の内容を確認してから操作してください。'
  );
  expect(ownServiceApi.decide).toHaveBeenCalledTimes(1);
  await waitFor(() =>
    expect(screen.queryByRole('button', { name: '確認して受諾する' })).not.toBeInTheDocument()
  );
});

it('権限が無いときは条件を出さず、空とも言わない', async () => {
  jest.mocked(ownServiceApi.list).mockRejectedValue({ response: { status: 403 } });
  render(<CastServiceConditionsPage />);
  await screen.findByText('サービス条件を確認する権限がありません。');
  expect(screen.queryByText('追加')).not.toBeInTheDocument();
  expect(screen.queryByText('サービス条件はありません')).not.toBeInTheDocument();
});

it('在籍店舗が無いときはそう伝える', async () => {
  jest.mocked(shiftApi.myStores).mockResolvedValue([]);
  render(<CastServiceConditionsPage />);
  await screen.findByText('在籍中の店舗がありません');
  expect(ownServiceApi.list).not.toHaveBeenCalled();
});

it('選んだサービスが消えていても、残りの条件を出し続ける', async () => {
  jest.mocked(ownServiceApi.decide).mockRejectedValue({ response: { status: 404 } });
  render(<CastServiceConditionsPage />);
  const service = await card('追加');
  jest
    .mocked(ownServiceApi.list)
    .mockResolvedValue(page({ ...item, id: 'remaining', name: '残るサービス' }));
  fireEvent.click(within(service).getByRole('button', { name: '受諾する' }));
  fireEvent.click(await screen.findByRole('button', { name: '確認して受諾する' }));
  await card('残るサービス');
  expect(
    screen.queryByText('有効な在籍またはサービスが見つかりません。店舗を選び直してください。')
  ).not.toBeInTheDocument();
});

it('取得の失敗は再試行でき、拒否すると「拒否済み」になる', async () => {
  jest.mocked(ownServiceApi.list).mockRejectedValueOnce(new Error('network'));
  render(<CastServiceConditionsPage />);
  await screen.findByText('サービス条件の取得に失敗しました');
  fireEvent.click(screen.getByRole('button', { name: '再試行' }));
  fireEvent.click(within(await card('追加')).getByRole('button', { name: '拒否する' }));
  jest
    .mocked(ownServiceApi.decide)
    .mockResolvedValue({ ...item, consent_status: 'REJECTED', consent_version: 1 });
  jest
    .mocked(ownServiceApi.list)
    .mockResolvedValue(page({ ...item, consent_status: 'REJECTED', consent_version: 1 }));
  fireEvent.click(await screen.findByRole('button', { name: '確認して拒否する' }));
  await waitFor(async () => expect(within(await card('追加')).getByText('拒否済み')).toBeVisible());
});

it('店舗チップで店舗を替えると、前の店舗の遅れた応答を出さない', async () => {
  jest.mocked(shiftApi.myStores).mockResolvedValue([
    { store_id: 1, store_name: '店舗一' },
    { store_id: 2, store_name: '店舗二' },
  ]);
  let finish!: (value: ReturnType<typeof page>) => void;
  jest.mocked(ownServiceApi.list).mockImplementation(store =>
    store === '1'
      ? new Promise(resolve => {
          finish = resolve;
        })
      : Promise.resolve(page({ ...item, id: 'other', store_id: '2', name: '二店の条件' }))
  );
  render(<CastServiceConditionsPage />);
  await waitFor(() => expect(ownServiceApi.list).toHaveBeenCalledWith('1', 0));
  const chips = await screen.findByRole('group', { name: '店舗' });
  fireEvent.click(within(chips).getByRole('button', { name: '店舗二' }));
  expect(within(await card('二店の条件')).getByText('店舗二')).toBeInTheDocument();
  finish(page());
  await waitFor(() => expect(screen.queryByText('追加')).not.toBeInTheDocument());
});

it.each([403, 404])('古いページの取得が %s で失敗しても最新のページを保つ', async status => {
  let rejectOlder!: (error: unknown) => void;
  let resolveLatest!: (value: ReturnType<typeof page>) => void;
  jest
    .mocked(ownServiceApi.list)
    .mockResolvedValueOnce({ ...page(), pageCount: 3, total: 3 })
    .mockImplementationOnce(
      () =>
        new Promise((_, reject) => {
          rejectOlder = reject;
        })
    )
    .mockImplementationOnce(
      () =>
        new Promise(resolve => {
          resolveLatest = resolve;
        })
    );
  render(<CastServiceConditionsPage />);
  await card('追加');
  const next = screen.getAllByRole('button', { name: '次へ' })[0];
  act(() => {
    fireEvent.click(next);
    fireEvent.click(next);
  });
  expect(ownServiceApi.list).toHaveBeenCalledTimes(3);
  await act(async () => {
    resolveLatest({ ...page({ ...item, name: '最新の条件' }), page: 1 });
  });
  expect(screen.getByText('最新の条件')).toBeInTheDocument();
  await act(async () => {
    rejectOlder({ response: { status } });
  });
  expect(screen.getByText('最新の条件')).toBeInTheDocument();
  expect(screen.queryByRole('alert')).not.toBeInTheDocument();
});
