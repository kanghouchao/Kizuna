import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { notify } from '@/shared/notify';
import OrderPointRollbackPage from '../ui/OrderPointRollbackPage';
import { Order, OrderPointRollbackPreview, orderApi } from '@/entities/order';
import { AxiosError } from 'axios';

jest.mock('@/entities/order', () => ({
  orderApi: {
    get: jest.fn(),
    pointRollbackPreview: jest.fn(),
    pointRollback: jest.fn(),
  },
}));

jest.mock('next/navigation', () => ({
  useRouter: () => ({ push: jest.fn(), back: jest.fn() }),
  useParams: () => ({ storeId: '1', id: 'o1' }),
}));

jest.mock('@/shared/notify', () => ({
  notify: { success: jest.fn(), error: jest.fn(), warning: jest.fn() },
}));

const mockedGet = orderApi.get as jest.Mock;
const mockedPreview = orderApi.pointRollbackPreview as jest.Mock;
const mockedRollback = orderApi.pointRollback as jest.Mock;

const completedOrder: Order = {
  id: 'o1',
  status: 'COMPLETED',
  business_date: '2026-08-29',
  customer_name: '山田太郎',
};

function preview(overrides: Partial<OrderPointRollbackPreview> = {}): OrderPointRollbackPreview {
  return {
    already_rolled_back: false,
    member_code: '123456789012',
    cancellable_points: 120,
    reversible_used_points: 300,
    ...overrides,
  };
}

beforeEach(() => {
  jest.clearAllMocks();
  mockedGet.mockResolvedValue(completedOrder);
  mockedPreview.mockResolvedValue(preview());
});

describe('ポイント巻き戻しのページ', () => {
  it('実行前に動く量と宛先の会員を示し、理由を添えて巻き戻すこと', async () => {
    mockedRollback.mockResolvedValue({ cancelled_points: 120, restored_points: 300 });
    render(<OrderPointRollbackPage />);

    expect(await screen.findByText('120 pt')).toBeInTheDocument();
    expect(screen.getByText('300 pt')).toBeInTheDocument();
    expect(screen.getByText(/123456789012/)).toBeInTheDocument();

    fireEvent.change(screen.getByLabelText('理由'), { target: { value: '誤完了の全否定' } });
    fireEvent.click(screen.getByRole('button', { name: '巻き戻す' }));

    await waitFor(() =>
      expect(mockedRollback).toHaveBeenCalledWith('o1', { reason: '誤完了の全否定' })
    );
    expect(await screen.findByText('巻き戻しました')).toBeInTheDocument();
  });

  it('打ち消す対象がゼロなら、元のロットへ返したとは名乗らないこと', async () => {
    // 仕訳ゼロの受注でも操作記録は書かれる。動いていないものを動いたと書くと、台帳を見に行った先で食い違う
    mockedPreview.mockResolvedValue(
      preview({ member_code: undefined, cancellable_points: 0, reversible_used_points: 0 })
    );
    mockedRollback.mockResolvedValue({ cancelled_points: 0, restored_points: 0 });
    render(<OrderPointRollbackPage />);

    fireEvent.change(await screen.findByLabelText('理由'), { target: { value: '無帰属の清零' } });
    fireEvent.click(screen.getByRole('button', { name: '巻き戻す' }));

    expect(await screen.findByText(/打ち消す対象はありませんでした/)).toBeInTheDocument();
    expect(screen.queryByText(/元のロットへ期限そのまま返っています/)).not.toBeInTheDocument();
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
    // 開いてから 400 を返すより、開いた時点で「打ち消すものが無い」を名乗る
    mockedGet.mockResolvedValue({ ...completedOrder, status: 'CONFIRMED' });
    render(<OrderPointRollbackPage />);

    expect(await screen.findByText(/完了した受注だけが/)).toBeInTheDocument();
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

    await waitFor(() => expect(notify.warning).toHaveBeenCalled());
    expect(await screen.findByText(/既に巻き戻し済みです/)).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '巻き戻す' })).not.toBeInTheDocument();
  });
});
