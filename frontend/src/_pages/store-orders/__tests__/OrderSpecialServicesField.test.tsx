import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { useForm } from 'react-hook-form';
import { orderApi, OrderSpecialService } from '@/entities/order';
import { Form, Button } from '@/shared/ui';
import { OrderSpecialServicesField } from '../ui/OrderSpecialServicesField';

jest.mock('@/entities/order', () => ({
  ...jest.requireActual('@/entities/order'),
  orderApi: { specialServiceCandidates: jest.fn(), specialServiceRevisions: jest.fn() },
}));
const current: OrderSpecialService = {
  service_id: 's1',
  revision_id: 'r1',
  revision_number: 1,
  terms_version: 1,
  name: '採用済み',
  charge_type: 'PAID',
  price: 2000,
  remuneration: 1500,
  adoption_basis: 'ACCEPTED_TERMS',
  enrollment_id: 'c1',
  requires_attention: true,
  current_consent_status: 'REJECTED',
};
function Screen({ save }: { save: (v: unknown) => void }) {
  const form = useForm({ defaultValues: { cast_id: 'c1', special_service_ids: ['s1'] } });
  return (
    <Form {...form}>
      <form onSubmit={form.handleSubmit(save)}>
        <OrderSpecialServicesField current={[current]} originalCast="c1" />
        <Button type="button" onClick={() => form.setValue('cast_id', 'c2')}>
          担当を変更
        </Button>
        <Button type="submit">保存</Button>
      </form>
    </Form>
  );
}
beforeEach(() => jest.clearAllMocks());
test('拒否された項目を除去して修復でき、価格の手入力を要求しない', async () => {
  (orderApi.specialServiceCandidates as jest.Mock).mockResolvedValue({ rows: [], total: 0 });
  const save = jest.fn();
  render(<Screen save={save} />);
  await screen.findByText('新しく選択できる特殊サービスがありません。');
  expect(screen.getByRole('alert')).toHaveTextContent('本人拒否・要対応');
  fireEvent.click(screen.getByRole('checkbox'));
  fireEvent.click(screen.getByRole('button', { name: '保存' }));
  await waitFor(() =>
    expect(save).toHaveBeenCalledWith(
      expect.objectContaining({ special_service_ids: [] }),
      expect.anything()
    )
  );
  expect(screen.queryByRole('spinbutton')).not.toBeInTheDocument();
});
test('担当変更は項目を空にし、保存後の選び直しを示す', async () => {
  (orderApi.specialServiceCandidates as jest.Mock).mockResolvedValue({ rows: [], total: 0 });
  const save = jest.fn();
  render(<Screen save={save} />);
  await screen.findByText('新しく選択できる特殊サービスがありません。');
  fireEvent.click(screen.getByRole('button', { name: '担当を変更' }));
  expect(screen.getByRole('status')).toHaveTextContent('保存後');
  fireEvent.click(screen.getByRole('button', { name: '保存' }));
  await waitFor(() =>
    expect(save).toHaveBeenCalledWith(
      expect.objectContaining({ cast_id: 'c2', special_service_ids: [] }),
      expect.anything()
    )
  );
});
test('取得失敗と権限不足を空の候補と区別する', async () => {
  const load = orderApi.specialServiceCandidates as jest.Mock;
  load
    .mockRejectedValueOnce(new Error('offline'))
    .mockRejectedValueOnce({ isAxiosError: true, response: { status: 403 } });
  render(<Screen save={jest.fn()} />);
  await screen.findByText('特殊サービスを取得できませんでした');
  fireEvent.click(screen.getByRole('button', { name: '再試行' }));
  await screen.findByText('特殊サービスを照会する権限がありません。担当者に依頼してください。');
  expect(screen.queryByText('新しく選択できる特殊サービスがありません。')).not.toBeInTheDocument();
});
