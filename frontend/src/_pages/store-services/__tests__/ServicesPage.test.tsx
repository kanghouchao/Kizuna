import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import ServicesPage from '../ui/ServicesPage';
import { serviceApi } from '@/entities/service';
import { readTokenClaims } from '@/shared/lib';

jest.mock('@/shared/lib', () => ({
  ...jest.requireActual('@/shared/lib'),
  readTokenClaims: jest.fn(),
}));
jest.mock('@/entities/service', () => ({
  serviceApi: {
    list: jest.fn(),
    get: jest.fn(),
    create: jest.fn(),
    update: jest.fn(),
    remove: jest.fn(),
    history: jest.fn(),
  },
  serviceKindLabels: { COURSE: 'コース', SPECIAL_SERVICE: '特殊サービス', SURCHARGE: '加算' },
}));
jest.mock('next/navigation', () => ({ useParams: () => ({ storeId: '1' }) }));
jest.mock('@/shared/notify', () => ({
  notify: { success: jest.fn(), error: jest.fn(), warning: jest.fn() },
}));
const api = jest.mocked(serviceApi);
const claims = jest.mocked(readTokenClaims);
beforeEach(() => {
  jest.clearAllMocks();
  claims.mockReturnValue({ authorities: ['PERM_SERVICE_MANAGE'] } as never);
  api.list.mockResolvedValue({ rows: [], page: 0, pageCount: 0, total: 0 });
  api.create.mockResolvedValue({ id: '10' });
});
it('権限がない利用者には設定値も操作も取得しない', async () => {
  claims.mockReturnValue({ authorities: ['PERM_ORDER_MANAGE'] } as never);
  render(<ServicesPage />);
  expect(await screen.findByRole('alert')).toHaveTextContent('サービス設定の権限がありません');
  expect(api.list).not.toHaveBeenCalled();
  expect(screen.queryByRole('button', { name: '新規作成' })).not.toBeInTheDocument();
});
it('コースを入力し整数円で保存できる', async () => {
  render(<ServicesPage />);
  fireEvent.click(await screen.findByRole('button', { name: '新規作成' }));
  fireEvent.change(screen.getByLabelText('名称'), { target: { value: '基本' } });
  fireEvent.change(screen.getByLabelText('所要時間（分）'), { target: { value: '60' } });
  fireEvent.change(screen.getByLabelText('価格（円）'), { target: { value: '12000' } });
  fireEvent.change(screen.getByLabelText('固定報酬（円）'), { target: { value: '7000' } });
  fireEvent.click(screen.getByRole('button', { name: '保存する' }));
  await waitFor(() =>
    expect(api.create).toHaveBeenCalledWith({
      kind: 'COURSE',
      name: '基本',
      duration_minutes: 60,
      charge_type: undefined,
      price: 12000,
      remuneration: 7000,
    })
  );
});

const item = {
  id: '10',
  kind: 'COURSE' as const,
  name: '基本',
  duration_minutes: 60,
  price: 12000,
  remuneration: 7000,
  version: 1,
  deleted: false,
  created_at: '2026-09-14T00:00:00Z',
  updated_at: '2026-09-14T00:00:00Z',
};
function seedItem() {
  api.list.mockResolvedValue({ rows: [item], page: 0, pageCount: 1, total: 1 });
  api.get.mockResolvedValue(item);
}
it('価格より大きい報酬は入力横に示し保存しない', async () => {
  seedItem();
  render(<ServicesPage />);
  fireEvent.click(await screen.findByRole('button', { name: '編集' }));
  fireEvent.change(await screen.findByLabelText('固定報酬（円）'), { target: { value: '12001' } });
  fireEvent.click(screen.getByRole('button', { name: '保存する' }));
  expect(await screen.findByText('報酬は価格以下で入力してください')).toBeInTheDocument();
  expect(api.update).not.toHaveBeenCalled();
});
it('更新競合では再取得した条件を再確認してから保存する', async () => {
  seedItem();
  api.update
    .mockRejectedValueOnce({ response: { status: 409 } })
    .mockResolvedValue({ ...item, version: 3 });
  render(<ServicesPage />);
  fireEvent.click(await screen.findByRole('button', { name: '編集' }));
  fireEvent.change(await screen.findByLabelText('名称'), { target: { value: '自分の変更' } });
  fireEvent.click(screen.getByRole('button', { name: '保存する' }));
  const reload = await screen.findByRole('button', { name: '最新の内容を再取得' });
  expect(screen.getByRole('button', { name: '保存する' })).toBeDisabled();
  expect(screen.getByLabelText('名称')).toHaveValue('自分の変更');
  api.get.mockResolvedValue({ ...item, name: '別担当の変更', version: 2 });
  fireEvent.click(reload);
  await waitFor(() => expect(screen.getByLabelText('名称')).toHaveValue('別担当の変更'));
  expect(api.update).toHaveBeenCalledTimes(1);
  fireEvent.click(screen.getByRole('button', { name: '保存する' }));
  await waitFor(() =>
    expect(api.update).toHaveBeenLastCalledWith(
      '10',
      expect.objectContaining({ expected_version: 2, name: '別担当の変更' })
    )
  );
});
it('削除は確認後にのみ実行し古い版本を送る', async () => {
  seedItem();
  api.remove.mockResolvedValue();
  render(<ServicesPage />);
  fireEvent.click(await screen.findByRole('button', { name: '削除' }));
  expect(api.remove).not.toHaveBeenCalled();
  expect(screen.getByRole('alertdialog')).toHaveTextContent('版本 1');
  fireEvent.click(screen.getByRole('button', { name: 'キャンセル' }));
  expect(api.remove).not.toHaveBeenCalled();
  fireEvent.click(screen.getByRole('button', { name: '削除' }));
  fireEvent.click(screen.getByRole('button', { name: '削除する' }));
  await waitFor(() => expect(api.remove).toHaveBeenCalledWith('10', 1));
});
it('取得失敗を空一覧にせず再試行できる', async () => {
  api.list.mockRejectedValueOnce(new Error('offline'));
  render(<ServicesPage />);
  expect(await screen.findByRole('alert')).toHaveTextContent('サービス一覧の取得に失敗しました');
  expect(screen.queryByText('該当するサービスはありません')).not.toBeInTheDocument();
  fireEvent.click(screen.getByRole('button', { name: '再試行' }));
  expect(await screen.findByText('該当するサービスはありません')).toBeInTheDocument();
});
it('削除済みの項目は履歴だけを開き前後の金額を照会できる', async () => {
  api.list.mockResolvedValue({
    rows: [{ ...item, deleted: true }],
    page: 0,
    pageCount: 1,
    total: 1,
  });
  api.history.mockResolvedValue({
    rows: [
      {
        id: 'r2',
        version: 2,
        operation: 'DELETED',
        actor_id: '3',
        occurred_at: item.created_at,
        before: item,
        after: { ...item, deleted: true, version: 2 },
      },
    ],
    nextCursor: null,
  });
  render(<ServicesPage />);
  fireEvent.click(await screen.findByRole('button', { name: '履歴' }));
  expect(screen.queryByRole('button', { name: '編集' })).not.toBeInTheDocument();
  expect(screen.queryByRole('button', { name: '削除' })).not.toBeInTheDocument();
  expect(await screen.findByText('版本 2 · 削除')).toBeInTheDocument();
  expect(within(screen.getByRole('dialog')).getAllByText('7,000 円')).toHaveLength(2);
});
it.each(['編集', '履歴'])('%s の取得で権限不足になったら設定値を消して案内する', async action => {
  seedItem();
  api.get.mockRejectedValue({ response: { status: 403 } });
  api.history.mockRejectedValue({ response: { status: 403 } });
  render(<ServicesPage />);
  fireEvent.click(await screen.findByRole('button', { name: action }));
  expect(await screen.findByRole('alert')).toHaveTextContent('サービス設定の権限がありません');
  expect(screen.queryByText('12,000 円')).not.toBeInTheDocument();
  expect(screen.queryByRole('button', { name: '再試行' })).not.toBeInTheDocument();
});
it('存在しない詳細を Escape で閉じても一覧を再取得する', async () => {
  seedItem();
  api.get.mockRejectedValue({ response: { status: 404 } });
  render(<ServicesPage />);
  fireEvent.click(await screen.findByRole('button', { name: '編集' }));
  expect(await screen.findByRole('alert')).toHaveTextContent(
    'サービスが存在しないか削除されています'
  );
  api.list.mockResolvedValue({ rows: [], page: 0, pageCount: 0, total: 0 });
  fireEvent.keyDown(screen.getByRole('dialog'), { key: 'Escape', code: 'Escape' });
  await waitFor(() => expect(api.list).toHaveBeenCalledTimes(2));
  expect(await screen.findByText('該当するサービスはありません')).toBeInTheDocument();
});

it.each([400, 404])(
  '編集中に削除された項目は %s 応答後に入力を閉じて一覧を更新する',
  async status => {
    seedItem();
    api.update.mockRejectedValueOnce({ response: { status } });
    render(<ServicesPage />);
    fireEvent.click(await screen.findByRole('button', { name: '編集' }));
    await screen.findByLabelText('名称');
    api.get.mockResolvedValue({ ...item, deleted: true, version: 2 });
    api.list.mockResolvedValue({ rows: [], page: 0, pageCount: 0, total: 0 });
    fireEvent.click(screen.getByRole('button', { name: '保存する' }));
    expect(await screen.findByRole('alert')).toHaveTextContent(
      'サービスが存在しないか削除されています'
    );
    expect(screen.queryByRole('button', { name: '保存する' })).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: '閉じる' }));
    expect(await screen.findByText('該当するサービスはありません')).toBeInTheDocument();
    expect(api.update).toHaveBeenCalledTimes(1);
  }
);

it.each([400, 404, 409])('削除の %s 応答後は一覧を更新し古い項目を除く', async status => {
  seedItem();
  api.remove.mockRejectedValueOnce({ response: { status } });
  render(<ServicesPage />);
  fireEvent.click(await screen.findByRole('button', { name: '削除' }));
  api.list.mockResolvedValue({ rows: [], page: 0, pageCount: 0, total: 0 });
  fireEvent.click(
    within(screen.getByRole('alertdialog')).getByRole('button', { name: '削除する' })
  );
  expect(await screen.findByText('該当するサービスはありません')).toBeInTheDocument();
  expect(api.remove).toHaveBeenCalledTimes(1);
  expect(api.list).toHaveBeenCalledTimes(2);
});
