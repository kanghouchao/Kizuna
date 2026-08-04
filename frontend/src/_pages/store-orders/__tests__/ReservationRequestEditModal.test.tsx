import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { toast } from 'react-hot-toast';
import { ReservationRequestEditModal } from '../ui/ReservationRequestEditModal';
import { Order, orderApi } from '@/entities/order';

jest.mock('@/entities/order', () => ({
  orderApi: { listReceptionists: jest.fn(), updateReservationRequest: jest.fn() },
}));

jest.mock('react-hot-toast', () => ({
  toast: { success: jest.fn(), error: jest.fn() },
}));

const mockedUpdate = orderApi.updateReservationRequest as jest.Mock;
const mockedReceptionists = orderApi.listReceptionists as jest.Mock;

const nominationFreeRequest: Order = {
  id: 'o1',
  status: 'CREATED',
  reception_route: 'WEB',
  business_date: '2026-08-10',
  pax: 3,
  remarks: '元の備考',
};

const nominatedRequest: Order = {
  ...nominationFreeRequest,
  cast_id: 'cast-1',
  cast_name: 'あや',
};

const renderModal = (request: Order, onSaved = jest.fn(), onClose = jest.fn()) =>
  render(<ReservationRequestEditModal request={request} onClose={onClose} onSaved={onSaved} />);

describe('ReservationRequestEditModal', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    mockedReceptionists.mockResolvedValue([]);
    mockedUpdate.mockResolvedValue({});
  });

  it('指名なしの申請を、キャストを埋めずに保存できる', async () => {
    renderModal(nominationFreeRequest);

    fireEvent.change(await screen.findByLabelText('人数'), { target: { value: '5' } });
    fireEvent.click(screen.getByRole('button', { name: '保存する' }));

    await waitFor(() =>
      expect(mockedUpdate).toHaveBeenCalledWith('o1', {
        receptionist_id: undefined,
        cast_id: undefined,
        pax: 5,
        remarks: '元の備考',
      })
    );
  });

  it('指名を外さなければ、そのまま送り返して指名を維持する', async () => {
    // 契約は部分更新ではないため、送らなかった指名は消える。維持は「送り返す」ことで成立する
    renderModal(nominatedRequest);

    fireEvent.click(await screen.findByRole('button', { name: '保存する' }));

    await waitFor(() =>
      expect(mockedUpdate).toHaveBeenCalledWith(
        'o1',
        expect.objectContaining({ cast_id: 'cast-1' })
      )
    );
  });

  it('指名を外すと、キャストを送らずに保存して一覧の取り直しを促す', async () => {
    const onSaved = jest.fn();
    const onClose = jest.fn();
    renderModal(nominatedRequest, onSaved, onClose);

    fireEvent.click(await screen.findByRole('checkbox', { name: '指名を外す' }));
    fireEvent.click(screen.getByRole('button', { name: '保存する' }));

    await waitFor(() =>
      expect(mockedUpdate).toHaveBeenCalledWith(
        'o1',
        expect.objectContaining({ cast_id: undefined })
      )
    );
    expect(onSaved).toHaveBeenCalled();
    expect(onClose).toHaveBeenCalled();
  });

  it('指名が無い申請には指名解除の操作を出さない', async () => {
    renderModal(nominationFreeRequest);

    expect(await screen.findByText('なし')).toBeInTheDocument();
    expect(screen.queryByRole('checkbox', { name: '指名を外す' })).not.toBeInTheDocument();
  });

  it('人数が空欄なら送信せず、無反応にもせず理由を出す', async () => {
    // 検証の結果を出さないと、押した保存が効いているのか分からないまま止まる
    renderModal(nominationFreeRequest);

    fireEvent.change(await screen.findByLabelText('人数'), { target: { value: '' } });
    fireEvent.click(screen.getByRole('button', { name: '保存する' }));

    expect(await screen.findByText('人数を入力してください')).toBeInTheDocument();
    expect(mockedUpdate).not.toHaveBeenCalled();
  });

  it('備考がサーバ側の上限を超えたら送信せず理由を出す', async () => {
    renderModal(nominationFreeRequest);

    fireEvent.change(await screen.findByLabelText('備考'), {
      target: { value: 'あ'.repeat(501) },
    });
    fireEvent.click(screen.getByRole('button', { name: '保存する' }));

    expect(await screen.findByText('備考は 500 文字以内で入力してください')).toBeInTheDocument();
    expect(mockedUpdate).not.toHaveBeenCalled();
  });

  it('保存に失敗したら、対処方法を含むサーバの文言をそのまま出す', async () => {
    // 指名の検証は「在籍中を選ぶか外すか」を伝える 400 を返す。汎用文言に潰すと行動できない
    mockedUpdate.mockRejectedValue({
      response: {
        status: 400,
        data: {
          error: '指名できるキャストではありません。在籍中のキャストを選ぶか、指名を外してください',
        },
      },
    });
    const onSaved = jest.fn();
    renderModal(nominatedRequest, onSaved);

    fireEvent.click(await screen.findByRole('button', { name: '保存する' }));

    await waitFor(() =>
      expect(toast.error).toHaveBeenCalledWith(
        '指名できるキャストではありません。在籍中のキャストを選ぶか、指名を外してください'
      )
    );
    expect(onSaved).not.toHaveBeenCalled();
  });
});
