import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemberReservationNewPage } from '../MemberReservationNewPage';
import { memberOrderApi } from '@/entities/order';
import { shiftApi } from '@/entities/shift';
import { platformStoreApi } from '@/entities/store';

const mockPush = jest.fn();
let searchParams = new URLSearchParams();

jest.mock('next/navigation', () => ({
  useRouter: () => ({ push: mockPush }),
  useSearchParams: () => searchParams,
}));

jest.mock('@/entities/order', () => ({
  memberOrderApi: { create: jest.fn() },
}));
jest.mock('@/entities/shift', () => ({
  shiftApi: { confirmedCasts: jest.fn() },
}));
jest.mock('@/entities/store', () => ({
  platformStoreApi: { lookupByDomain: jest.fn() },
}));

const mockedLookup = platformStoreApi.lookupByDomain as jest.Mock;
const mockedCasts = shiftApi.confirmedCasts as jest.Mock;
const mockedCreate = memberOrderApi.create as jest.Mock;

describe('MemberReservationNewPage', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    searchParams = new URLSearchParams('store=store1.kizuna.test');
    mockedCasts.mockResolvedValue([]);
  });

  it('公式サイトから引き継いだドメインを照会して店舗をプリセレクトする', async () => {
    mockedLookup.mockResolvedValue({ id: '1', name: 'サンプル店舗' });

    render(<MemberReservationNewPage />);

    expect(await screen.findByText('サンプル店舗')).toBeInTheDocument();
    expect(mockedLookup).toHaveBeenCalledWith('store1.kizuna.test');
  });

  it('店舗を特定できないときはフォームを出さず案内だけ表示する', async () => {
    mockedLookup.mockRejectedValue(new Error('not found'));

    render(<MemberReservationNewPage />);

    expect(
      await screen.findByText(
        '店舗が特定できませんでした。店舗公式サイトの予約ボタンからお進みください。'
      )
    ).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'この内容で申請する' })).not.toBeInTheDocument();
  });

  it('店舗パラメータが無いときも案内だけ表示する', async () => {
    searchParams = new URLSearchParams();

    render(<MemberReservationNewPage />);

    expect(
      await screen.findByText(
        '店舗が特定できませんでした。店舗公式サイトの予約ボタンからお進みください。'
      )
    ).toBeInTheDocument();
    expect(mockedLookup).not.toHaveBeenCalled();
  });

  it('利用日を選ぶとその日の確定シフトのキャストを指名候補に出す', async () => {
    mockedLookup.mockResolvedValue({ id: '1', name: 'サンプル店舗' });
    mockedCasts.mockResolvedValue([{ cast_id: 'c1', cast_name: 'さくら', start_time: '18:00:00' }]);

    render(<MemberReservationNewPage />);

    fireEvent.change(await screen.findByLabelText('利用日'), { target: { value: '2026-08-10' } });

    await waitFor(() =>
      expect(mockedCasts).toHaveBeenCalledWith({ store_id: 1, date: '2026-08-10' })
    );
    expect(await screen.findByRole('option', { name: 'さくら（18:00〜）' })).toBeInTheDocument();
  });

  it('照会で得た店舗 ID と人数を添えて申請する', async () => {
    mockedLookup.mockResolvedValue({ id: '7', name: 'サンプル店舗' });
    mockedCreate.mockResolvedValue({});

    render(<MemberReservationNewPage />);

    fireEvent.change(await screen.findByLabelText('利用日'), { target: { value: '2026-08-10' } });
    const pax = screen.getByLabelText('人数');
    fireEvent.change(pax, { target: { value: '' } });
    fireEvent.change(pax, { target: { value: '3' } });
    fireEvent.click(screen.getByRole('button', { name: 'この内容で申請する' }));

    await waitFor(() =>
      expect(mockedCreate).toHaveBeenCalledWith(
        expect.objectContaining({ store_id: 7, business_date: '2026-08-10', pax: 3 })
      )
    );
    expect(mockPush).toHaveBeenCalledWith('/member/reservations/');
  });

  it('人数が未入力なら申請せずに検証エラーを出す', async () => {
    mockedLookup.mockResolvedValue({ id: '1', name: 'サンプル店舗' });

    render(<MemberReservationNewPage />);

    fireEvent.change(await screen.findByLabelText('利用日'), { target: { value: '2026-08-10' } });
    fireEvent.change(screen.getByLabelText('人数'), { target: { value: '' } });
    fireEvent.click(screen.getByRole('button', { name: 'この内容で申請する' }));

    expect(await screen.findByText('人数を入力してください')).toBeInTheDocument();
    expect(mockedCreate).not.toHaveBeenCalled();
  });
});
