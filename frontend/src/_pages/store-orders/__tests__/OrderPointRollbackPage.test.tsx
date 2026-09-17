import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { notify } from '@/shared/notify';
import OrderPointRollbackPage from '../ui/OrderPointRollbackPage';
import { Order, OrderPointRollbackPreview, orderApi } from '@/entities/order';
import { AxiosError } from 'axios';
import { hasPermission } from '@/shared/lib';
jest.mock('@/shared/lib', () => ({
  ...jest.requireActual('@/shared/lib'),
  hasPermission: jest.fn(() => true),
}));

jest.mock('@/entities/order', () => ({
  orderApi: {
    get: jest.fn(),
    pointRollbackPreview: jest.fn(),
    pointRollback: jest.fn(),
  },
}));

let mockParams = { storeId: '1', id: 'o1' };
jest.mock('next/navigation', () => ({
  useRouter: () => ({ push: jest.fn(), back: jest.fn() }),
  useParams: () => mockParams,
}));

jest.mock('@/shared/notify', () => ({
  notify: { success: jest.fn(), error: jest.fn(), warning: jest.fn() },
}));

const mockedGet = orderApi.get as jest.Mock;
const mockedPreview = orderApi.pointRollbackPreview as jest.Mock;
const mockedRollback = orderApi.pointRollback as jest.Mock;

const completedOrder: Order = {
  accrued_remuneration: 0,
  total_duration_minutes: 60,
  total_remuneration: 7000,
  requires_attention: false,
  unresolved_special_service_count: 0,
  special_services: [],
  course: {
    service_id: 'course-1',
    revision_id: 'r1',
    revision_number: 1,
    name: '基本',
    duration_minutes: 60,
    price: 12000,
    remuneration: 7000,
    adoption_basis: 'CURRENT_SETTING' as const,
    adopted_at: '2026-09-15T00:00:00Z',
  },
  fee_lines: [],
  id: 'o1',
  status: 'COMPLETED',
  business_date: '2026-08-29',
  customer_name: '山田太郎',
};

function result(overrides = {}) {
  return {
    id: '1',
    reason: '誤完了',
    actor_user_id: 42,
    created_at: '2026-09-17T10:00:00Z',
    before_total_fee: 11700,
    offset_amount: 300,
    after_total_fee: 12000,
    cancelled_points: 120,
    restored_points: 300,
    ...overrides,
  };
}

function preview(overrides: Partial<OrderPointRollbackPreview> = {}): OrderPointRollbackPreview {
  return {
    already_rolled_back: false,
    member_code: '123456789012',
    cancellable_points: 120,
    reversible_used_points: 300,
    current_total_fee: 11700,
    offset_amount: 300,
    resulting_total_fee: 12000,
    ...overrides,
  };
}

beforeEach(() => {
  jest.clearAllMocks();
  mockParams = { storeId: '1', id: 'o1' };
  (hasPermission as jest.Mock).mockReturnValue(true);
  mockedGet.mockResolvedValue(completedOrder);
  mockedPreview.mockResolvedValue(preview());
});

describe('ポイント巻き戻しのページ', () => {
  it('権限不足では担当者と専用入口を示し会員情報を取得しないこと', async () => {
    (hasPermission as jest.Mock).mockReturnValue(false);
    render(<OrderPointRollbackPage />);
    expect(await screen.findByText(/ポイント救済担当者へ依頼/)).toBeInTheDocument();
    expect(mockedGet).not.toHaveBeenCalled();
    expect(mockedPreview).not.toHaveBeenCalled();
    expect(screen.queryByText(/123456789012/)).not.toBeInTheDocument();
  });

  it('取得失敗を空の見込みと扱わず再試行で復帰すること', async () => {
    mockedPreview.mockRejectedValueOnce(new Error('network'));
    render(<OrderPointRollbackPage />);
    expect(await screen.findByText('受注を取得できませんでした。')).toBeInTheDocument();
    expect(screen.queryByLabelText('理由')).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: /再試行/ }));
    expect(await screen.findByLabelText('理由')).toBeInTheDocument();
  });

  it('変更競合の後は新しい金額を再確認してから送ること', async () => {
    mockedRollback
      .mockRejectedValueOnce(
        new AxiosError('conflict', undefined, undefined, undefined, {
          status: 409,
          data: { error: '請求が変わりました' },
        } as never)
      )
      .mockResolvedValue(result());
    mockedPreview
      .mockResolvedValueOnce(preview())
      .mockResolvedValue(preview({ current_total_fee: 12700, resulting_total_fee: 13000 }));
    render(<OrderPointRollbackPage />);
    fireEvent.change(await screen.findByLabelText('理由'), { target: { value: '確認' } });
    fireEvent.click(screen.getByRole('button', { name: '巻き戻す' }));
    expect(mockedRollback).not.toHaveBeenCalled();
    fireEvent.click(await screen.findByRole('button', { name: '実行する' }));
    expect(await screen.findByText('12,700 円')).toBeInTheDocument();
    expect(mockedRollback).toHaveBeenCalledTimes(1);
    fireEvent.click(screen.getByRole('button', { name: '巻き戻す' }));
    fireEvent.click(await screen.findByRole('button', { name: '実行する' }));
    await waitFor(() =>
      expect(mockedRollback).toHaveBeenLastCalledWith('o1', {
        reason: '確認',
        expected_total_fee: 12700,
        expected_offset_amount: 300,
      })
    );
  });

  it('再訪で保存済みの理由と請求履歴を表示し店舗切替では消すこと', async () => {
    mockedPreview.mockResolvedValueOnce(
      preview({ already_rolled_back: true, rollback: result({ reason: '保存した理由' }) })
    );
    const view = render(<OrderPointRollbackPage />);
    expect(await screen.findByText('理由：保存した理由')).toBeInTheDocument();
    mockedPreview.mockImplementation(() => new Promise(() => {}));
    mockParams = { storeId: '2', id: 'o1' };
    view.rerender(<OrderPointRollbackPage />);
    expect(screen.queryByText('理由：保存した理由')).not.toBeInTheDocument();
    expect(screen.queryByText(/123456789012/)).not.toBeInTheDocument();
  });

  it('実行前に動く量と宛先の会員を示し、理由を添えて巻き戻すこと', async () => {
    mockedRollback.mockResolvedValue(result());
    render(<OrderPointRollbackPage />);

    expect(await screen.findByText('120 pt')).toBeInTheDocument();
    expect(screen.getByText('300 pt')).toBeInTheDocument();
    expect(screen.getByText(/123456789012/)).toBeInTheDocument();
    expect(screen.getByText('11,700 円')).toBeInTheDocument();
    expect(screen.getByText('12,000 円')).toBeInTheDocument();

    fireEvent.change(screen.getByLabelText('理由'), { target: { value: '誤完了の全否定' } });
    fireEvent.click(screen.getByRole('button', { name: '巻き戻す' }));
    fireEvent.click(await screen.findByRole('button', { name: '実行する' }));

    await waitFor(() =>
      expect(mockedRollback).toHaveBeenCalledWith('o1', {
        reason: '誤完了の全否定',
        expected_total_fee: 11700,
        expected_offset_amount: 300,
      })
    );
    expect(await screen.findByText('巻き戻しました')).toBeInTheDocument();
  });

  it('打ち消す対象がゼロなら、元のロットへ返したとは名乗らないこと', async () => {
    // 仕訳ゼロの受注でも操作記録は書かれる。動いていないものを動いたと書くと、台帳を見に行った先で食い違う
    mockedPreview.mockResolvedValue(
      preview({ member_code: undefined, cancellable_points: 0, reversible_used_points: 0 })
    );
    mockedRollback.mockResolvedValue(result({ cancelled_points: 0, restored_points: 0 }));
    render(<OrderPointRollbackPage />);

    fireEvent.change(await screen.findByLabelText('理由'), { target: { value: '無帰属の清零' } });
    fireEvent.click(screen.getByRole('button', { name: '巻き戻す' }));
    fireEvent.click(await screen.findByRole('button', { name: '実行する' }));

    expect(await screen.findByText(/打ち消す対象はありませんでした/)).toBeInTheDocument();
    expect(screen.queryByText(/元のロットへ期限そのまま返っています/)).not.toBeInTheDocument();
  });

  it('付与だけを打ち消した回に「対象が無かった」と名乗らないこと', async () => {
    // 利用の無い受注が普通なので、逆転の有無だけで判じると取り消した額を並べながら否定してしまう
    mockedPreview.mockResolvedValue(preview({ reversible_used_points: 0 }));
    mockedRollback.mockResolvedValue(result({ restored_points: 0 }));
    render(<OrderPointRollbackPage />);

    fireEvent.change(await screen.findByLabelText('理由'), { target: { value: '誤完了' } });
    fireEvent.click(screen.getByRole('button', { name: '巻き戻す' }));
    fireEvent.click(await screen.findByRole('button', { name: '実行する' }));

    expect(await screen.findByText(/未消費の付与を取り消しました/)).toBeInTheDocument();
    expect(screen.queryByText(/打ち消す対象はありませんでした/)).not.toBeInTheDocument();
  });

  it('理由が空欄なら送らず理由を出すこと（台帳の行だけを見て説明が辿れなくなる）', async () => {
    render(<OrderPointRollbackPage />);

    fireEvent.click(await screen.findByRole('button', { name: '巻き戻す' }));

    expect(await screen.findByText('巻き戻しの理由を入力してください')).toBeInTheDocument();
    expect(mockedRollback).not.toHaveBeenCalled();
  });

  it('巻き戻し済みの受注では実行の導線を出さないこと（二度目は撥ねられる）', async () => {
    mockedPreview.mockResolvedValue(
      preview({ already_rolled_back: true, cancellable_points: 0, reversible_used_points: 0 })
    );
    render(<OrderPointRollbackPage />);

    expect(await screen.findByText(/既に巻き戻し済みです/)).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '巻き戻す' })).not.toBeInTheDocument();
  });

  it('完了していない受注では欄そのものを出さず、理由を名乗ること', async () => {
    mockedGet.mockResolvedValue({ ...completedOrder, status: 'CONFIRMED' });
    render(<OrderPointRollbackPage />);

    expect(await screen.findByText(/完了した受注だけが/)).toBeInTheDocument();
    expect(mockedPreview).not.toHaveBeenCalled();
    expect(screen.queryByRole('button', { name: '巻き戻す' })).not.toBeInTheDocument();
  });

  it('帰属していない受注では宛先の会員を名乗らず、申領を塞ぐことだけを告げること', async () => {
    mockedPreview.mockResolvedValue(
      preview({ member_code: undefined, cancellable_points: 0, reversible_used_points: 0 })
    );
    render(<OrderPointRollbackPage />);

    expect(await screen.findByText(/会員に帰属していません/)).toBeInTheDocument();
  });

  it('二度目の 409 は済みの姿へ落とし、その場の再送を残さないこと', async () => {
    // 初回の理由・実行者はサーバに残る。取り直さないと画面は何度でも 409 を出せる形のままになる
    mockedRollback.mockRejectedValue(
      new AxiosError('conflict', undefined, undefined, undefined, {
        status: 409,
        data: { error: 'この受注のポイントは既に巻き戻されています' },
      } as never)
    );
    mockedPreview
      .mockResolvedValueOnce(preview())
      .mockResolvedValue(
        preview({ already_rolled_back: true, cancellable_points: 0, reversible_used_points: 0 })
      );
    render(<OrderPointRollbackPage />);

    fireEvent.change(await screen.findByLabelText('理由'), { target: { value: '二度目' } });
    fireEvent.click(screen.getByRole('button', { name: '巻き戻す' }));
    fireEvent.click(await screen.findByRole('button', { name: '実行する' }));

    await waitFor(() => expect(notify.warning).toHaveBeenCalled());
    expect(await screen.findByText(/既に巻き戻し済みです/)).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '巻き戻す' })).not.toBeInTheDocument();
  });
});
