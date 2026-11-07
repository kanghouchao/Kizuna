import { render, screen } from '@testing-library/react';
import { EmptyState } from '../empty-state';
import { ListRow, ListRows, ListRowsSkeleton } from '../list-row';
import { Money } from '../money';
import { Button } from '../button';

describe('ListRow', () => {
  it('遷移先のある行は行全体が一つのリンクになり、題・補足・右端の値を名乗ること', () => {
    render(
      <ListRows label="報酬明細">
        <ListRow
          href="/cast/remunerations/1"
          title="90分コース"
          meta="11/5（木）· 銀座本店"
          trailing="¥12,000"
        />
        <ListRow title="延長 30分" trailing="¥3,000" />
      </ListRows>
    );

    expect(screen.getByRole('list', { name: '報酬明細' })).toBeInTheDocument();
    expect(screen.getAllByRole('listitem')).toHaveLength(2);
    const link = screen.getByRole('link');
    expect(link).toHaveAttribute('href', '/cast/remunerations/1');
    expect(link).toHaveTextContent('90分コース11/5（木）· 銀座本店¥12,000');
  });

  it('読み込み中は行の形の骨組みを、読み込み中と名乗る領域で出すこと', () => {
    render(<ListRowsSkeleton rows={3} />);

    const region = screen.getByRole('status', { name: '読み込み中' });
    expect(region.querySelectorAll('[data-slot="skeleton"]').length).toBeGreaterThanOrEqual(3);
  });
});

describe('Money', () => {
  it('金額を共通の書式で、桁を揃えて出すこと', () => {
    render(<Money amount={-1000} />);
    expect(screen.getByText('−¥1,000')).toHaveClass('tabular-nums');
  });

  it('変更前を渡すと前後を矢印でつなぐこと', () => {
    render(<Money before={8000} amount={10000} />);
    expect(screen.getByText('¥8,000 → ¥10,000')).toBeInTheDocument();
  });
});

describe('EmptyState', () => {
  it('何が無いかと次の操作を出すこと', () => {
    render(<EmptyState message="まだ予約はありません" action={<Button>新しく予約</Button>} />);

    expect(screen.getByText('まだ予約はありません')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '新しく予約' })).toBeInTheDocument();
  });
});
