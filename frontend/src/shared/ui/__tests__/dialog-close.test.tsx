import { act, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { useDeleteAction } from '../../lib/hooks/useDeleteAction';
import { ConfirmDialog } from '../confirm-dialog';

jest.mock('@/shared/notify', () => ({
  notify: { success: jest.fn(), error: jest.fn() },
}));

it('削除と親の再取得は退出を待たずに行い、確認文は退出中も保持する', async () => {
  const remove = jest.fn(async () => {});
  const refresh = jest.fn();
  function Page() {
    const deletion = useDeleteAction({
      remove,
      onDeleted: refresh,
      successMessage: '削除しました',
      errorMessage: '削除できませんでした',
    });
    return (
      <>
        <button onClick={() => deletion.ask('対象 A')}>確認を開く</button>
        <ConfirmDialog
          open={deletion.open}
          title={`${deletion.target ?? ''}を削除`}
          onConfirm={() => void deletion.confirm()}
          onClose={deletion.cancel}
        />
      </>
    );
  }
  render(<Page />);
  fireEvent.click(screen.getByText('確認を開く'));
  const popup = screen.getByRole('alertdialog');
  let finish!: () => void;
  const finished = new Promise<void>(resolve => {
    finish = resolve;
  });
  const getAnimations = jest.fn(() => [{ finished }]);
  Object.defineProperty(popup, 'getAnimations', { value: getAnimations });
  fireEvent.click(screen.getByRole('button', { name: '削除する' }));
  expect(remove).toHaveBeenCalledWith('対象 A');
  await waitFor(() => expect(refresh).toHaveBeenCalledTimes(1));
  await waitFor(() => expect(getAnimations).toHaveBeenCalled());
  expect(popup).toHaveAttribute('data-closed');
  expect(popup).toHaveTextContent('対象 Aを削除');
  expect(popup).toBeInTheDocument();
  await act(async () => finish());
  await waitFor(() => expect(popup).not.toBeInTheDocument());
  expect(remove).toHaveBeenCalledTimes(1);
});
