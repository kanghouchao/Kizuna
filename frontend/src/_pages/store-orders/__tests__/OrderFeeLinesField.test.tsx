import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { useForm } from 'react-hook-form';
import { Form } from '@/shared/ui';
import { orderApi, OrderFeeLineInput, toFeeLineInputs } from '@/entities/order';
import { OrderFeeLinesField } from '../ui/OrderFeeLinesField';

jest.mock('@/entities/order', () => ({
  ...jest.requireActual('@/entities/order'),
  orderApi: { surchargeCandidates: jest.fn() },
}));

jest.mock('next/navigation', () => ({ useParams: () => ({ storeId: '1' }) }));

function Harness({ submit }: { submit: jest.Mock }) {
  const form = useForm<{ fee_lines: OrderFeeLineInput[] }>({ defaultValues: { fee_lines: [] } });
  return (
    <Form {...form}>
      <form onSubmit={form.handleSubmit(values => submit(toFeeLineInputs(values.fee_lines)))}>
        <OrderFeeLinesField />
        <button type="submit">保存</button>
      </form>
    </Form>
  );
}

test('無料延長でも分数を記録し、費用を超える報酬を拒否する', async () => {
  const submit = jest.fn();
  render(<Harness submit={submit} />);
  fireEvent.click(screen.getByRole('button', { name: '延長を追加' }));
  fireEvent.change(screen.getByLabelText('明細1の分数'), { target: { value: '15' } });
  fireEvent.change(screen.getByLabelText('明細1の固定報酬'), { target: { value: '1' } });
  fireEvent.click(screen.getByRole('button', { name: '保存' }));
  expect(await screen.findByText('固定報酬は顧客費用以下で入力してください')).toBeInTheDocument();
  expect(submit).not.toHaveBeenCalled();
  fireEvent.change(screen.getByLabelText('明細1の固定報酬'), { target: { value: '0' } });
  fireEvent.click(screen.getByRole('button', { name: '保存' }));
  await waitFor(() =>
    expect(submit).toHaveBeenCalledWith([
      { kind: 'EXTENSION', name: '延長', duration_minutes: 15, amount: 0, remuneration: 0 },
    ])
  );
});

test('加算の権限不足と取得失敗を空一覧と区別し、再試行できる', async () => {
  const fetch = jest.mocked(orderApi.surchargeCandidates);
  fetch.mockRejectedValueOnce({ isAxiosError: true, response: { status: 403 } });
  render(<Harness submit={jest.fn()} />);
  fireEvent.click(screen.getByText('加算を選択'));
  expect(await screen.findByRole('alert')).toHaveTextContent('加算を照会する権限がありません');
  expect(screen.queryByText('選択できる加算がありません。')).not.toBeInTheDocument();
  fireEvent.click(screen.getByText('選択を閉じる'));
  fetch
    .mockRejectedValueOnce(new Error('network'))
    .mockResolvedValueOnce({ rows: [], total: 0, page: 0, pageCount: 0 });
  fireEvent.click(screen.getByText('加算を選択'));
  expect(await screen.findByRole('alert')).toHaveTextContent('加算を取得できませんでした');
  fireEvent.click(screen.getByText('再試行'));
  expect(await screen.findByText('選択できる加算がありません。')).toBeInTheDocument();
});
