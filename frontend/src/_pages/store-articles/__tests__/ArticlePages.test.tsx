import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { ArticleCreatePage, ArticleEditPage, ArticlesPage } from '..';
import { Article, articleApi } from '@/entities/content/article';
import { notify } from '@/shared/notify';
import { useMe } from '@/entities/user';
import type { PermissionCode } from '@/entities/user';

const mockPush = jest.fn();
jest.mock('next/navigation', () => ({
  useRouter: () => ({ push: mockPush, back: jest.fn() }),
  useParams: () => ({ id: '7' }),
}));
jest.mock('@/entities/content/article', () => ({
  ...jest.requireActual('@/entities/content/article/model/types'),
  articleApi: {
    list: jest.fn(),
    get: jest.fn(),
    create: jest.fn(),
    update: jest.fn(),
    updatePublication: jest.fn(),
    remove: jest.fn(),
  },
}));
jest.mock('@/entities/user/model/MeContext', () => ({
  ...jest.requireActual('@/entities/user/model/MeContext'),
  useMe: jest.fn(),
}));
jest.mock('@/shared/notify', () => ({
  notify: { success: jest.fn(), error: jest.fn(), warning: jest.fn() },
}));

const api = jest.mocked(articleApi);
const article: Article = {
  id: '7',
  title: '夏のお知らせ',
  body: '本文です',
  publication: { published: false, publish_from: null, publish_until: null, display_order: null },
  displayed: false,
  updated_at: '2026-10-01T00:00:00Z',
  version: 3,
};
const permission = (...permissions: PermissionCode[]) =>
  jest.mocked(useMe).mockReturnValue({
    me: { email: 'staff@example.com', user_type: 'STAFF', permissions, line_linked: false },
    isLoading: false,
    failure: null,
    reload: jest.fn(),
    setMe: jest.fn(),
  });

beforeEach(() => {
  jest.resetAllMocks();
  permission('ARTICLE_MANAGE');
  api.get.mockResolvedValue(article);
});

describe('一覧', () => {
  test('権限が無ければ取得せず拒否を表示する', () => {
    permission('REVIEW_VIEW');
    render(<ArticlesPage />);
    expect(screen.getByRole('alert')).toHaveTextContent('記事の管理権限がありません');
    expect(api.list).not.toHaveBeenCalled();
  });

  test('displayed に従って掲出中／非掲出を表示し、作成画面へ遷移できる', async () => {
    api.list.mockResolvedValue({
      rows: [
        { ...article, id: '1', title: '掲出中の記事', displayed: true },
        { ...article, id: '2', title: '下書きの記事', displayed: false },
      ],
      page: 0,
      pageCount: 1,
      total: 2,
    });
    render(<ArticlesPage />);
    const shown = (await screen.findByText('掲出中の記事')).closest('tr')!;
    expect(within(shown).getByText('掲出中')).toBeVisible();
    const hidden = screen.getByText('下書きの記事').closest('tr')!;
    expect(within(hidden).getByText('非掲出')).toBeVisible();
    expect(within(hidden).getByText(/非公開 \/ 期間指定なし/)).toBeVisible();
    expect(api.list).toHaveBeenCalledWith(0);
    expect(screen.getByRole('link', { name: '記事を作成' })).toHaveAttribute(
      'href',
      '/store/articles/create'
    );
  });
});

describe('作成', () => {
  test('表題と本文の上限を送信前に検証する', async () => {
    render(<ArticleCreatePage />);
    expect(screen.getByText(/非公開で作成されます/)).toBeVisible();
    fireEvent.change(screen.getByLabelText('表題'), { target: { value: 'あ'.repeat(101) } });
    fireEvent.change(screen.getByLabelText('本文'), { target: { value: 'い'.repeat(10001) } });
    fireEvent.click(screen.getByRole('button', { name: '記事を作成' }));
    expect(await screen.findByText('表題を1〜100文字で入力してください')).toBeVisible();
    expect(screen.getByText('本文を1〜10000文字で入力してください')).toBeVisible();
    fireEvent.change(screen.getByLabelText('表題'), { target: { value: '   ' } });
    fireEvent.click(screen.getByRole('button', { name: '記事を作成' }));
    expect(await screen.findByText('表題を1〜100文字で入力してください')).toBeVisible();
    expect(api.create).not.toHaveBeenCalled();
  });

  test('表題と本文を送り、作成した記事の編集画面へ移る', async () => {
    api.create.mockResolvedValue({ id: '42' });
    render(<ArticleCreatePage />);
    fireEvent.change(screen.getByLabelText('表題'), { target: { value: '新着' } });
    fireEvent.change(screen.getByLabelText('本文'), { target: { value: '一行目\n二行目' } });
    fireEvent.click(screen.getByRole('button', { name: '記事を作成' }));
    await waitFor(() =>
      expect(api.create).toHaveBeenCalledWith({ title: '新着', body: '一行目\n二行目' })
    );
    await waitFor(() => expect(mockPush).toHaveBeenCalledWith('/store/articles/42/edit'));
    expect(notify.success).toHaveBeenCalledWith('記事を作成しました');
  });
});

describe('編集', () => {
  test('本文の保存と公開設定の保存はそれぞれ直前の応答の版を送る', async () => {
    api.update.mockResolvedValue({ ...article, body: '直した本文', version: 4 });
    api.updatePublication.mockResolvedValue({
      ...article,
      body: '直した本文',
      version: 5,
      displayed: true,
      publication: { ...article.publication, published: true },
    });
    api.remove.mockResolvedValue();
    render(<ArticleEditPage />);
    fireEvent.change(await screen.findByLabelText('本文'), { target: { value: '直した本文' } });
    fireEvent.click(screen.getByRole('button', { name: '記事を保存' }));
    await waitFor(() =>
      expect(api.update).toHaveBeenCalledWith('7', {
        title: '夏のお知らせ',
        body: '直した本文',
        version: 3,
      })
    );
    fireEvent.click(screen.getByRole('switch', { name: '公式サイトで公開する' }));
    fireEvent.change(screen.getByLabelText(/公開開始/), { target: { value: '2026-11-01T20:00' } });
    fireEvent.click(screen.getByRole('button', { name: '公開設定を保存' }));
    await waitFor(() =>
      expect(api.updatePublication).toHaveBeenCalledWith('7', {
        published: true,
        publish_from: '2026-11-01T11:00:00.000Z',
        publish_until: null,
        display_order: null,
        version: 4,
      })
    );
    expect(await screen.findByText('掲出中')).toBeVisible();
    fireEvent.click(screen.getByRole('button', { name: '記事を削除' }));
    const confirmation = await screen.findByRole('alertdialog');
    fireEvent.click(within(confirmation).getByRole('button', { name: '削除する' }));
    await waitFor(() => expect(api.remove).toHaveBeenCalledWith('7', 5));
    await waitFor(() => expect(mockPush).toHaveBeenCalledWith('/store/articles'));
  });

  test('409 は競合を表示し、最新の内容を読み込み直してから操作させる', async () => {
    api.update.mockRejectedValue({ response: { status: 409, data: { error: '競合しました' } } });
    render(<ArticleEditPage />);
    fireEvent.change(await screen.findByLabelText('表題'), { target: { value: '改題' } });
    fireEvent.click(screen.getByRole('button', { name: '記事を保存' }));
    expect(
      await screen.findByText(
        '記事が更新されています。最新の内容を読み込んでから、改めて操作してください。'
      )
    ).toBeVisible();
    expect(screen.getByRole('button', { name: '記事を保存' })).toBeDisabled();
    expect(notify.error).toHaveBeenCalledWith('競合しました');
    api.get.mockResolvedValue({ ...article, title: '他の人の表題', version: 9 });
    fireEvent.click(screen.getByRole('button', { name: '最新の内容を読み込む' }));
    expect(await screen.findByDisplayValue('他の人の表題')).toBeVisible();
    expect(screen.getByRole('button', { name: '記事を保存' })).toBeEnabled();
  });

  test('削除の 409 も競合として扱い、画面に留まる', async () => {
    api.remove.mockRejectedValue({ response: { status: 409 } });
    render(<ArticleEditPage />);
    fireEvent.click(await screen.findByRole('button', { name: '記事を削除' }));
    fireEvent.click(
      within(await screen.findByRole('alertdialog')).getByRole('button', { name: '削除する' })
    );
    await waitFor(() => expect(api.remove).toHaveBeenCalledWith('7', 3));
    expect(
      await screen.findByText(
        '記事が更新されています。最新の内容を読み込んでから、改めて操作してください。'
      )
    ).toBeVisible();
    expect(mockPush).not.toHaveBeenCalled();
  });

  test('見つからない記事は一覧への導線だけを出す', async () => {
    api.get.mockRejectedValue({ response: { status: 404 } });
    render(<ArticleEditPage />);
    expect(await screen.findByText('記事が見つかりません')).toBeVisible();
    expect(screen.getByRole('link', { name: '記事一覧へ' })).toHaveAttribute(
      'href',
      '/store/articles'
    );
  });
});
