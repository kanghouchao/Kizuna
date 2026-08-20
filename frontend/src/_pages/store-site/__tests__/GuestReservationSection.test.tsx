import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import GuestReservationSection from '../templates/_sections/GuestReservationSection';
import { guestOrderApplicationApi } from '@/entities/order';

jest.mock('@/entities/order', () => ({
  guestOrderApplicationApi: { request: jest.fn() },
}));

const mockedRequest = guestOrderApplicationApi.request as jest.Mock;

const submitButton = () => screen.getByRole('button', { name: 'この内容で予約を希望する' });

const fillRequiredFields = () => {
  fireEvent.change(screen.getByLabelText('お名前'), { target: { value: 'ゲスト花子' } });
  fireEvent.change(screen.getByLabelText('お電話番号'), { target: { value: '09000000000' } });
  fireEvent.change(screen.getByLabelText('ご希望日'), { target: { value: '2026-08-25' } });
};

describe('GuestReservationSection', () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  it('連絡先と希望内容を匿名の申請端点へ送る', async () => {
    mockedRequest.mockResolvedValue({ id: 'app-1' });

    render(<GuestReservationSection />);
    fillRequiredFields();
    fireEvent.change(screen.getByLabelText('ご人数'), { target: { value: '3' } });
    fireEvent.click(submitButton());

    await waitFor(() => expect(mockedRequest).toHaveBeenCalled());
    expect(mockedRequest).toHaveBeenCalledWith(
      expect.objectContaining({
        business_date: '2026-08-25',
        pax: 3,
        contact_name: 'ゲスト花子',
        contact_phone_number: '09000000000',
      })
    );
  });

  it('送信後は「まだ確定していない」ことと折返し連絡を伝える', async () => {
    mockedRequest.mockResolvedValue({ id: 'app-1' });

    render(<GuestReservationSection />);
    fillRequiredFields();
    fireEvent.click(submitButton());

    // 申請は予約の成立ではない。確定は店舗の折返し連絡を経る（ADR 0017）
    expect(await screen.findByText('ご予約の希望を承りました')).toBeInTheDocument();
    expect(screen.getByText(/まだ予約は確定しておりません/)).toBeInTheDocument();
    expect(screen.queryByLabelText('お名前')).not.toBeInTheDocument();
  });

  it('連絡先が欠けたままでは送らず、欄の傍で理由を述べる', async () => {
    render(<GuestReservationSection />);
    fireEvent.change(screen.getByLabelText('ご希望日'), { target: { value: '2026-08-25' } });
    fireEvent.click(submitButton());

    // 折返し先の無い申請は店舗が処理しようがない
    expect(await screen.findByText('お名前をご入力ください')).toBeInTheDocument();
    expect(screen.getByText('お電話番号をご入力ください')).toBeInTheDocument();
    expect(mockedRequest).not.toHaveBeenCalled();
  });

  it('サーバが返した理由（流量制限など）をそのまま出す', async () => {
    mockedRequest.mockRejectedValue({
      response: { data: { error: '送信が続いたため受け付けられませんでした' } },
    });

    render(<GuestReservationSection />);
    fillRequiredFields();
    fireEvent.click(submitButton());

    expect(await screen.findByText('送信が続いたため受け付けられませんでした')).toBeInTheDocument();
  });
});
