import { render, screen } from '@testing-library/react';
import { StatusBadge } from '../status-badge';

describe('StatusBadge', () => {
  it.each([
    ['申請中', 'warning'],
    ['受諾待ち', 'warning'],
    ['承諾待ち', 'warning'],
    ['再承諾が必要', 'warning'],
    ['承認', 'success'],
    ['受諾済み', 'success'],
    ['承諾済み', 'success'],
    ['却下', 'destructive'],
  ] as const)('「%s」は %s の状態ラベルで描くこと', (status, token) => {
    render(<StatusBadge status={status} />);
    expect(screen.getByText(status)).toHaveClass(`bg-${token}/10`, `text-${token}-strong`);
  });

  it.each(['取り下げ', '拒否済み'] as const)(
    '本人が自分で閉じた「%s」は中立の面で描き、muted の文字を重ねないこと',
    status => {
      render(<StatusBadge status={status} />);
      const badge = screen.getByText(status);
      expect(badge).toHaveClass('bg-muted', 'text-foreground');
      expect(badge).not.toHaveClass('text-muted-foreground');
    }
  );

  it('期限で閉じた「失効」も中立の面で描くこと', () => {
    render(<StatusBadge status="失効" />);
    expect(screen.getByText('失効')).toHaveClass('bg-muted', 'text-foreground');
  });
});
