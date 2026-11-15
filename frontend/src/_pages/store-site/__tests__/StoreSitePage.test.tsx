import { render, screen } from '@testing-library/react';
import { notFound } from 'next/navigation';
import { resolveHost } from '@/shared/api/index.server';
import { storefrontService } from '../api/storefront';
import StoreSitePage from '../ui/StoreSitePage';

jest.mock('next/navigation', () => ({
  notFound: jest.fn(() => {
    throw new Error('NEXT_NOT_FOUND');
  }),
}));
jest.mock('@/shared/api/index.server', () => ({ resolveHost: jest.fn() }));
jest.mock('../api/storefront', () => ({
  storefrontService: {
    fetchSiteConfig: jest.fn(),
    fetchCasts: jest.fn(),
    fetchCast: jest.fn(),
    fetchShifts: jest.fn(),
    fetchReviews: jest.fn(),
    fetchArticles: jest.fn(),
    fetchArticle: jest.fn(),
  },
}));
// 殻が渡す props をそのまま文字にして、受け渡しだけを確かめる
jest.mock('../templates/loadTemplate', () => ({
  loadTemplatePage: jest.fn((templateKey: string) =>
    Promise.resolve({
      templateKey: templateKey === 'no-such-template' ? 'default' : templateKey,
      Page: (props: Record<string, unknown>) => <pre>{JSON.stringify(props)}</pre>,
    })
  ),
}));

const service = jest.mocked(storefrontService);
const store = { id: '7', name: '店舗A' };

const renderedProps = async (element: Promise<React.ReactElement>) => {
  const { container } = render(await element);
  return {
    marker: container.firstElementChild?.getAttribute('data-storefront-template'),
    props: JSON.parse(screen.getByText(/^\{/).textContent ?? ''),
  };
};

describe('StoreSitePage', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    jest.mocked(resolveHost).mockResolvedValue({ kind: 'store', store });
    service.fetchSiteConfig.mockResolvedValue({ template_key: 'classic', mv_type: 'image' });
  });

  it('店舗ドメインでなければ何も取得せず 404 にする', async () => {
    jest.mocked(resolveHost).mockResolvedValue({ kind: 'app' });

    await expect(StoreSitePage({ page: 'page' })).rejects.toThrow('NEXT_NOT_FOUND');
    expect(service.fetchSiteConfig).not.toHaveBeenCalled();
  });

  it('最も外側に描いたテンプレートの標識を出し、取得したデータをページへ渡す', async () => {
    service.fetchCasts.mockResolvedValue([{ id: '1', name: 'キャストA' }]);

    const { marker, props } = await renderedProps(StoreSitePage({ page: 'page' }));

    expect(marker).toBe('classic');
    expect(props).toEqual({
      store,
      siteConfig: { template_key: 'classic', mv_type: 'image' },
      casts: [{ id: '1', name: 'キャストA' }],
    });
    expect(service.fetchCasts).toHaveBeenCalledWith('7');
  });

  it('未知の template_key の店舗は default の標識で描く', async () => {
    service.fetchSiteConfig.mockResolvedValue({ template_key: 'no-such-template' });

    const { marker } = await renderedProps(StoreSitePage({ page: 'menu' }));

    expect(marker).toBe('default');
  });

  it('template_key が未設定の店舗は default で描く', async () => {
    service.fetchSiteConfig.mockResolvedValue({ mv_type: 'image' });

    const { marker } = await renderedProps(StoreSitePage({ page: 'about' }));

    expect(marker).toBe('default');
  });

  it('一覧は要求されたページを取得し、ページ番号と一緒に渡す', async () => {
    service.fetchReviews.mockResolvedValue(null);
    service.fetchArticles.mockResolvedValue(null);

    const reviews = await renderedProps(StoreSitePage({ page: 'reviews', pageIndex: 2 }));
    expect(reviews.props).toMatchObject({ reviews: null, page: 2 });
    expect(service.fetchReviews).toHaveBeenCalledWith('7', 2, 10);
  });

  it('お知らせ一覧も要求されたページを取得する', async () => {
    service.fetchArticles.mockResolvedValue(null);

    const news = await renderedProps(StoreSitePage({ page: 'news', pageIndex: 1 }));

    expect(news.props).toMatchObject({ articles: null, page: 1 });
    expect(service.fetchArticles).toHaveBeenCalledWith('7', 1, 10);
  });

  it('出勤表は本日の出勤を渡す', async () => {
    service.fetchShifts.mockResolvedValue([{ cast_name: 'キャストA' }]);

    const { props } = await renderedProps(StoreSitePage({ page: 'schedule' }));

    expect(props.shifts).toEqual([{ cast_name: 'キャストA' }]);
  });

  it('キャスト詳細は対象のキャストを渡し、存在しなければ 404 にする', async () => {
    service.fetchCast.mockResolvedValueOnce({ id: '3', name: 'キャストB' });
    const { props } = await renderedProps(StoreSitePage({ page: 'cast-detail', castId: '3' }));
    expect(props.cast).toEqual({ id: '3', name: 'キャストB' });
    expect(service.fetchCast).toHaveBeenCalledWith('7', '3');

    service.fetchCast.mockResolvedValueOnce(null);
    await expect(StoreSitePage({ page: 'cast-detail', castId: '9' })).rejects.toThrow(
      'NEXT_NOT_FOUND'
    );
    expect(notFound).toHaveBeenCalled();
  });

  it('記事は対象の記事を渡し、存在しない・掲出外なら 404 にする', async () => {
    const article = { id: '12', title: '告知', body: '本文', publish_from: null };
    service.fetchArticle.mockResolvedValueOnce(article);
    const { props } = await renderedProps(StoreSitePage({ page: 'news-detail', articleId: '12' }));
    expect(props.article).toEqual(article);

    service.fetchArticle.mockResolvedValueOnce(null);
    await expect(StoreSitePage({ page: 'news-detail', articleId: '99' })).rejects.toThrow(
      'NEXT_NOT_FOUND'
    );
  });
});
