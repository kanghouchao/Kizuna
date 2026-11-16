import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { guestOrderApplicationApi } from '@/entities/order';
import type { Page } from '@/shared/api';
import type { PublicArticleSummary, PublicReview, SiteConfig } from '../model/types';
import DefaultAboutPage from '../templates/default/about';
import DefaultMenuPage from '../templates/default/menu';
import DefaultNewsPage from '../templates/default/news';
import DefaultNewsDetailPage from '../templates/default/news-detail';
import DefaultReservationPage from '../templates/default/reservation';
import DefaultReviewsPage from '../templates/default/reviews';
import DefaultSchedulePage from '../templates/default/schedule';

jest.mock('../model/useMemberReservationUrl', () => ({
  useMemberReservationUrl: () => null,
}));
jest.mock('@/entities/order', () => ({
  guestOrderApplicationApi: { request: jest.fn(), consent: jest.fn() },
}));

const mockedConsent = guestOrderApplicationApi.consent as jest.Mock;
const mockedRequest = guestOrderApplicationApi.request as jest.Mock;

const store = { id: '7', name: '店舗A' };
const siteConfig: SiteConfig = {};

function pageOf<T>(content: T[], overrides: Partial<Page<T>> = {}): Page<T> {
  return {
    content,
    number: 0,
    size: 10,
    total_pages: 1,
    total_elements: content.length,
    ...overrides,
  };
}

const review = (body: string): PublicReview => ({
  body,
  display_name: '常連',
  received_on: '2026-01-02',
});
const article = (id: string, extra: Partial<PublicArticleSummary> = {}): PublicArticleSummary => ({
  id,
  title: `記事${id}`,
  publish_from: '2026-03-01T15:30:00Z',
  ...extra,
});

/** main の中だけを見る。フッターとメニューにも同じ名前のリンクがある。 */
const main = () => within(screen.getByRole('main'));

describe('default テンプレート（後半のページ）', () => {
  beforeEach(() => {
    localStorage.setItem('kizuna_age_verified', 'true');
  });
  afterEach(() => {
    localStorage.clear();
    jest.clearAllMocks();
  });

  describe('店舗情報', () => {
    it('住所・電話・営業時間・アクセス補足を出し、電話は発信のリンクにする', () => {
      render(
        <DefaultAboutPage
          store={store}
          siteConfig={{
            address: '東京都中央区銀座 0-0-0',
            phone: '03-0000-0000',
            business_hours: '20:00 〜 LAST',
            access_note: '銀座駅より徒歩 3 分',
          }}
        />
      );

      expect(
        screen.getByRole('heading', { level: 1, name: '店舗情報・アクセス' })
      ).toBeInTheDocument();
      expect(screen.getByText('東京都中央区銀座 0-0-0')).toBeInTheDocument();
      expect(screen.getByRole('link', { name: '03-0000-0000' })).toHaveAttribute(
        'href',
        'tel:03-0000-0000'
      );
      expect(screen.getByText('20:00 〜 LAST')).toBeInTheDocument();
      expect(screen.getByText('銀座駅より徒歩 3 分')).toBeInTheDocument();
    });

    it('値の無い項目は行を出さず、何も無ければ準備中と伝える', () => {
      const { rerender } = render(
        <DefaultAboutPage store={store} siteConfig={{ address: '住所だけ' }} />
      );
      expect(screen.getByText('住所')).toBeInTheDocument();
      expect(screen.queryByText('電話')).not.toBeInTheDocument();

      rerender(<DefaultAboutPage store={store} siteConfig={siteConfig} />);
      expect(screen.getByText('店舗情報は準備中です')).toBeInTheDocument();
      expect(screen.queryByText('住所')).not.toBeInTheDocument();
    });
  });

  describe('料金', () => {
    it('金額で終わる行を表の行にし、それ以外を但し書きとして残す', () => {
      render(
        <DefaultMenuPage
          store={store}
          siteConfig={{
            pricing_description:
              'セット料金 60分 10,000円〜\n指名料　¥3,000\nサービス料・消費税を別途申し受けます。',
          }}
        />
      );

      const table = screen.getByRole('table', { name: '料金表' });
      const rows = within(table).getAllByRole('row');
      expect(rows).toHaveLength(2);
      expect(within(rows[0]).getByRole('rowheader')).toHaveTextContent('セット料金 60分');
      expect(within(rows[0]).getByRole('cell')).toHaveTextContent('10,000円〜');
      expect(within(rows[1]).getByRole('rowheader')).toHaveTextContent('指名料');
      expect(within(rows[1]).getByRole('cell')).toHaveTextContent('¥3,000');
      expect(screen.getByText('サービス料・消費税を別途申し受けます。')).toBeInTheDocument();
    });

    it('金額の行が無い説明文は表を作らずそのまま出す', () => {
      render(
        <DefaultMenuPage
          store={store}
          siteConfig={{ pricing_description: '料金はお電話でご案内します。\n営業時間 20:00' }}
        />
      );

      expect(screen.queryByRole('table')).not.toBeInTheDocument();
      expect(screen.getByText(/料金はお電話でご案内します。/)).toHaveTextContent('営業時間 20:00');
    });

    it('未設定なら準備中と伝える', () => {
      render(<DefaultMenuPage store={store} siteConfig={siteConfig} />);

      expect(screen.getByText('料金情報は準備中です')).toBeInTheDocument();
    });
  });

  describe('出勤表', () => {
    it('受け取った順に、キャスト名・時間帯・キャスト詳細へのリンクを出す', () => {
      render(
        <DefaultSchedulePage
          store={store}
          siteConfig={siteConfig}
          shifts={[
            { cast_id: '2', cast_name: 'B', start_time: '21:00:00', end_time: '00:00:00' },
            { cast_id: '1', cast_name: 'A', start_time: '18:00', end_time: '20:00' },
          ]}
        />
      );

      const names = main()
        .getAllByRole('heading', { level: 2 })
        .map(h => h.textContent);
      expect(names).toEqual(['B', 'A']);
      expect(screen.getByText('21:00–24:00')).toBeInTheDocument();
      expect(screen.getByText('18:00–20:00')).toBeInTheDocument();
      expect(main().getByRole('link', { name: /B/ })).toHaveAttribute('href', '/casts/2');
    });

    it('0 件なら出勤が無いことと問い合わせ先を伝える', () => {
      render(<DefaultSchedulePage store={store} siteConfig={siteConfig} shifts={[]} />);

      expect(screen.getByText('本日の出勤情報はありません')).toBeInTheDocument();
      expect(
        screen.getByText('出勤情報は準備中です。最新の情報はお電話でお問い合わせください。')
      ).toBeInTheDocument();
    });
  });

  describe('口コミ', () => {
    it('本文・表示名・受領日を出し、表示名が無ければ匿名とする', () => {
      render(
        <DefaultReviewsPage
          store={store}
          siteConfig={siteConfig}
          page={0}
          reviews={pageOf([review('一行目\n二行目'), { ...review('名無し'), display_name: null }])}
        />
      );

      const [first, second] = screen.getAllByRole('article');
      expect(within(first).getByText(/一行目/).textContent).toBe('一行目\n二行目');
      expect(within(first).getByText('常連')).toBeInTheDocument();
      expect(within(first).getByText('2026年1月2日')).toHaveAttribute('datetime', '2026-01-02');
      expect(within(second).getByText('匿名')).toBeInTheDocument();
      expect(screen.queryByRole('navigation', { name: 'ページネーション' })).toBeNull();
    });

    it('複数ページなら範囲と番号を出し、端の 前へ はリンクにしない', () => {
      render(
        <DefaultReviewsPage
          store={store}
          siteConfig={siteConfig}
          page={0}
          reviews={pageOf([review('a')], { total_pages: 3, total_elements: 21 })}
        />
      );

      expect(screen.getByText('21 件中 1–1 を表示')).toBeInTheDocument();
      const nav = screen.getByRole('navigation', { name: 'ページネーション' });
      expect(within(nav).queryByRole('link', { name: '前へ' })).toBeNull();
      expect(within(nav).getByText('前へ')).toHaveAttribute('aria-disabled', 'true');
      expect(within(nav).getByRole('link', { name: '次へ' })).toHaveAttribute(
        'href',
        '/reviews?page=2'
      );
      expect(within(nav).getByRole('link', { name: '1' })).toHaveAttribute('aria-current', 'page');
      expect(within(nav).getByRole('link', { name: '3' })).toHaveAttribute(
        'href',
        '/reviews?page=3'
      );
    });

    it('取得の失敗は同じページを開き直す導線つきで知らせる', () => {
      render(<DefaultReviewsPage store={store} siteConfig={siteConfig} page={2} reviews={null} />);

      const alert = screen.getByRole('alert');
      expect(alert).toHaveTextContent('口コミを読み込めませんでした');
      expect(within(alert).getByRole('link', { name: '再読み込み' })).toHaveAttribute(
        'href',
        '/reviews?page=3'
      );
    });

    it('0 件と、縮んで空になったページを言い分ける', () => {
      const { rerender } = render(
        <DefaultReviewsPage store={store} siteConfig={siteConfig} page={0} reviews={pageOf([])} />
      );
      expect(screen.getByText('掲載中の口コミはありません')).toBeInTheDocument();

      rerender(
        <DefaultReviewsPage
          store={store}
          siteConfig={siteConfig}
          page={4}
          reviews={pageOf<PublicReview>([], { number: 4, total_pages: 2, total_elements: 12 })}
        />
      );
      expect(screen.getByText('このページに口コミはありません')).toBeInTheDocument();
      expect(screen.getByRole('link', { name: '最初のページへ' })).toHaveAttribute(
        'href',
        '/reviews'
      );
    });
  });

  describe('お知らせ一覧', () => {
    it('表題・公開日・表紙を出し、表紙の無い記事には画像を置かない', () => {
      render(
        <DefaultNewsPage
          store={store}
          siteConfig={siteConfig}
          page={0}
          articles={pageOf([
            article('1', { cover_image_url: 'https://img.example/cover.png' }),
            article('2', { publish_from: null }),
          ])}
        />
      );

      const first = main().getByRole('link', { name: /記事1/ });
      expect(first).toHaveAttribute('href', '/news/1');
      // 15:30Z は東京では翌日
      expect(within(first).getByText('2026年3月2日')).toBeInTheDocument();
      expect(first.querySelector('img')).not.toBeNull();
      const second = main().getByRole('link', { name: /記事2/ });
      expect(second.querySelector('img')).toBeNull();
      expect(second.querySelector('time')).toBeNull();
    });

    it('複数ページならページ送りを出し、端の 次へ はリンクにしない', () => {
      render(
        <DefaultNewsPage
          store={store}
          siteConfig={siteConfig}
          page={1}
          articles={pageOf([article('11')], { number: 1, total_pages: 2, total_elements: 11 })}
        />
      );

      const nav = screen.getByRole('navigation', { name: 'ページネーション' });
      expect(within(nav).getByRole('link', { name: '前へ' })).toHaveAttribute('href', '/news');
      expect(within(nav).queryByRole('link', { name: '次へ' })).toBeNull();
      expect(screen.getByText('11 件中 11–11 を表示')).toBeInTheDocument();
    });

    it('取得の失敗・0 件・縮んで空になったページを言い分ける', () => {
      const { rerender } = render(
        <DefaultNewsPage store={store} siteConfig={siteConfig} page={0} articles={null} />
      );
      expect(screen.getByRole('alert')).toHaveTextContent('お知らせを読み込めませんでした');
      expect(screen.getByRole('link', { name: '再読み込み' })).toHaveAttribute('href', '/news');

      rerender(
        <DefaultNewsPage store={store} siteConfig={siteConfig} page={0} articles={pageOf([])} />
      );
      expect(screen.getByText('掲載中のお知らせはありません')).toBeInTheDocument();

      rerender(
        <DefaultNewsPage
          store={store}
          siteConfig={siteConfig}
          page={3}
          articles={pageOf<PublicArticleSummary>([], {
            number: 3,
            total_pages: 1,
            total_elements: 4,
          })}
        />
      );
      expect(screen.getByRole('link', { name: '最初のページへ' })).toHaveAttribute('href', '/news');
    });
  });

  describe('記事', () => {
    it('表題・公開日・本文を出し、一覧へ戻れる', () => {
      render(
        <DefaultNewsDetailPage
          store={store}
          siteConfig={siteConfig}
          article={{ ...article('1'), body: '本文の一行目\n<b>二行目</b>' }}
        />
      );

      expect(screen.getByRole('heading', { level: 1, name: '記事1' })).toBeInTheDocument();
      expect(screen.getByText('2026年3月2日')).toBeInTheDocument();
      expect(screen.getByText(/本文の一行目/).textContent).toBe('本文の一行目\n<b>二行目</b>');
      expect(main().getByRole('link', { name: 'お知らせ一覧へ' })).toHaveAttribute('href', '/news');
      expect(screen.getByRole('main').querySelector('img')).toBeNull();
    });
  });

  describe('予約', () => {
    beforeEach(() => {
      mockedConsent.mockResolvedValue({
        version: 3,
        business_text: '業務連絡に同意します',
        marketing_text: '販促の連絡に同意します',
      });
    });

    it('入力の誤りを欄に結び付けて出し、直すと送信して受付番号を伝える', async () => {
      mockedRequest.mockResolvedValue({ id: 'app-1' });
      render(<DefaultReservationPage store={store} siteConfig={siteConfig} />);

      expect(screen.getByRole('heading', { level: 1, name: 'ご予約' })).toBeInTheDocument();
      const business = await screen.findByRole('checkbox', { name: /業務連絡に同意します/ });
      const submit = screen.getByRole('button', { name: 'この内容で予約を希望する' });
      fireEvent.click(submit);

      const name = screen.getByLabelText('お名前');
      await waitFor(() => expect(name).toHaveAccessibleDescription('お名前をご入力ください'));
      expect(name).toBeInvalid();
      expect(screen.getByLabelText('お電話番号')).toHaveAccessibleDescription(
        '電話・メール・LINEのいずれかをご入力ください'
      );
      expect(screen.getByLabelText('ご希望日')).toHaveAccessibleDescription(
        'ご希望日を選択してください'
      );
      expect(business).toHaveAccessibleDescription('今回の予約に関する業務連絡への同意が必要です');
      expect(mockedRequest).not.toHaveBeenCalled();

      fireEvent.change(name, { target: { value: 'ゲスト花子' } });
      fireEvent.change(screen.getByLabelText('メール'), { target: { value: 'g@example.com' } });
      fireEvent.change(screen.getByLabelText('ご希望日'), { target: { value: '2026-08-25' } });
      fireEvent.click(business);
      fireEvent.click(submit);

      expect(await screen.findByText('受付番号: app-1')).toBeInTheDocument();
      expect(screen.getByText('ご予約の希望を承りました')).toBeInTheDocument();
      expect(mockedRequest).toHaveBeenCalledWith(
        '7',
        expect.objectContaining({
          business_date: '2026-08-25',
          contact_consent: { version: 3, business_allowed: true, marketing_allowed: false },
        })
      );
    });

    it('送信の失敗を知らせ、入力は残す', async () => {
      mockedRequest.mockRejectedValue(new Error('network'));
      render(<DefaultReservationPage store={store} siteConfig={siteConfig} />);

      fireEvent.click(await screen.findByRole('checkbox', { name: /業務連絡に同意します/ }));
      fireEvent.change(screen.getByLabelText('お名前'), { target: { value: 'ゲスト花子' } });
      fireEvent.change(screen.getByLabelText('LINE ID'), { target: { value: 'line-1' } });
      fireEvent.change(screen.getByLabelText('ご希望日'), { target: { value: '2020-01-01' } });
      fireEvent.click(screen.getByRole('button', { name: 'この内容で予約を希望する' }));

      expect(await screen.findByRole('alert')).toHaveTextContent(
        'ご予約の送信に失敗しました。時間をおいてお試しください'
      );
      expect(screen.getByLabelText('お名前')).toHaveValue('ゲスト花子');
    });

    it('同意文面を取得できなければ、再試行で取り直せる', async () => {
      mockedConsent.mockRejectedValueOnce(new Error('down'));
      render(<DefaultReservationPage store={store} siteConfig={siteConfig} />);

      expect(await screen.findByText('同意文面を取得できませんでした。')).toBeInTheDocument();
      fireEvent.click(screen.getByRole('button', { name: '再試行' }));

      expect(
        await screen.findByRole('checkbox', { name: /業務連絡に同意します/ })
      ).toBeInTheDocument();
    });
  });
});
