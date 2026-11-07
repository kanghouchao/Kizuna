import { useState } from 'react';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { BottomSheet, BottomSheetClose } from '../bottom-sheet';
import { Button } from '../button';

function Harness() {
  const [open, setOpen] = useState(false);
  return (
    <>
      <Button onClick={() => setOpen(true)}>受諾する</Button>
      <BottomSheet
        open={open}
        onOpenChange={setOpen}
        title="サービス条件を受諾"
        description="内容を確かめてから確定します。"
        footer={
          <>
            <Button>確定する</Button>
            <BottomSheetClose render={<Button variant="outline" />}>やめる</BottomSheetClose>
          </>
        }
      >
        <p>銀座本店・90分コース</p>
      </BottomSheet>
    </>
  );
}

describe('BottomSheet', () => {
  it('開くと題名を名乗るダイアログに本文と操作が出ること', async () => {
    render(<Harness />);

    fireEvent.click(screen.getByRole('button', { name: '受諾する' }));

    const sheet = await screen.findByRole('dialog', { name: 'サービス条件を受諾' });
    expect(sheet).toHaveAccessibleDescription('内容を確かめてから確定します。');
    expect(screen.getByText('銀座本店・90分コース')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '確定する' })).toBeInTheDocument();
  });

  it('閉じる操作で閉じること', async () => {
    render(<Harness />);
    fireEvent.click(screen.getByRole('button', { name: '受諾する' }));
    await screen.findByRole('dialog');

    fireEvent.click(screen.getByRole('button', { name: 'やめる' }));

    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
  });
});
