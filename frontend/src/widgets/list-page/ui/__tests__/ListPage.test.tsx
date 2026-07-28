import { fireEvent, render, screen, within } from '@testing-library/react';
import { ListPage, ListPageState } from '../ListPage';

const baseState = (override: Partial<ListPageState> = {}): ListPageState => ({
  rows: ['a', 'b'],
  page: 0,
  pageCount: 1,
  total: 2,
  isLoading: false,
  onPageChange: jest.fn(),
  ...override,
});

describe('ListPage', () => {
  // DESIGN.md が外殻の面として定める class 文字列。6 ページ側の複製を退役させた分、
  // この 1 本が唯一のアンカーになる
  it('外枠は space-y-6、テーブルカードは py-0 overflow-hidden であること', () => {
    const { container } = render(
      <ListPage title="顧客管理" state={baseState()} emptyMessage="empty">
        <div>table-content</div>
      </ListPage>
    );

    expect(container.firstElementChild).toHaveClass('space-y-6');
    expect(screen.getByText('table-content').parentElement).toHaveClass('py-0', 'overflow-hidden');
  });

  it('見出しを h1 として描き、副題とアクションを並べること', () => {
    render(
      <ListPage
        title="顧客管理"
        description="顧客情報の登録・編集ができます。"
        actions={<button type="button">新規顧客登録</button>}
        state={baseState()}
        emptyMessage="顧客が登録されていません"
      >
        <div>table</div>
      </ListPage>
    );

    expect(screen.getByRole('heading', { level: 1, name: '顧客管理' })).toBeInTheDocument();
    expect(screen.getByText('顧客情報の登録・編集ができます。')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '新規顧客登録' })).toBeInTheDocument();
  });

  it('isLoading 中は children を描かず読み込み中を表示すること', () => {
    render(
      <ListPage
        title="顧客管理"
        state={baseState({ isLoading: true, rows: [] })}
        emptyMessage="empty"
      >
        <div>table-content</div>
      </ListPage>
    );

    expect(screen.getByText('読み込み中...')).toBeInTheDocument();
    expect(screen.queryByText('table-content')).not.toBeInTheDocument();
  });

  it('rows が空なら emptyMessage を表示すること', () => {
    render(
      <ListPage title="顧客管理" state={baseState({ rows: [], total: 0 })} emptyMessage="empty">
        <div>table-content</div>
      </ListPage>
    );

    expect(screen.getByText('empty')).toBeInTheDocument();
    expect(screen.queryByText('table-content')).not.toBeInTheDocument();
  });

  it('rows があれば children をそのまま描くこと', () => {
    render(
      <ListPage title="顧客管理" state={baseState()} emptyMessage="empty">
        <div>table-content</div>
      </ListPage>
    );

    expect(screen.getByText('table-content')).toBeInTheDocument();
    expect(screen.queryByText('empty')).not.toBeInTheDocument();
  });

  it('pageCount が 1 以下ならページネーションを描かないこと', () => {
    render(
      <ListPage title="顧客管理" state={baseState({ pageCount: 1 })} emptyMessage="empty">
        <div>table-content</div>
      </ListPage>
    );

    expect(screen.queryByRole('navigation')).not.toBeInTheDocument();
  });

  it('ページング情報を持たない一覧（配列形 API）はページネーションを描かないこと', () => {
    render(
      <ListPage
        title="スタッフ管理"
        state={{ rows: ['a', 'b'], isLoading: false }}
        emptyMessage="empty"
      >
        <div>table-content</div>
      </ListPage>
    );

    expect(screen.getByText('table-content')).toBeInTheDocument();
    expect(screen.queryByRole('navigation')).not.toBeInTheDocument();
  });

  it('0 起点の page をボタン表示では 1 起点に換算すること', () => {
    render(
      <ListPage
        title="顧客管理"
        state={baseState({ page: 0, pageCount: 3, total: 21, rows: Array(10).fill('x') })}
        emptyMessage="empty"
      >
        <div>table-content</div>
      </ListPage>
    );

    expect(screen.getByRole('button', { name: '1' })).toHaveClass('border-primary');
    expect(screen.getByRole('button', { name: '1' })).toHaveAttribute('aria-current', 'page');
    expect(screen.getByRole('button', { name: '2' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '2' })).not.toHaveAttribute('aria-current');
    expect(screen.getByRole('button', { name: '3' })).toBeInTheDocument();
  });

  it('件数範囲は非最終ページで rows.length を基準に算出すること', () => {
    render(
      <ListPage
        title="顧客管理"
        state={baseState({ page: 1, pageCount: 3, total: 25, rows: Array(10).fill('x') })}
        emptyMessage="empty"
      >
        <div>table-content</div>
      </ListPage>
    );

    expect(screen.getByText('25 件中 11-20 を表示')).toBeInTheDocument();
  });

  it('件数範囲は最終ページで total から逆算すること', () => {
    render(
      <ListPage
        title="顧客管理"
        state={baseState({ page: 2, pageCount: 3, total: 25, rows: Array(5).fill('x') })}
        emptyMessage="empty"
      >
        <div>table-content</div>
      </ListPage>
    );

    expect(screen.getByText('25 件中 21-25 を表示')).toBeInTheDocument();
  });

  it('ページボタン押下で onPageChange が 0 起点の対象ページで呼ばれること', () => {
    const onPageChange = jest.fn();
    render(
      <ListPage
        title="顧客管理"
        state={baseState({
          page: 0,
          pageCount: 3,
          total: 21,
          rows: Array(10).fill('x'),
          onPageChange,
        })}
        emptyMessage="empty"
      >
        <div>table-content</div>
      </ListPage>
    );

    fireEvent.click(screen.getByRole('button', { name: '2' }));
    expect(onPageChange).toHaveBeenCalledWith(1);
  });

  it('form の submit で onSearch が発火し、ページ遷移は起きないこと', () => {
    const onSearch = jest.fn();
    render(
      <ListPage
        title="顧客管理"
        search={{ content: <input aria-label="検索" />, onSearch }}
        state={baseState()}
        emptyMessage="empty"
      >
        <div>table-content</div>
      </ListPage>
    );

    const form = screen.getByLabelText('検索').closest('form');
    expect(form).not.toBeNull();
    fireEvent.submit(form!);

    expect(onSearch).toHaveBeenCalledTimes(1);
  });

  it('search を渡さなければフォームを描かないこと', () => {
    const { container } = render(
      <ListPage title="顧客管理" state={baseState()} emptyMessage="empty">
        <div>table-content</div>
      </ListPage>
    );

    expect(container.querySelector('form')).toBeNull();
  });

  it('削除等で現在ページの rows が空になっても pageCount > 1 ならページネーションを描き続けること', () => {
    render(
      <ListPage
        title="顧客管理"
        state={baseState({ page: 2, pageCount: 3, total: 25, rows: [] })}
        emptyMessage="empty"
      >
        <div>table-content</div>
      </ListPage>
    );

    expect(screen.getByText('empty')).toBeInTheDocument();
    expect(screen.getByRole('navigation')).toBeInTheDocument();
    expect(screen.getByText('25 件')).toBeInTheDocument();
  });

  it('削除で pageCount が 1 に潰れても、範囲外の page なら前へ戻る手段を残すこと', () => {
    const onPageChange = jest.fn();
    render(
      <ListPage
        title="顧客管理"
        state={baseState({ page: 1, pageCount: 1, total: 10, rows: [], onPageChange })}
        emptyMessage="empty"
      >
        <div>table-content</div>
      </ListPage>
    );

    const nav = screen.getByRole('navigation');
    const prev = within(nav).getByRole('button', { name: '前へ' });
    expect(prev).not.toBeDisabled();
    fireEvent.click(prev);
    expect(onPageChange).toHaveBeenCalledWith(0);
  });

  it('ページネーションの nav にアクセシブルネームがあること', () => {
    render(
      <ListPage
        title="顧客管理"
        state={baseState({ page: 0, pageCount: 3, total: 21, rows: Array(10).fill('x') })}
        emptyMessage="empty"
      >
        <div>table-content</div>
      </ListPage>
    );

    expect(screen.getByRole('navigation', { name: 'ページネーション' })).toBeInTheDocument();
  });

  it('現在ページが 5 起点の window を超えても番号ボタンに含まれること', () => {
    const onPageChange = jest.fn();
    render(
      <ListPage
        title="顧客管理"
        state={baseState({
          page: 6,
          pageCount: 10,
          total: 100,
          rows: Array(10).fill('x'),
          onPageChange,
        })}
        emptyMessage="empty"
      >
        <div>table-content</div>
      </ListPage>
    );

    expect(screen.getByRole('button', { name: '7' })).toHaveClass('border-primary');
  });

  it('デスクトップ用の前へ/次へアイコンボタンにアクセシブルネームがあること', () => {
    render(
      <ListPage
        title="顧客管理"
        state={baseState({ page: 1, pageCount: 3, total: 21, rows: Array(10).fill('x') })}
        emptyMessage="empty"
      >
        <div>table-content</div>
      </ListPage>
    );

    const nav = screen.getByRole('navigation');
    expect(within(nav).getByRole('button', { name: '前へ' })).toBeInTheDocument();
    expect(within(nav).getByRole('button', { name: '次へ' })).toBeInTheDocument();
  });
});
