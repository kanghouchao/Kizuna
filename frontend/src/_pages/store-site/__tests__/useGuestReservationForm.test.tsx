import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { useGuestReservationForm } from '../model/useGuestReservationForm';
import { guestOrderApplicationApi } from '@/entities/order';

jest.mock('@/entities/order', () => ({
  guestOrderApplicationApi: { request: jest.fn(), consent: jest.fn() },
}));

const FIELDS = [
  ['contact_name', 'お名前'],
  ['contact_phone_number', 'お電話番号'],
  ['contact_email', 'メール'],
  ['contact_line_id', 'LINE ID'],
  ['business_date', 'ご希望日'],
  ['pax', 'ご人数'],
] as const;

/** 見た目を持たないフォーム。テンプレートの画面に依らず、検証と送信だけを確かめる。 */
function ReservationForm({ storeId }: { storeId: string }) {
  const { field, errors, onSubmit, consent, failure, acceptedId } =
    useGuestReservationForm(storeId);
  if (acceptedId !== null) return <p>受付番号: {acceptedId}</p>;
  return (
    <form onSubmit={onSubmit} noValidate>
      {FIELDS.map(([name, label]) => (
        <label key={name}>
          {label}
          <input {...field(name)} />
        </label>
      ))}
      {consent.data && (
        <>
          <label>
            <input type="checkbox" {...field('business_allowed')} />
            {consent.data.business_text}
          </label>
          <label>
            <input type="checkbox" {...field('marketing_allowed')} />
            {consent.data.marketing_text}
          </label>
        </>
      )}
      {Object.values(errors).map(error => (
        <p key={error.message}>{error.message}</p>
      ))}
      {failure && <p>{failure}</p>}
      <button type="submit">送信</button>
    </form>
  );
}

const mockedRequest = guestOrderApplicationApi.request as jest.Mock;

const submitButton = () => screen.getByRole('button', { name: '送信' });

const fillRequiredFields = () => {
  fireEvent.change(screen.getByLabelText('お名前'), { target: { value: 'ゲスト花子' } });
  fireEvent.change(screen.getByLabelText('お電話番号'), { target: { value: '09000000000' } });
  fireEvent.change(screen.getByLabelText('ご希望日'), { target: { value: '2026-08-25' } });
};

describe('useGuestReservationForm', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    jest.mocked(guestOrderApplicationApi.consent).mockResolvedValue({
      version: '1',
      business_text: '今回の業務連絡を許可します',
      marketing_text: '販促を許可します（任意）',
    });
  });

  it('業務連絡の同意がなければ申請を送らない', async () => {
    render(<ReservationForm storeId="7" />);
    fillRequiredFields();
    await screen.findByLabelText('今回の業務連絡を許可します');
    fireEvent.click(submitButton());
    expect(
      await screen.findByText('今回の予約に関する業務連絡への同意が必要です')
    ).toBeInTheDocument();
    expect(mockedRequest).not.toHaveBeenCalled();
  });

  it('販促は独立した任意選択として送る', async () => {
    mockedRequest.mockResolvedValue({ id: 'marketing' });
    render(<ReservationForm storeId="7" />);
    fillRequiredFields();
    const business = await screen.findByLabelText('今回の業務連絡を許可します');
    expect(screen.getByLabelText('販促を許可します（任意）')).not.toBeChecked();
    fireEvent.click(business);
    fireEvent.click(screen.getByLabelText('販促を許可します（任意）'));
    fireEvent.click(submitButton());
    await waitFor(() =>
      expect(mockedRequest).toHaveBeenCalledWith(
        '7',
        expect.objectContaining({
          contact_consent: { version: '1', business_allowed: true, marketing_allowed: true },
        })
      )
    );
  });

  it('文面の版競合では入力を保ち両同意を解除して再同意を求める', async () => {
    mockedRequest.mockRejectedValue({
      response: { status: 409, data: { error: '同意文面が更新されました' } },
    });
    render(<ReservationForm storeId="7" />);
    fillRequiredFields();
    fireEvent.click(await screen.findByLabelText('今回の業務連絡を許可します'));
    fireEvent.click(screen.getByLabelText('販促を許可します（任意）'));
    jest.mocked(guestOrderApplicationApi.consent).mockResolvedValue({
      version: '2',
      business_text: '更新後の業務連絡',
      marketing_text: '更新後の販促（任意）',
    });
    fireEvent.click(submitButton());
    expect(await screen.findByText('同意文面が更新されました')).toBeInTheDocument();
    expect(screen.getByLabelText('更新後の業務連絡')).not.toBeChecked();
    expect(screen.getByLabelText('更新後の販促（任意）')).not.toBeChecked();
    expect(screen.getByLabelText('お名前')).toHaveValue('ゲスト花子');
  });

  it('連絡先と希望内容を匿名の申請端点へ送る', async () => {
    mockedRequest.mockResolvedValue({ id: 'app-1' });

    render(<ReservationForm storeId="7" />);
    fillRequiredFields();
    fireEvent.click(await screen.findByLabelText('今回の業務連絡を許可します'));
    fireEvent.change(screen.getByLabelText('ご人数'), { target: { value: '3' } });
    fireEvent.click(submitButton());

    await waitFor(() => expect(mockedRequest).toHaveBeenCalled());
    expect(mockedRequest).toHaveBeenCalledWith(
      '7',
      expect.objectContaining({
        contact_consent: { version: '1', business_allowed: true, marketing_allowed: false },
        business_date: '2026-08-25',
        pax: 3,
        contact_snapshot: {
          name: 'ゲスト花子',
          phone_number: '09000000000',
          email: '',
          line_id: '',
        },
      })
    );
  });

  it('受け付けられた申請の受付番号を返す', async () => {
    mockedRequest.mockResolvedValue({ id: 'app-1' });

    render(<ReservationForm storeId="7" />);
    fillRequiredFields();
    fireEvent.click(await screen.findByLabelText('今回の業務連絡を許可します'));
    fireEvent.click(submitButton());

    expect(await screen.findByText('受付番号: app-1')).toBeInTheDocument();
  });

  it('電話なしでメールだけの申請を送信できる', async () => {
    jest.mocked(guestOrderApplicationApi.request).mockResolvedValue({ id: 'email-only' });
    render(<ReservationForm storeId="7" />);
    fireEvent.change(screen.getByLabelText('お名前'), { target: { value: 'メールの来客' } });
    fireEvent.change(screen.getByLabelText('ご希望日'), { target: { value: '2026-10-01' } });
    fireEvent.change(screen.getByLabelText('メール'), { target: { value: 'guest@example.com' } });
    fireEvent.click(await screen.findByLabelText('今回の業務連絡を許可します'));
    fireEvent.click(screen.getByRole('button', { name: '送信' }));
    await waitFor(() =>
      expect(guestOrderApplicationApi.request).toHaveBeenCalledWith(
        '7',
        expect.objectContaining({
          contact_snapshot: expect.objectContaining({
            email: 'guest@example.com',
            phone_number: '',
          }),
        })
      )
    );
  });

  it('連絡先が欠けたままでは送らず、欄の傍で理由を述べる', async () => {
    render(<ReservationForm storeId="7" />);
    fireEvent.change(screen.getByLabelText('ご希望日'), { target: { value: '2026-08-25' } });
    fireEvent.click(submitButton());

    // 折返し先の無い申請は店舗が処理しようがない
    expect(await screen.findByText('お名前をご入力ください')).toBeInTheDocument();
    expect(guestOrderApplicationApi.request).not.toHaveBeenCalled();
    expect(mockedRequest).not.toHaveBeenCalled();
  });

  it('サーバが返した理由（流量制限など）をそのまま出す', async () => {
    mockedRequest.mockRejectedValue({
      response: { data: { error: '送信が続いたため受け付けられませんでした' } },
    });

    render(<ReservationForm storeId="7" />);
    fillRequiredFields();
    fireEvent.click(await screen.findByLabelText('今回の業務連絡を許可します'));
    fireEvent.click(submitButton());

    expect(await screen.findByText('送信が続いたため受け付けられませんでした')).toBeInTheDocument();
  });
});
