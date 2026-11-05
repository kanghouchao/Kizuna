import { render, screen } from '@testing-library/react';
import { ChangeTable } from '../change-table';

const rowOf = (label: string) => screen.getByRole('cell', { name: label }).closest('tr');

describe('ChangeTable', () => {
  it('変わった行だけを強調し、変わらない行は強調しないこと', () => {
    render(
      <ChangeTable
        rows={[
          { label: '価格', before: '1,000 円', after: '1,200 円' },
          { label: '状態', before: '有効', after: '有効' },
        ]}
      />
    );

    expect(rowOf('価格')).toHaveAttribute('data-changed', 'true');
    expect(rowOf('状態')).toHaveAttribute('data-changed', 'false');
  });

  it('全項目を項目・変更前・変更後の順で並べること', () => {
    render(<ChangeTable rows={[{ label: '請求', before: '¥28,000', after: '¥22,000' }]} />);

    expect(screen.getAllByRole('columnheader').map(h => h.textContent)).toEqual([
      '項目',
      '変更前',
      '変更後',
    ]);
    expect(Array.from(rowOf('請求')!.querySelectorAll('td')).map(cell => cell.textContent)).toEqual(
      ['請求', '¥28,000', '¥22,000']
    );
  });

  it('変更前は変わらない行だけ muted にし、bg-muted の変わった行には重ねないこと', () => {
    render(
      <ChangeTable
        rows={[
          { label: '価格', before: '1,000 円', after: '1,200 円' },
          { label: '状態', before: '有効', after: '有効' },
        ]}
      />
    );
    const beforeOf = (label: string) => rowOf(label)!.querySelectorAll('td')[1];

    expect(beforeOf('価格')).not.toHaveClass('text-muted-foreground');
    expect(beforeOf('状態')).toHaveClass('text-muted-foreground');
  });
});
