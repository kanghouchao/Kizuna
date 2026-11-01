import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { useForm } from 'react-hook-form';
import { orderApi } from '@/entities/order';
import { Form } from '@/shared/ui';
import { OrderCourseField } from '../ui/OrderCourseField';
import { course } from '../lib/orderTestSupport';
jest.mock('@/entities/user', () => ({
  ...jest.requireActual('@/entities/user'),
  useStoreContext: () => ({
    stores: [{ id: 1, name: 'テスト店舗' }],
    currentStoreId: '1',
    currentStore: { id: 1, name: 'テスト店舗' },
    loadFailed: false,
    reload: jest.fn(),
    switchStore: jest.fn(),
  }),
}));

jest.mock('@/entities/order', () => ({
  ...jest.requireActual('@/entities/order'),
  orderApi: { courseCandidates: jest.fn(), courseRevisions: jest.fn() },
}));
function Screen({ onSubmit = jest.fn() }: { onSubmit?: () => void }) {
  const form = useForm({ defaultValues: { course_id: '' } });
  return (
    <Form {...form}>
      <form onSubmit={form.handleSubmit(onSubmit)}>
        <OrderCourseField required />
        <button type="submit">保存</button>
      </form>
    </Form>
  );
}
beforeEach(() => jest.clearAllMocks());
test('必須コースが未選択なら保存せず選択欄へフォーカスする', async () => {
  (orderApi.courseCandidates as jest.Mock).mockResolvedValue({ rows: [course], total: 1 });
  const onSubmit = jest.fn();
  render(<Screen onSubmit={onSubmit} />);
  const submit = screen.getByRole('button', { name: '保存' });
  submit.focus();
  fireEvent.click(submit);
  await screen.findByText('コースを選択してください');
  await waitFor(() => expect(screen.getByRole('combobox', { name: 'コース' })).toHaveFocus());
  expect(onSubmit).not.toHaveBeenCalled();
});
test('取得失敗は再試行でき、権限不足は操作理由を表示する', async () => {
  const load = orderApi.courseCandidates as jest.Mock;
  load
    .mockRejectedValueOnce(new Error('network'))
    .mockResolvedValueOnce({ rows: [course], total: 1 });
  render(<Screen />);
  fireEvent.click(screen.getByRole('combobox', { name: 'コース' }));
  await screen.findByText('コースを取得できませんでした');
  fireEvent.click(screen.getByRole('button', { name: '再試行' }));
  await screen.findByRole('combobox', { name: 'コース' });
  load.mockRejectedValue({ isAxiosError: true, response: { status: 403 } });
  fireEvent.change(screen.getByLabelText('コースを検索'), { target: { value: '別' } });
  await screen.findByText('コースを照会する権限がありません。担当者に依頼してください。');
  expect(screen.queryByRole('option')).not.toBeInTheDocument();
});
test('次のページでも採用候補の名称を保ち、空の候補を説明する', async () => {
  const load = orderApi.courseCandidates as jest.Mock;
  load
    .mockResolvedValueOnce({ rows: [course], page: 0, pageCount: 2, total: 21 })
    .mockResolvedValueOnce({ rows: [], page: 1, pageCount: 2, total: 21 });
  render(<Screen />);
  fireEvent.click(await screen.findByRole('combobox', { name: 'コース' }));
  const option = screen.getByRole('option', { name: /基本/ });
  fireEvent.pointerDown(option);
  fireEvent.click(option);
  fireEvent.click(screen.getByRole('combobox', { name: 'コース' }));
  fireEvent.click(screen.getByRole('button', { name: '次へ' }));
  await screen.findByText('選択できるコースがありません。');
  await waitFor(() =>
    expect(screen.getByRole('combobox', { name: 'コース' })).toHaveTextContent('基本')
  );
  expect(load).toHaveBeenLastCalledWith('', 1);
});
test('訂正の歴史版本も検索語を保ったままページ番号で辿る', async () => {
  const load = orderApi.courseRevisions as jest.Mock;
  load
    .mockResolvedValueOnce({ rows: [course], page: 0, pageCount: 2, total: 21 })
    .mockResolvedValueOnce({ rows: [], page: 1, pageCount: 2, total: 21 });
  function Historical() {
    const form = useForm({ defaultValues: { course_revision_id: '' } });
    return (
      <Form {...form}>
        <OrderCourseField historicalOrderId="o1" />
      </Form>
    );
  }
  render(<Historical />);
  fireEvent.click(await screen.findByRole('combobox', { name: '訂正する過去の版' }));
  fireEvent.click(await screen.findByRole('button', { name: '次へ' }));
  await screen.findByText('選択できるコースがありません。');
  expect(load).toHaveBeenLastCalledWith('o1', '', 1);
  expect(orderApi.courseCandidates).not.toHaveBeenCalled();
});
