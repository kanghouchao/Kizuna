import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { CustomerContactsSection } from '../ui/CustomerContactsSection';
import { customerApi } from '@/entities/customer';
import { notify } from '@/shared/notify';

jest.mock('@/entities/customer', () => ({
  customerApi: {
    contacts: jest.fn(),
    contactHistory: jest.fn(),
    addContact: jest.fn(),
    updateContact: jest.fn(),
    deleteContact: jest.fn(),
    setContactPreference: jest.fn(),
    setContactPermission: jest.fn(),
  },
}));
jest.mock('@/shared/notify', () => ({
  notify: { success: jest.fn(), error: jest.fn(), warning: jest.fn() },
}));

beforeEach(() => {
  jest.clearAllMocks();
  (customerApi.contacts as jest.Mock).mockResolvedValue({ rows: [], nextCursor: undefined });
  (customerApi.contactHistory as jest.Mock).mockResolvedValue({ rows: [], nextCursor: undefined });
});

test('連絡先がなくても表示でき、入力して追加する', async () => {
  (customerApi.addContact as jest.Mock).mockResolvedValue({ id: 'c1' });
  render(<CustomerContactsSection customerId="customer1" />);
  expect(await screen.findByText('連絡先はありません')).toBeInTheDocument();
  fireEvent.click(screen.getByRole('button', { name: '連絡先を追加' }));
  fireEvent.change(screen.getByLabelText('連絡先の値'), { target: { value: '090-1234-5678' } });
  fireEvent.click(screen.getByRole('button', { name: '保存する' }));
  await waitFor(() =>
    expect(customerApi.addContact).toHaveBeenCalledWith('customer1', {
      type: 'PHONE',
      value: '090-1234-5678',
    })
  );
});

test('取得失敗を空一覧と区別し、再試行で回復する', async () => {
  (customerApi.contacts as jest.Mock).mockRejectedValueOnce(new Error('unavailable'));
  render(<CustomerContactsSection customerId="customer1" />);
  expect(await screen.findByText('連絡先を取得できませんでした')).toBeInTheDocument();
  expect(screen.queryByText('連絡先はありません')).not.toBeInTheDocument();
  fireEvent.click(screen.getByRole('button', { name: '再試行' }));
  expect(await screen.findByText('連絡先はありません')).toBeInTheDocument();
});

test('保存失敗後も入力を保持し、修正して再送できる', async () => {
  (customerApi.addContact as jest.Mock)
    .mockRejectedValueOnce(new Error('conflict'))
    .mockResolvedValue({ id: 'new' });
  render(<CustomerContactsSection customerId="customer1" />);
  await screen.findByText('連絡先はありません');
  fireEvent.click(screen.getByRole('button', { name: '連絡先を追加' }));
  fireEvent.change(screen.getByLabelText('連絡先の値'), { target: { value: 'invalid' } });
  fireEvent.click(screen.getByRole('button', { name: '保存する' }));
  await waitFor(() => expect(customerApi.addContact).toHaveBeenCalled());
  expect(screen.getByLabelText('連絡先の値')).toHaveValue('invalid');
  fireEvent.change(screen.getByLabelText('連絡先の値'), { target: { value: '09012345678' } });
  fireEvent.click(screen.getByRole('button', { name: '保存する' }));
  await waitFor(() =>
    expect(customerApi.addContact).toHaveBeenLastCalledWith('customer1', {
      type: 'PHONE',
      value: '09012345678',
    })
  );
});

test('優先指定を解除でき、削除は確認を経て実行する', async () => {
  (customerApi.contacts as jest.Mock).mockResolvedValue({
    rows: [{ id: 'phone1', type: 'PHONE', value: '+819012345678', preferred: true }],
    nextCursor: null,
  });
  (customerApi.setContactPreference as jest.Mock).mockResolvedValue(undefined);
  (customerApi.deleteContact as jest.Mock).mockResolvedValue(undefined);
  render(<CustomerContactsSection customerId="customer1" />);
  fireEvent.click(await screen.findByRole('button', { name: '優先を解除' }));
  await waitFor(() =>
    expect(customerApi.setContactPreference).toHaveBeenCalledWith('customer1', 'PHONE', null)
  );
  fireEvent.click(screen.getByRole('button', { name: '削除' }));
  expect(customerApi.deleteContact).not.toHaveBeenCalled();
  fireEvent.click(await screen.findByRole('button', { name: '削除する' }));
  await waitFor(() =>
    expect(customerApi.deleteContact).toHaveBeenCalledWith('customer1', 'phone1')
  );
});

test('編集中の連絡先が消えたら保存を外し、閉じて一覧を再取得する', async () => {
  (customerApi.contacts as jest.Mock).mockResolvedValue({
    rows: [{ id: 'gone', type: 'PHONE', value: '+819012345678', preferred: false }],
    nextCursor: null,
  });
  (customerApi.updateContact as jest.Mock).mockRejectedValue({ response: { status: 404 } });
  render(<CustomerContactsSection customerId="customer1" />);
  fireEvent.click(await screen.findByRole('button', { name: '編集' }));
  fireEvent.click(screen.getByRole('button', { name: '保存する' }));
  expect(await screen.findByText('この連絡先は見つかりませんでした。')).toBeInTheDocument();
  expect(screen.queryByRole('button', { name: '保存する' })).not.toBeInTheDocument();
  (customerApi.contacts as jest.Mock).mockResolvedValue({ rows: [], nextCursor: null });
  fireEvent.click(screen.getByRole('button', { name: '閉じる' }));
  expect(await screen.findByText('連絡先はありません')).toBeInTheDocument();
});

test.each(['削除', '優先にする'])('%s が 404 なら消えた行と履歴を再取得する', async action => {
  (customerApi.contacts as jest.Mock)
    .mockResolvedValueOnce({
      rows: [{ id: 'gone', type: 'PHONE', value: '+819012345678', preferred: false }],
      nextCursor: null,
    })
    .mockResolvedValue({ rows: [], nextCursor: null });
  (customerApi.deleteContact as jest.Mock).mockRejectedValue({ response: { status: 404 } });
  (customerApi.setContactPreference as jest.Mock).mockRejectedValue({ response: { status: 404 } });
  render(<CustomerContactsSection customerId="customer1" />);
  fireEvent.click(await screen.findByRole('button', { name: action }));
  if (action === '削除') fireEvent.click(await screen.findByRole('button', { name: '削除する' }));
  expect(await screen.findByText('連絡先はありません')).toBeInTheDocument();
  expect(customerApi.contactHistory).toHaveBeenCalledTimes(2);
  expect(notify.warning).toHaveBeenCalled();
  expect(notify.error).not.toHaveBeenCalled();
});

test('行の許可と共同制約を区別し、根拠なしの変更を防ぐ', async () => {
  (customerApi.contacts as jest.Mock).mockResolvedValue({
    rows: [
      {
        id: 'phone1',
        type: 'PHONE',
        value: '+819012345678',
        preferred: false,
        business_status: 'ALLOWED',
        marketing_status: 'UNKNOWN',
        effective_business_status: 'DENIED',
        effective_marketing_status: 'UNKNOWN',
      },
    ],
    nextCursor: null,
  });
  render(<CustomerContactsSection customerId="customer1" />);
  expect(await screen.findByText('業務連絡: 許可／共同制約: 拒否（連絡不可）')).toBeInTheDocument();
  fireEvent.click(screen.getByRole('button', { name: '業務連絡の可否を変更' }));
  fireEvent.click(screen.getByRole('button', { name: '可否を保存' }));
  expect(await screen.findByText('出所を入力してください')).toBeInTheDocument();
  expect(await screen.findByText('根拠を入力してください')).toBeInTheDocument();
});

test('可否保存の失敗後に根拠を保持して再送し、全行を再取得する', async () => {
  const contact = {
    id: 'phone1',
    type: 'PHONE',
    value: '+819012345678',
    preferred: false,
    business_status: 'UNKNOWN',
    marketing_status: 'UNKNOWN',
    effective_business_status: 'UNKNOWN',
    effective_marketing_status: 'UNKNOWN',
  };
  (customerApi.contacts as jest.Mock).mockResolvedValue({ rows: [contact] });
  (customerApi.setContactPermission as jest.Mock)
    .mockRejectedValueOnce(new Error('failed'))
    .mockImplementationOnce(async () => {
      (customerApi.contacts as jest.Mock).mockResolvedValue({
        rows: [{ ...contact, business_status: 'ALLOWED', effective_business_status: 'ALLOWED' }],
      });
    });
  render(<CustomerContactsSection customerId="customer1" />);
  fireEvent.click(await screen.findByRole('button', { name: '業務連絡の可否を変更' }));
  fireEvent.click(screen.getByRole('combobox'));
  const allowed = screen.getByRole('option', { name: '許可' });
  fireEvent.pointerDown(allowed);
  fireEvent.click(allowed);
  fireEvent.change(screen.getByLabelText('出所'), { target: { value: '電話' } });
  fireEvent.change(screen.getByLabelText('根拠'), { target: { value: '本人からの回答' } });
  fireEvent.click(screen.getByRole('button', { name: '可否を保存' }));
  await waitFor(() => expect(notify.error).toHaveBeenCalled());
  expect(screen.getByLabelText('根拠')).toHaveValue('本人からの回答');
  fireEvent.click(screen.getByRole('button', { name: '可否を保存' }));
  expect(await screen.findByText('業務連絡: 許可／共同制約: 許可（連絡可）')).toBeInTheDocument();
  expect(customerApi.setContactPermission).toHaveBeenLastCalledWith(
    'customer1',
    'phone1',
    'BUSINESS',
    {
      status: 'ALLOWED',
      source: '電話',
      reason: '本人からの回答',
    }
  );
});

test('継承履歴は新たな同意と区別し除去元と引継先を表示する', async () => {
  const state = {
    customer_id: 'customer1',
    type: 'PHONE',
    value: '+819012345678',
    preferred: false,
    deleted: false,
    business_status: 'ALLOWED',
    marketing_status: 'ALLOWED',
  };
  (customerApi.contactHistory as jest.Mock).mockResolvedValue({
    rows: [
      {
        id: 'h1',
        contact_id: 'target',
        origin_customer_id: 'customer1',
        action: 'RESTRICTION_INHERITANCE',
        actor_id: 12,
        occurred_at: '2026-09-20T12:00:00+09:00',
        operation_id: 'operation1',
        source_contact_id: 'removed',
        before: state,
        after: { ...state, business_status: 'DENIED', marketing_status: 'UNKNOWN' },
      },
    ],
  });
  render(<CustomerContactsSection customerId="customer1" />);
  expect(await screen.findByText(/制約の継承（新たな同意ではありません）/)).toBeInTheDocument();
  expect(screen.getByText('除去元: removed／引継先: target')).toBeInTheDocument();
  expect(screen.getByText(/変更後: .*業務: 拒否／販促: 未確認/)).toBeInTheDocument();
});

test('可否の保存時に対象が消えたら入力を閉じ一覧を更新できる', async () => {
  (customerApi.contacts as jest.Mock).mockResolvedValue({
    rows: [
      {
        id: 'gone',
        type: 'PHONE',
        value: '+819012345678',
        business_status: 'UNKNOWN',
        marketing_status: 'UNKNOWN',
        effective_business_status: 'UNKNOWN',
        effective_marketing_status: 'UNKNOWN',
      },
    ],
  });
  (customerApi.setContactPermission as jest.Mock).mockRejectedValue({ response: { status: 404 } });
  render(<CustomerContactsSection customerId="customer1" />);
  fireEvent.click(await screen.findByRole('button', { name: '販促連絡の可否を変更' }));
  fireEvent.change(screen.getByLabelText('出所'), { target: { value: '電話' } });
  fireEvent.change(screen.getByLabelText('根拠'), { target: { value: '本人の回答' } });
  fireEvent.click(screen.getByRole('button', { name: '可否を保存' }));
  expect(await screen.findByText('この連絡先は見つかりませんでした。')).toBeInTheDocument();
  expect(screen.queryByRole('button', { name: '可否を保存' })).not.toBeInTheDocument();
  (customerApi.contacts as jest.Mock).mockResolvedValue({ rows: [] });
  fireEvent.click(screen.getByRole('button', { name: '閉じる' }));
  expect(await screen.findByText('連絡先はありません')).toBeInTheDocument();
});
