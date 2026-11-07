import { useState } from 'react';
import { fireEvent, render, screen } from '@testing-library/react';
import { StoreChips } from '../store-chips';

const A = { id: 'a', name: '銀座本店' };
const B = { id: 'b', name: '新宿店' };

function Harness({
  stores,
  initial,
}: {
  stores: { id: string; name: string }[];
  initial: string | null;
}) {
  const [value, setValue] = useState<string | null>(initial);
  return (
    <>
      <StoreChips stores={stores} value={value} onValueChange={setValue} />
      <output>{value ?? '未選択'}</output>
    </>
  );
}

describe('StoreChips', () => {
  it('複数店舗なら店名のチップを並べ、押した店舗を選ぶこと', () => {
    render(<Harness stores={[A, B]} initial="a" />);

    fireEvent.click(screen.getByRole('button', { name: '新宿店' }));

    expect(screen.getByRole('group', { name: '店舗' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '新宿店' })).toHaveAttribute('aria-pressed', 'true');
    expect(screen.getByRole('status')).toHaveTextContent('b');
  });

  it('選択中の店舗を押しても選択が外れないこと', () => {
    render(<Harness stores={[A, B]} initial="a" />);

    fireEvent.click(screen.getByRole('button', { name: '銀座本店' }));

    expect(screen.getByRole('status')).toHaveTextContent('a');
  });

  it('1 店舗ならチップを出さず、その店舗を自動で選ぶこと', () => {
    render(<Harness stores={[A]} initial={null} />);

    expect(screen.queryByRole('group')).not.toBeInTheDocument();
    expect(screen.getByRole('status')).toHaveTextContent('a');
  });
});
