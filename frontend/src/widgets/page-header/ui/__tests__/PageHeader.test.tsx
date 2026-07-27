import { render, screen } from '@testing-library/react';
import { PageHeader } from '../PageHeader';

// 6 つの一覧ページは title・description・actions を常に 3 つとも渡すため、
// 任意引数を省いた場合の分岐はここでしか通らない。
describe('PageHeader', () => {
  it('見出しを h1 として描き、副題とアクションを並べること', () => {
    render(
      <PageHeader
        title="顧客管理"
        description="顧客情報の登録・編集ができます。"
        actions={<button type="button">新規顧客登録</button>}
      />
    );

    expect(screen.getByRole('heading', { level: 1, name: '顧客管理' })).toBeInTheDocument();
    expect(screen.getByText('顧客情報の登録・編集ができます。')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '新規顧客登録' })).toBeInTheDocument();
  });

  it('副題とアクションが無ければ見出しだけを描くこと', () => {
    const { container } = render(<PageHeader title="顧客管理" />);

    expect(screen.getByRole('heading', { level: 1, name: '顧客管理' })).toBeInTheDocument();
    expect(container.querySelector('p')).toBeNull();
    expect(screen.queryByRole('button')).not.toBeInTheDocument();
  });
});
