import { readdirSync, readFileSync } from 'node:fs';
import { join } from 'node:path';
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { guestOrderApplicationApi } from '@/entities/order';
import type { Page } from '@/shared/api';
import type { PublicArticleSummary, PublicReview, SiteConfig } from '../model/types';
import ClassicAboutPage from '../templates/classic/about';
import ClassicMenuPage from '../templates/classic/menu';
import ClassicNewsPage from '../templates/classic/news';
import ClassicNewsDetailPage from '../templates/classic/news-detail';
import { pricingRows } from '../templates/classic/pricing';
import ClassicReservationPage from '../templates/classic/reservation';
import ClassicReviewsPage from '../templates/classic/reviews';
import ClassicSchedulePage from '../templates/classic/schedule';

jest.mock('@/entities/order', () => ({
  guestOrderApplicationApi: { request: jest.fn(), consent: jest.fn() },
}));
jest.mock('../model/useMemberReservationUrl', () => ({
  useMemberReservationUrl: () => 'https://app.example/member/reservations/new?store=s',
}));

const store = { id: '7', name: '店舗A' };
const siteConfig: SiteConfig = {};
const main = () => within(screen.getByRole('main'));

function pageOf<T>(overrides: Partial<Page<T>> = {}): Page<T> {
  return { content: [], total_pages: 0, total_elements: 0, size: 10, number: 0, ...overrides };
}

describe('classic テンプレートの後半のページ', () => {
  beforeEach(() => {
    localStorage.setItem('kizuna_age_verified', 'true');
  });
  afterEach(() => {
    localStorage.clear();
  });

  it('旧い共有の部品と共通の色トークンを参照するファイルが無い', () => {
    const dir = join(__dirname, '../templates/classic');
    const offenders = readdirSync(dir).filter(file =>
      /_sections|--storefront-|storefront-classic|theme\.css/.test(
        readFileSync(join(dir, file), 'utf8')
      )
    );

    expect(offenders).toEqual([]);
  });

  describe('出勤表', () => {
    it('キャストの名前を見出しに、時間帯をひと続きの文字で出し、詳細へ進める', () => {
      render(
        <ClassicSchedulePage
          store={store}
          siteConfig={siteConfig}
          shifts={[
            { cast_id: 'c1', cast_name: '美咲', start_time: '20:00:00', end_time: '02:00:00' },
            { cast_id: 'c2', cast_name: 'あやか', start_time: '18:00:00', end_time: '00:00:00' },
          ]}
        />
      );

      expect(screen.getByRole('heading', { level: 1, name: '出勤表' })).toBeInTheDocument();
      expect(main().getByRole('heading', { name: '美咲' })).toBeInTheDocument();
      expect(main().getByText('20:00–02:00')).toBeInTheDocument();
      expect(main().getByText('18:00–24:00')).toBeInTheDocument();
      expect(main().getByRole('link', { name: /あやか/ })).toHaveAttribute('href', '/casts/c2');
    });

    it('出勤が 0 件なら、その旨と問い合わせの案内を出す', () => {
      render(<ClassicSchedulePage store={store} siteConfig={siteConfig} shifts={[]} />);

      expect(main().getByText('本日の出勤情報はありません')).toBeInTheDocument();
      expect(main().queryByRole('list')).not.toBeInTheDocument();
    });
  });

  describe('料金', () => {
    const text =
      'セット料金 60分 10,000円〜\n延長 30分 6,000円\n\n指名料 3,000円\nサービス料・消費税を別途申し受けます。';

    it('説明文の一行を表の一行にし、金額と読める行は品目と金額に分ける', () => {
      render(<ClassicMenuPage store={store} siteConfig={{ pricing_description: text }} />);

      expect(screen.getByRole('heading', { level: 1, name: '料金' })).toBeInTheDocument();
      const table = main().getByRole('table');
      expect(within(table).getAllByRole('row')).toHaveLength(4);
      const row = within(table).getByRole('row', { name: /セット料金/ });
      expect(within(row).getByRole('rowheader')).toHaveTextContent('セット料金 60分');
      expect(within(row).getByRole('cell')).toHaveTextContent('10,000円〜');
      expect(
        within(table).getByRole('cell', { name: 'サービス料・消費税を別途申し受けます。' })
      ).toBeInTheDocument();
    });

    it('文面は書き換えず、金額と読めない行は品目だけの行として順に残す', () => {
      expect(pricingRows('VIPルーム ¥10,000 / 1時間\n10,000円\nボトル各種　時価\r\n  ')).toEqual([
        { item: 'VIPルーム', price: '¥10,000 / 1時間' },
        { item: '10,000円' },
        { item: 'ボトル各種　時価' },
      ]);
    });

    it('金額の後ろが長い但し書きや文になる行は、分けずに行ごと残す', () => {
      const longNote = 'ボトル ¥10,000 〜 ¥300,000（銘柄により異なります。詳しくはスタッフまで）';
      const sentence = '※ 22時以降は 1,000円 増しとなります';

      expect(pricingRows(`${longNote}\n${sentence}`)).toEqual([
        { item: longNote },
        { item: sentence },
      ]);
    });

    it('未設定なら準備中の案内を出し、表は出さない', () => {
      render(<ClassicMenuPage store={store} siteConfig={siteConfig} />);

      expect(main().getByText('料金情報は準備中です')).toBeInTheDocument();
      expect(main().queryByRole('table')).not.toBeInTheDocument();
    });
  });

  describe('店舗情報・アクセス', () => {
    it('住所・電話・営業時間・アクセス補足を表に出し、電話は発信できる', () => {
      render(
        <ClassicAboutPage
          store={store}
          siteConfig={{
            address: '東京都中央区銀座 0-0-0',
            phone: '03-0000-0000',
            business_hours: '20:00 〜 LAST',
            access_note: '銀座駅 A0 出口より徒歩 3 分',
          }}
        />
      );

      expect(
        screen.getByRole('heading', { level: 1, name: '店舗情報・アクセス' })
      ).toBeInTheDocument();
      for (const [label, value] of [
        ['住所', '東京都中央区銀座 0-0-0'],
        ['営業時間', '20:00 〜 LAST'],
        ['アクセス', '銀座駅 A0 出口より徒歩 3 分'],
      ]) {
        const row = main().getByRole('row', { name: new RegExp(`^${label}`) });
        expect(within(row).getByRole('cell')).toHaveTextContent(value);
      }
      expect(main().getByRole('link', { name: '03-0000-0000' })).toHaveAttribute(
        'href',
        'tel:03-0000-0000'
      );
    });

    it('何も設定されていなければ、空の表ではなく準備中の案内を出す', () => {
      render(<ClassicAboutPage store={store} siteConfig={siteConfig} />);

      expect(main().getByText('店舗情報は準備中です')).toBeInTheDocument();
      expect(main().queryByRole('table')).not.toBeInTheDocument();
    });
  });

  describe('ご予約', () => {
    const mockedRequest = guestOrderApplicationApi.request as jest.Mock;

    beforeEach(() => {
      jest.clearAllMocks();
      jest.mocked(guestOrderApplicationApi.consent).mockResolvedValue({
        version: '1',
        business_text: '今回の業務連絡を許可します',
        marketing_text: '販促を許可します（任意）',
      });
    });

    it('入力の誤りを欄に結び付けて出し、送信しない', async () => {
      render(<ClassicReservationPage store={store} siteConfig={siteConfig} />);

      expect(screen.getAllByRole('heading', { name: 'ご予約' })).toHaveLength(1);
      await screen.findByRole('checkbox', { name: /今回の業務連絡を許可します/ });
      fireEvent.click(screen.getByRole('button', { name: 'この内容で予約を希望する' }));

      const name = screen.getByLabelText('お名前');
      await waitFor(() => expect(name).toHaveAttribute('aria-invalid', 'true'));
      expect(name).toHaveAccessibleDescription('お名前をご入力ください');
      expect(screen.getByLabelText('ご希望日')).toHaveAccessibleDescription(
        'ご希望日を選択してください'
      );
      expect(screen.getByLabelText('お電話番号')).toHaveAccessibleDescription(
        '電話・メール・LINEのいずれかをご入力ください'
      );
      expect(
        screen.getByRole('checkbox', { name: /今回の業務連絡を許可します/ })
      ).toHaveAccessibleDescription('今回の予約に関する業務連絡への同意が必要です');
      expect(mockedRequest).not.toHaveBeenCalled();
    });

    it('正しく入力して送ると、受付番号と未確定の旨を出す', async () => {
      mockedRequest.mockResolvedValue({ id: 'A-100' });
      render(<ClassicReservationPage store={store} siteConfig={siteConfig} />);

      fireEvent.change(screen.getByLabelText('お名前'), { target: { value: 'ゲスト花子' } });
      fireEvent.change(screen.getByLabelText('メール'), { target: { value: 'g@example.com' } });
      fireEvent.change(screen.getByLabelText('ご希望日'), { target: { value: '2026-08-25' } });
      fireEvent.click(await screen.findByRole('checkbox', { name: /今回の業務連絡を許可します/ }));
      fireEvent.click(screen.getByRole('button', { name: 'この内容で予約を希望する' }));

      expect(await screen.findByText('受付番号: A-100')).toBeInTheDocument();
      expect(screen.getByText(/まだ予約は確定しておりません/)).toBeInTheDocument();
      expect(mockedRequest).toHaveBeenCalledWith('7', expect.objectContaining({ pax: 1 }));
    });

    it('サーバが断った理由を知らせ、入力を保つ', async () => {
      mockedRequest.mockRejectedValue({
        response: { status: 400, data: { error: 'ご希望日は本日以降を指定してください' } },
      });
      render(<ClassicReservationPage store={store} siteConfig={siteConfig} />);

      fireEvent.change(screen.getByLabelText('お名前'), { target: { value: 'ゲスト花子' } });
      fireEvent.change(screen.getByLabelText('LINE ID'), { target: { value: 'line-id' } });
      fireEvent.change(screen.getByLabelText('ご希望日'), { target: { value: '2020-01-01' } });
      fireEvent.click(await screen.findByRole('checkbox', { name: /今回の業務連絡を許可します/ }));
      fireEvent.click(screen.getByRole('button', { name: 'この内容で予約を希望する' }));

      expect(await screen.findByRole('alert')).toHaveTextContent(
        'ご希望日は本日以降を指定してください'
      );
      expect(screen.getByLabelText('お名前')).toHaveValue('ゲスト花子');
    });

    it('同意文面を取得できなければ、その旨と再試行を出す', async () => {
      jest.mocked(guestOrderApplicationApi.consent).mockRejectedValue(new Error('down'));
      render(<ClassicReservationPage store={store} siteConfig={siteConfig} />);

      expect(await screen.findByRole('alert')).toHaveTextContent(
        '同意文面を取得できませんでした。'
      );
      expect(screen.getByRole('button', { name: '再試行' })).toBeInTheDocument();
    });
  });

  describe('口コミ', () => {
    const renderReviews = (reviews: Page<PublicReview> | null, page = 0) =>
      render(
        <ClassicReviewsPage store={store} siteConfig={siteConfig} reviews={reviews} page={page} />
      );

    it('本文・表示名・日付を一件ずつ出し、表示名が無ければ匿名とする', () => {
      renderReviews(
        pageOf({
          content: [
            { display_name: '常連', body: '一行目\n二行目', received_on: '2026-01-02' },
            { display_name: null, body: '名前のない感想', received_on: '2026-10-11' },
          ],
          total_pages: 1,
          total_elements: 2,
        })
      );

      expect(screen.getByRole('heading', { level: 1, name: '口コミ' })).toBeInTheDocument();
      const [first, second] = main().getAllByRole('article');
      expect(within(first).getByText(/一行目/).textContent).toBe('一行目\n二行目');
      expect(within(first).getByText('常連')).toBeInTheDocument();
      expect(within(first).getByText('2026年1月2日')).toHaveAttribute('datetime', '2026-01-02');
      expect(within(second).getByText('匿名')).toBeInTheDocument();
      expect(main().queryByRole('navigation', { name: 'ページネーション' })).toBeNull();
    });

    it('取得に失敗したら 0 件と区別して知らせ、同じページを読み直せる', () => {
      renderReviews(null, 1);

      expect(main().getByRole('alert')).toHaveTextContent('口コミを読み込めませんでした');
      expect(main().getByRole('link', { name: '再読み込み' })).toHaveAttribute(
        'href',
        '/reviews?page=2'
      );
      expect(main().queryByText('掲載中の口コミはありません')).not.toBeInTheDocument();
    });

    it('0 件なら空の案内だけを出す', () => {
      renderReviews(pageOf());

      expect(main().getByText('掲載中の口コミはありません')).toBeInTheDocument();
      expect(main().queryByRole('alert')).not.toBeInTheDocument();
    });

    it('掲出があるのに範囲外のページを開いたら、最初のページへ戻れる', () => {
      renderReviews(pageOf({ number: 3, total_pages: 1, total_elements: 4 }), 3);

      expect(main().getByText('このページに口コミはありません')).toBeInTheDocument();
      expect(main().getByRole('link', { name: '最初のページへ' })).toHaveAttribute(
        'href',
        '/reviews'
      );
    });

    it('複数ページなら件数と範囲、現在地を示す番号、前へ・次へを出す', () => {
      const content = Array.from({ length: 10 }, (_, i) => ({
        display_name: `客${i}`,
        body: `本文${i}`,
        received_on: '2026-10-01',
      }));
      renderReviews(pageOf({ content, number: 1, total_pages: 3, total_elements: 25 }), 1);

      expect(main().getByText('25 件中 11-20 を表示')).toBeInTheDocument();
      const pager = within(main().getByRole('navigation', { name: 'ページネーション' }));
      expect(pager.getByRole('link', { name: '2' })).toHaveAttribute('aria-current', 'page');
      expect(pager.getByRole('link', { name: '1' })).toHaveAttribute('href', '/reviews');
      expect(pager.getByRole('link', { name: '前へ' })).toHaveAttribute('href', '/reviews');
      expect(pager.getByRole('link', { name: '次へ' })).toHaveAttribute('href', '/reviews?page=3');
    });

    it('端のページでは、進めない向きをリンクにしない', () => {
      const content = [{ display_name: '客', body: '本文', received_on: '2026-10-01' }];
      renderReviews(pageOf({ content, number: 2, total_pages: 3, total_elements: 21 }), 2);

      const pager = within(main().getByRole('navigation', { name: 'ページネーション' }));
      expect(pager.getByRole('link', { name: '前へ' })).toHaveAttribute('href', '/reviews?page=2');
      expect(pager.queryByRole('link', { name: '次へ' })).not.toBeInTheDocument();
      expect(pager.getByText('次へ')).toHaveAttribute('aria-disabled', 'true');
    });
  });

  describe('お知らせ', () => {
    const renderNews = (articles: Page<PublicArticleSummary> | null, page = 0) =>
      render(
        <ClassicNewsPage store={store} siteConfig={siteConfig} articles={articles} page={page} />
      );

    it('表題・公開日・表紙を一つのリンクにまとめ、表紙の無い記事には画像を置かない', () => {
      renderNews(
        pageOf({
          content: [
            {
              id: 'a1',
              title: '年末年始の営業',
              cover_image_url: 'https://img.example/cover.png',
              publish_from: '2026-11-01T00:00:00+09:00',
            },
            { id: 'a2', title: '表紙の無い記事', publish_from: null },
          ],
          total_pages: 1,
          total_elements: 2,
        })
      );

      expect(screen.getByRole('heading', { level: 1, name: 'お知らせ' })).toBeInTheDocument();
      const withCover = main().getByRole('link', { name: /年末年始の営業/ });
      expect(withCover).toHaveAttribute('href', '/news/a1');
      expect(within(withCover).getByText('2026年11月1日')).toBeInTheDocument();
      expect(withCover.querySelector('img')).not.toBeNull();
      const bare = main().getByRole('link', { name: '表紙の無い記事' });
      expect(bare.querySelector('img')).toBeNull();
      expect(bare.querySelector('time')).toBeNull();
    });

    it('取得に失敗したら 0 件と区別して知らせ、同じページを読み直せる', () => {
      renderNews(null, 2);

      expect(main().getByRole('alert')).toHaveTextContent('お知らせを読み込めませんでした');
      expect(main().getByRole('link', { name: '再読み込み' })).toHaveAttribute(
        'href',
        '/news?page=3'
      );
    });

    it('0 件なら空の案内を、範囲外のページなら最初のページへの導線を出す', () => {
      const { unmount } = renderNews(pageOf());
      expect(main().getByText('掲載中のお知らせはありません')).toBeInTheDocument();
      unmount();

      renderNews(pageOf({ number: 5, total_pages: 2, total_elements: 12 }), 5);
      expect(main().getByRole('link', { name: '最初のページへ' })).toHaveAttribute('href', '/news');
    });

    it('複数ページならページ送りを出す', () => {
      const content = Array.from({ length: 10 }, (_, i) => ({
        id: `a${i}`,
        title: `記事${i}`,
        publish_from: null,
      }));
      renderNews(pageOf({ content, number: 0, total_pages: 2, total_elements: 12 }));

      const pager = within(main().getByRole('navigation', { name: 'ページネーション' }));
      expect(pager.getByRole('link', { name: '次へ' })).toHaveAttribute('href', '/news?page=2');
      expect(pager.getByText('前へ')).toHaveAttribute('aria-disabled', 'true');
    });
  });

  describe('記事', () => {
    const article = {
      id: 'a1',
      title: '年末年始の営業について',
      publish_from: '2026-11-01T00:00:00+09:00',
      body: '一行目\n<b>太字ではない</b>',
    };

    it('表題を見出しに、本文を文字のまま改行を保って出し、一覧へ戻れる', () => {
      render(<ClassicNewsDetailPage store={store} siteConfig={siteConfig} article={article} />);

      expect(
        screen.getByRole('heading', { level: 1, name: '年末年始の営業について' })
      ).toBeInTheDocument();
      expect(main().getByText('2026年11月1日')).toBeInTheDocument();
      const body = main().getByText(/一行目/);
      expect(body.textContent).toBe('一行目\n<b>太字ではない</b>');
      expect(body.querySelector('b')).toBeNull();
      expect(main().queryByRole('presentation')).not.toBeInTheDocument();
      expect(main().getByRole('link', { name: 'お知らせ一覧へ' })).toHaveAttribute('href', '/news');
    });

    it('表紙があれば本文の前に出す', () => {
      render(
        <ClassicNewsDetailPage
          store={store}
          siteConfig={siteConfig}
          article={{ ...article, cover_image_url: 'https://img.example/cover.png' }}
        />
      );

      expect(main().getByRole('article').querySelector('img')).not.toBeNull();
    });
  });
});
