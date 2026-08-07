import { render, screen } from '@testing-library/react';
import { PageHeader } from '@/widgets/page-header';
import { ListPage } from '@/widgets/list-page';

/**
 * widgets/list-page/ui/ListPage.tsx は Steiger の同一レイヤー cross-slice import 禁止
 * （fsd/forbidden-imports）のため widgets/page-header を import できず、見出し markup を
 * 逐字複製している。正本が 2 つに分かれた状態であり、片方だけ class を直すと気付かず乖離する。
 * このテストは両者の見出し block（外枠 div・タイトル div・h1・description p・actions
 * wrapper div の class 文字列）が一致し続けることを機械的に固定する。
 *
 * widgets 同士を横断 import できるのは FSD の層構造に属さない src/__tests__ だけ
 * （frontend/CLAUDE.md の「cross-cutting invariant tests」）。
 */
describe('ListPage の見出し markup と PageHeader の一致', () => {
  const headingProps = {
    title: '顧客管理',
    description: '顧客情報の登録・編集ができます。',
    actions: <button type="button">新規顧客登録</button>,
  };

  it('見出し block の class 文字列がすべて一致すること', () => {
    const { unmount: unmountPageHeader } = render(<PageHeader {...headingProps} />);
    const pageHeaderH1 = screen.getByRole('heading', { level: 1 });
    const pageHeaderTitleBlock = pageHeaderH1.parentElement!;
    const pageHeaderOuter = pageHeaderTitleBlock.parentElement!;
    const pageHeaderDescription = screen.getByText(headingProps.description);
    const pageHeaderActionsWrapper = screen.getByRole('button', {
      name: '新規顧客登録',
    }).parentElement!;

    const snapshot = {
      outer: pageHeaderOuter.className,
      titleBlock: pageHeaderTitleBlock.className,
      h1: pageHeaderH1.className,
      description: pageHeaderDescription.className,
      actionsWrapper: pageHeaderActionsWrapper.className,
    };
    unmountPageHeader();

    render(
      <ListPage
        {...headingProps}
        state={{
          rows: ['a'],
          page: 0,
          pageCount: 1,
          total: 1,
          isLoading: false,
          failed: false,
          onPageChange: jest.fn(),
        }}
        emptyMessage="empty"
        errorMessage="一覧の取得に失敗しました"
        onRetry={jest.fn()}
      >
        <div>table</div>
      </ListPage>
    );
    const listPageH1 = screen.getByRole('heading', { level: 1 });
    const listPageTitleBlock = listPageH1.parentElement!;
    const listPageOuter = listPageTitleBlock.parentElement!;
    const listPageDescription = screen.getByText(headingProps.description);
    const listPageActionsWrapper = screen.getByRole('button', {
      name: '新規顧客登録',
    }).parentElement!;

    expect(listPageOuter.className).toBe(snapshot.outer);
    expect(listPageTitleBlock.className).toBe(snapshot.titleBlock);
    expect(listPageH1.className).toBe(snapshot.h1);
    expect(listPageDescription.className).toBe(snapshot.description);
    expect(listPageActionsWrapper.className).toBe(snapshot.actionsWrapper);
  });
});
