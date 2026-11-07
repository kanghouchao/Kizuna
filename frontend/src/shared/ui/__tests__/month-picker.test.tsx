import { useState } from 'react';
import { fireEvent, render, screen } from '@testing-library/react';
import { MonthPicker } from '../month-picker';

function Harness({ initial }: { initial: string }) {
  const [value, setValue] = useState(initial);
  return (
    <>
      <MonthPicker value={value} currentMonth="2026-11" onValueChange={setValue} />
      <output>{value}</output>
    </>
  );
}

describe('MonthPicker', () => {
  it('‹ › で前後の月へ動き、今月より先へは進めないこと', () => {
    render(<Harness initial="2026-11" />);

    expect(screen.getByRole('button', { name: '次の月' })).toBeDisabled();
    fireEvent.click(screen.getByRole('button', { name: '前の月' }));

    expect(screen.getByRole('status')).toHaveTextContent('2026-10');
    expect(screen.getByRole('combobox')).toHaveTextContent('2026年10月');
  });

  it('年をまたいで戻れ、直近 12 か月の最古より前へは戻れないこと', () => {
    render(<Harness initial="2026-01" />);

    fireEvent.click(screen.getByRole('button', { name: '前の月' }));

    expect(screen.getByRole('status')).toHaveTextContent('2025-12');
    expect(screen.getByRole('button', { name: '前の月' })).toBeDisabled();
  });

  it('中央を押すと直近 12 か月の一覧から選べ、手入力の欄は無いこと', async () => {
    render(<Harness initial="2026-11" />);

    fireEvent.click(screen.getByRole('combobox'));
    const options = await screen.findAllByRole('option');
    expect(options.map(option => option.textContent)).toEqual([
      '2026年11月',
      '2026年10月',
      '2026年9月',
      '2026年8月',
      '2026年7月',
      '2026年6月',
      '2026年5月',
      '2026年4月',
      '2026年3月',
      '2026年2月',
      '2026年1月',
      '2025年12月',
    ]);
    const target = screen.getByRole('option', { name: '2026年3月' });
    fireEvent.pointerDown(target);
    fireEvent.click(target);

    expect(screen.getByRole('status')).toHaveTextContent('2026-03');
    expect(screen.queryByRole('textbox')).not.toBeInTheDocument();
  });

  it('範囲外の月が渡されても、‹ › は範囲外の月へ動かさないこと', () => {
    render(<Harness initial="2025-06" />);

    expect(screen.getByRole('button', { name: '前の月' })).toBeDisabled();
    expect(screen.getByRole('button', { name: '次の月' })).toBeDisabled();
  });
});
