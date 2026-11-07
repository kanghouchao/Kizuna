import { useState } from 'react';
import { fireEvent, render, screen } from '@testing-library/react';
import { Segmented } from '../segmented';

const OPTIONS = [
  { value: 'all', label: 'すべて' },
  { value: 'pending', label: '承諾待ちだけ' },
] as const;

function Harness({ onChange }: { onChange?: (value: string) => void }) {
  const [value, setValue] = useState<'all' | 'pending'>('all');
  return (
    <Segmented
      label="絞り込み"
      options={OPTIONS}
      value={value}
      onValueChange={next => {
        setValue(next);
        onChange?.(next);
      }}
    />
  );
}

describe('Segmented', () => {
  it('押した区分だけが押下状態になること', () => {
    render(<Harness />);
    const group = screen.getByRole('group', { name: '絞り込み' });
    expect(group).toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: '承諾待ちだけ' }));

    expect(screen.getByRole('button', { name: '承諾待ちだけ' })).toHaveAttribute(
      'aria-pressed',
      'true'
    );
    expect(screen.getByRole('button', { name: 'すべて' })).toHaveAttribute('aria-pressed', 'false');
  });

  it('選択中の区分を押しても選択が外れないこと', () => {
    const onChange = jest.fn();
    render(<Harness onChange={onChange} />);

    fireEvent.click(screen.getByRole('button', { name: 'すべて' }));

    expect(screen.getByRole('button', { name: 'すべて' })).toHaveAttribute('aria-pressed', 'true');
    expect(onChange).not.toHaveBeenCalled();
  });
});
