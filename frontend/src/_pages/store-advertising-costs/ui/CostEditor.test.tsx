import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { CostEditor } from './CostEditor';
const values = {
  id: '1',
  store_id: 1,
  month: '2026-10',
  category: 'SALES' as const,
  media_name: '媒体',
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
  fireEvent.change(screen.getByLabelText('媒体'), { target: { value: '媒体' } });
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
