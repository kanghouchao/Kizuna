import { act, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { CastServiceConditionsPage } from '../CastServiceConditionsPage';
import { shiftApi } from '@/entities/shift';
import { ownServiceApi } from '@/entities/service';

jest.mock('@/shared/notify', () => ({
  notify: { success: jest.fn(), warning: jest.fn(), error: jest.fn() },
}));
jest.mock('@/entities/shift', () => ({
  shiftApi: { myStores: jest.fn().mockResolvedValue([{ store_id: '1', store_name: '店舗一' }]) },
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
  charge_type: 'PAID' as const,
  price: 2000,
  remuneration: 1500,
  terms_version: 1,
  consent_status: 'NOT_ACCEPTED' as const,
  consent_version: 0,
};
const page = (row = item) => ({ rows: [row], page: 0, pageCount: 1, total: 1 });
beforeEach(() => {
  jest.clearAllMocks();
  jest.mocked(ownServiceApi.list).mockResolvedValue(page());
});
it('shows the terms and sends only the viewed versions after confirmation', async () => {
  jest
    .mocked(ownServiceApi.decide)
    .mockResolvedValue({ ...item, consent_status: 'ACCEPTED', consent_version: 1 });
  render(<CastServiceConditionsPage />);
  await screen.findByText('追加');
  expect(screen.getByText('顧客価格: 2,000 円')).toBeInTheDocument();
  expect(screen.getByText('固定報酬: 1,500 円')).toBeInTheDocument();
  fireEvent.click(screen.getByRole('button', { name: '受諾する' }));
  fireEvent.click(screen.getByRole('button', { name: '確認して受諾する' }));
  await waitFor(() =>
    expect(ownServiceApi.decide).toHaveBeenCalledWith('1', 's', {
      terms_version: 1,
      consent_version: 0,
      decision: 'ACCEPTED',
    })
  );
});
it('requires another confirmation after conflicting terms and never auto-resubmits', async () => {
  jest.mocked(ownServiceApi.decide).mockRejectedValue({ response: { status: 409 } });
  render(<CastServiceConditionsPage />);
  await screen.findByText('追加');
  fireEvent.click(screen.getByRole('button', { name: '受諾する' }));
  fireEvent.click(screen.getByRole('button', { name: '確認して受諾する' }));
  await screen.findByText(
    '条件または意思が変更されています。最新の内容を確認してから操作してください。'
  );
  expect(ownServiceApi.decide).toHaveBeenCalledTimes(1);
  expect(screen.queryByRole('button', { name: '確認して受諾する' })).not.toBeInTheDocument();
});
it('shows permission denial without leaking stale conditions or calling it empty data', async () => {
  jest.mocked(ownServiceApi.list).mockRejectedValue({ response: { status: 403 } });
  render(<CastServiceConditionsPage />);
  await screen.findByText('サービス条件を確認する権限がありません。');
  expect(screen.queryByText('追加')).not.toBeInTheDocument();
  expect(screen.queryByText('サービス条件はありません')).not.toBeInTheDocument();
});

it('keeps the remaining conditions reachable when the selected service was deleted', async () => {
  jest.mocked(ownServiceApi.decide).mockRejectedValue({ response: { status: 404 } });
  render(<CastServiceConditionsPage />);
  await screen.findByText('追加');
  jest
    .mocked(ownServiceApi.list)
    .mockResolvedValue(page({ ...item, id: 'remaining', name: '残るサービス' }));
  fireEvent.click(screen.getByRole('button', { name: '受諾する' }));
  fireEvent.click(screen.getByRole('button', { name: '確認して受諾する' }));
  await screen.findByText('残るサービス');
  expect(
    screen.queryByText('有効な在籍またはサービスが見つかりません。店舗を選び直してください。')
  ).not.toBeInTheDocument();
});
it('retries a failed fetch and exposes active rejection separately', async () => {
  jest.mocked(ownServiceApi.list).mockRejectedValueOnce(new Error('network'));
  render(<CastServiceConditionsPage />);
  await screen.findByText('サービス条件の取得に失敗しました');
  fireEvent.click(screen.getByRole('button', { name: '再試行' }));
  await screen.findByText('追加');
  fireEvent.click(screen.getByRole('button', { name: '拒否する' }));
  jest
    .mocked(ownServiceApi.decide)
    .mockResolvedValue({ ...item, consent_status: 'REJECTED', consent_version: 1 });
  jest.mocked(ownServiceApi.list).mockResolvedValue({
    ...page(),
    rows: [{ ...item, consent_status: 'REJECTED', consent_version: 1 }],
  });
  fireEvent.click(screen.getByRole('button', { name: '確認して拒否する' }));
  await screen.findByText('本人が拒否');
});

it('switches stores without exposing the previous response', async () => {
  jest.mocked(shiftApi.myStores).mockResolvedValueOnce([
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
  fireEvent.click(screen.getByRole('combobox', { name: '店舗' }));
  const option = await screen.findByRole('option', { name: '店舗二' });
  fireEvent.pointerDown(option);
  fireEvent.pointerUp(option);
  fireEvent.click(option);
  await screen.findByText('二店の条件');
  finish(page());
  await waitFor(() => expect(screen.queryByText('追加')).not.toBeInTheDocument());
});

it.each([403, 404])('keeps the latest page when an older request fails with %s', async status => {
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
  await screen.findByText('追加');
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
