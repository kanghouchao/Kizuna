import { render, screen, fireEvent, waitFor, within } from '@testing-library/react';
import { mediumApi } from '@/entities/medium';
import { CostEditor } from './CostEditor';
jest.mock('@/entities/medium', () => ({ mediumApi: { options: jest.fn() } }));
const options = jest.mocked(mediumApi.options);
beforeEach(() => {
  jest.clearAllMocks();
  options.mockResolvedValue([
    { id: 'medium-a', name: '求人媒体A' },
    { id: 'medium-b', name: '求人媒体B' },
  ]);
});
async function pickMedium(name: string) {
  const trigger = screen.getByRole('combobox', { name: '媒体' });
  await waitFor(() => expect(trigger).toBeEnabled());
  fireEvent.click(trigger);
  const option = await screen.findByRole('option', { name });
  // Base UI の Item は pointerdown を経ていない click を無視する
  fireEvent.pointerDown(option);
  fireEvent.click(option);
}
const values = {
  id: '1',
  store_id: 1,
  month: '2026-10',
  category: 'SALES' as const,
  medium: { id: 'medium-a', name: '求人媒体A' },
  agency_name: null,
  plan_name: null,
  inquiry_count: 0,
  amount: 100,
  version: 2,
  updated_at: '2026-10-01T00:00:00Z',
};
it('必須金額の空欄をゼロにせず、問い合わせの未計測とゼロを区別する', async () => {
  const save = jest.fn().mockResolvedValue(undefined);
  render(<CostEditor disabled={false} onSubmit={save} />);
  await pickMedium('求人媒体A');
  fireEvent.click(screen.getByRole('button', { name: '保存する' }));
  expect(await screen.findByText('金額を入力してください')).toBeVisible();
  expect(save).not.toHaveBeenCalled();
  fireEvent.change(screen.getByLabelText('金額（円）'), { target: { value: '0' } });
  fireEvent.click(screen.getByRole('button', { name: '保存する' }));
  await waitFor(() =>
    expect(save).toHaveBeenCalledWith(
      expect.objectContaining({
        values: expect.objectContaining({ amount: 0, inquiry_count: null }),
      })
    )
  );
  fireEvent.change(screen.getByLabelText('問い合わせ人数（未計測は空欄）'), {
    target: { value: '0' },
  });
  fireEvent.click(screen.getByRole('button', { name: '保存する' }));
  await waitFor(() =>
    expect(save).toHaveBeenLastCalledWith(
      expect.objectContaining({ values: expect.objectContaining({ inquiry_count: 0 }) })
    )
  );
});
it('変更と削除には理由が必要で版を維持する', async () => {
  const save = jest.fn().mockResolvedValue(undefined);
  render(<CostEditor cost={values} remove disabled={false} onSubmit={save} />);
  fireEvent.click(screen.getByRole('button', { name: '削除内容を確認' }));
  expect(await screen.findByText('理由を入力してください')).toBeVisible();
  fireEvent.change(screen.getByLabelText('理由'), { target: { value: ' 誤入力 ' } });
  fireEvent.click(screen.getByRole('button', { name: '削除内容を確認' }));
  fireEvent.click(await screen.findByRole('button', { name: '削除する' }));
  await waitFor(() =>
    expect(save).toHaveBeenCalledWith({ kind: 'delete', id: '1', version: 2, reason: '誤入力' })
  );
});
it('選んだ媒体を媒体 ID で送り、未選択なら送らない', async () => {
  const save = jest.fn().mockResolvedValue(undefined);
  render(<CostEditor disabled={false} onSubmit={save} />);
  const submit = screen.getByRole('button', { name: '保存する' });
  expect(submit).toBeDisabled();
  await waitFor(() => expect(submit).toBeEnabled());
  fireEvent.change(screen.getByLabelText('金額（円）'), { target: { value: '100' } });
  fireEvent.click(submit);
  expect(await screen.findByText('媒体を選択してください')).toBeVisible();
  expect(save).not.toHaveBeenCalled();
  await pickMedium('求人媒体B');
  fireEvent.click(submit);
  await waitFor(() =>
    expect(save).toHaveBeenCalledWith({
      kind: 'create',
      values: expect.objectContaining({ medium_id: 'medium-b', amount: 100 }),
    })
  );
});
it('停止中の媒体を持つ行は停止中と示し、同じ媒体 ID のまま保存する', async () => {
  const save = jest.fn().mockResolvedValue(undefined);
  render(
    <CostEditor
      cost={{ ...values, medium: { id: 'medium-stopped', name: '終了した媒体' } }}
      disabled={false}
      onSubmit={save}
    />
  );
  const trigger = screen.getByRole('combobox', { name: '媒体' });
  expect(await within(trigger).findByText('終了した媒体（停止中）')).toBeVisible();
  fireEvent.change(screen.getByLabelText('理由'), { target: { value: '金額訂正' } });
  fireEvent.click(screen.getByRole('button', { name: '保存する' }));
  await waitFor(() =>
    expect(save).toHaveBeenCalledWith(
      expect.objectContaining({
        kind: 'replace',
        values: expect.objectContaining({ medium_id: 'medium-stopped' }),
      })
    )
  );
});
it('媒体の選択肢を取得できなければ再試行でき、媒体が無ければ媒体管理へ案内する', async () => {
  options.mockReset();
  options.mockRejectedValueOnce(new Error('切断')).mockResolvedValueOnce([]);
  render(<CostEditor disabled={false} onSubmit={jest.fn()} />);
  const alert = await screen.findByRole('alert');
  expect(alert).toHaveTextContent('媒体の選択肢を取得できませんでした。');
  fireEvent.click(within(alert).getByRole('button'));
  expect(await screen.findByText(/選択できる媒体がありません/)).toBeVisible();
  expect(screen.getByRole('link', { name: '媒体を管理' })).toHaveAttribute(
    'href',
    '/store/advertising-costs/media'
  );
});
