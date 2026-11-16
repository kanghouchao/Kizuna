import { fireEvent, render, screen, within } from '@testing-library/react';
import { guestOrderApplicationApi } from '@/entities/order';
import type { Page } from '@/shared/api';
import { STORE_SITE_NAV_PAGES } from '../model/navigation';
import type { SiteConfig } from '../model/types';
import ModernCastDetailPage from '../templates/modern/cast-detail';
import ModernAboutPage from '../templates/modern/about';
import ModernCastsPage from '../templates/modern/casts';
import ModernMenuPage from '../templates/modern/menu';
import ModernNewsPage from '../templates/modern/news';
import ModernNewsDetailPage from '../templates/modern/news-detail';
import ModernTopPage from '../templates/modern/page';
import ModernReservationPage from '../templates/modern/reservation';
import ModernReviewsPage from '../templates/modern/reviews';
import ModernSchedulePage from '../templates/modern/schedule';

jest.mock('next/navigation', () => ({ usePathname: () => '/' }));
jest.mock('@/entities/order', () => ({
  guestOrderApplicationApi: { request: jest.fn(), consent: jest.fn() },
}));
jest.mock('../model/useMemberReservationUrl', () => ({
  useMemberReservationUrl: () => 'https://app.example/member/reservations/new?store=s1',
}));

const store = { id: '7', name: '店舗A' };
const fullConfig: SiteConfig = {
  logo_url: 'https://img.example/logo.png',
  banner_url: 'https://img.example/banner.png',
  mv_url: 'https://img.example/mv.png',
  mv_type: 'image',
  catch_copy: '今宵、月の華が咲く。',
  description: '大人のための社交場。',
  sns_links: [{ platform: 'x', url: 'https://x.example/a' }],
  partner_links: [{ name: '提携サイト', url: 'https://partner.example' }],
};
const casts = [
  { id: 'c1', name: '美咲', age: 24, height: 158, photo_url: 'https://img.example/c1.png' },
  { id: 'c2', name: 'ひなた' },
];

const pageOf = <T,>(content: T[], overrides: Partial<Page<T>> = {}): Page<T> => ({
  content,
  total_pages: 1,
  total_elements: content.length,
  size: 10,
  number: 0,
  ...overrides,
});

const hrefsOf = (name: string) =>
  screen.getAllByRole('link', { name }).map(link => link.getAttribute('href'));

describe('modern テンプレート', () => {
  beforeEach(() => {
    localStorage.setItem('kizuna_age_verified', 'true');
  });
  afterEach(() => {
    localStorage.clear();
  });

  it('トップは設定した内容・キャスト・予約とログインの入口を欠かさず出す', () => {
    render(<ModernTopPage store={store} siteConfig={fullConfig} casts={casts} />);

    expect(screen.getByRole('heading', { level: 1, name: '店舗A' })).toBeInTheDocument();
    expect(screen.getByText('今宵、月の華が咲く。')).toBeInTheDocument();
    expect(screen.getByText('大人のための社交場。')).toBeInTheDocument();
    expect(screen.getByRole('img', { name: '店舗A のバナー' })).toBeInTheDocument();
    expect(screen.getByRole('img', { name: '店舗A のメインビジュアル' })).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: '美咲' })).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'ひなた' })).toBeInTheDocument();
    expect(hrefsOf('美咲')).toContain('/casts/c1');
    expect(hrefsOf('ご予約')).toContain('/reservation');
    expect(hrefsOf('ログイン')).toContain('/auth/login');
    expect(hrefsOf('WEB予約')).toContain('https://app.example/member/reservations/new?store=s1');
    expect(hrefsOf('提携サイト')).toEqual(['https://partner.example']);
    expect(hrefsOf('x')).toEqual(['https://x.example/a']);
  });

  it('デスクトップのナビは共有の一覧の全ページへ行ける', () => {
    render(<ModernTopPage store={store} siteConfig={{}} casts={[]} />);

    const [desktopNav] = screen.getAllByRole('navigation', { name: 'メインメニュー' });
    const hrefs = within(desktopNav)
      .getAllByRole('link')
      .map(link => link.getAttribute('href'));
    expect(hrefs).toEqual(expect.arrayContaining(STORE_SITE_NAV_PAGES.map(page => page.href)));
  });

  it('未設定の区画は出さず、キャストが 0 人でも準備中の案内で版面を保つ', () => {
    render(<ModernTopPage store={store} siteConfig={{}} casts={[]} />);

    expect(screen.getByRole('heading', { level: 1, name: '店舗A' })).toBeInTheDocument();
    expect(screen.queryByRole('img')).not.toBeInTheDocument();
    expect(screen.queryByText('ABOUT')).not.toBeInTheDocument();
    expect(screen.getByText('キャスト情報は準備中です')).toBeInTheDocument();
  });

  it('キャスト一覧は名前・年齢・身長を出し、詳細へ進める', () => {
    render(<ModernCastsPage store={store} siteConfig={{}} casts={casts} />);

    expect(screen.getByRole('heading', { level: 1, name: 'キャスト一覧' })).toBeInTheDocument();
    expect(screen.getByText('24歳')).toBeInTheDocument();
    expect(screen.getByText('158cm')).toBeInTheDocument();
    expect(hrefsOf('ひなた')).toEqual(['/casts/c2']);
  });

  it('キャスト詳細は値のある項目だけをプロフィールに出し、紹介文と予約の入口を出す', () => {
    render(
      <ModernCastDetailPage
        store={store}
        siteConfig={{}}
        cast={{
          id: 'c1',
          name: '美咲',
          age: 24,
          bust: 84,
          introduction: 'はじめまして。',
          custom_fields: [{ label: '趣味', value: '映画' }],
        }}
      />
    );

    expect(screen.getByRole('heading', { level: 1, name: '美咲' })).toBeInTheDocument();
    expect(
      screen.getAllByRole('term').map(term => [term.textContent, term.nextSibling?.textContent])
    ).toEqual([
      ['年齢', '24歳'],
      ['バスト', '84'],
      ['趣味', '映画'],
    ]);
    expect(screen.getByText('はじめまして。')).toBeInTheDocument();
    expect(hrefsOf('美咲さんを予約する')).toEqual(['/reservation']);
    expect(hrefsOf('キャスト一覧へ')).toEqual(['/casts']);
  });

  it('年齢確認は拒否で制限の画面を出し、同意で消えて記録する', () => {
    localStorage.clear();
    const { unmount } = render(<ModernCastsPage store={store} siteConfig={{}} casts={[]} />);

    fireEvent.click(screen.getByRole('button', { name: 'いいえ' }));
    expect(screen.getByText('アクセス制限')).toBeInTheDocument();
    expect(screen.getByText('18歳未満の方はご利用いただけません')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'ウィンドウを閉じる' })).toBeInTheDocument();
    expect(localStorage.getItem('kizuna_age_verified')).toBeNull();
    unmount();

    render(<ModernCastsPage store={store} siteConfig={{}} casts={[]} />);
    fireEvent.click(screen.getByRole('button', { name: 'はい' }));
    expect(screen.queryByRole('button', { name: 'はい' })).not.toBeInTheDocument();
    expect(localStorage.getItem('kizuna_age_verified')).toBe('true');
  });
  it('出勤表は受け取った順にキャスト名と時間帯を出し、00:00 終了を 24:00 と書く', () => {
    render(
      <ModernSchedulePage
        store={store}
        siteConfig={{}}
        shifts={[
          { cast_id: '42', cast_name: 'B子', start_time: '20:00:00', end_time: '00:00:00' },
          { cast_id: '7', cast_name: 'A子', start_time: '18:00', end_time: '02:00' },
          { cast_id: '42', cast_name: 'B子', start_time: '03:00', end_time: '05:00' },
        ]}
      />
    );

    expect(screen.getByRole('heading', { level: 1, name: '出勤表' })).toBeInTheDocument();
    expect(screen.getAllByRole('heading', { level: 2 }).map(h => h.textContent)).toEqual([
      'B子',
      'A子',
      'B子',
    ]);
    expect(screen.getByText('本日 2人')).toBeInTheDocument();
    expect(screen.getByText('20:00–24:00')).toBeInTheDocument();
    expect(screen.getByText('18:00–02:00')).toBeInTheDocument();
    expect(hrefsOf('B子 20:00–24:00')).toEqual(['/casts/42']);
  });

  it('出勤が 0 件なら案内だけを出す', () => {
    render(<ModernSchedulePage store={store} siteConfig={{}} shifts={[]} />);

    expect(screen.getByText('本日の出勤情報はありません')).toBeInTheDocument();
    expect(
      screen.getByText('出勤情報は準備中です。最新の情報はお電話でお問い合わせください。')
    ).toBeInTheDocument();
  });

  it('料金は金額で終わる行を表に組み、それ以外の行も順序どおり欠かさず出す', () => {
    const pricing_description =
      'ご案内\nセット料金 60分 10,000円〜\n延長 30分 6,000円\n\nサービス料・消費税を別途申し受けます。\n指名料　¥3,000';
    const { container } = render(
      <ModernMenuPage store={store} siteConfig={{ pricing_description }} />
    );

    expect(screen.getByRole('heading', { level: 1, name: '料金' })).toBeInTheDocument();
    const cells = screen
      .getAllByRole('row')
      .slice(1)
      .map(row => Array.from(row.querySelectorAll('th,td'), cell => cell.textContent));
    expect(cells).toEqual([
      ['セット料金 60分', '10,000円〜'],
      ['延長 30分', '6,000円'],
      ['項目', '料金'],
      ['指名料', '¥3,000'],
    ]);
    const main = container.querySelector('main')?.textContent ?? '';
    const positions = [
      'ご案内',
      'セット料金',
      '延長',
      'サービス料・消費税を別途申し受けます。',
      '指名料',
    ].map(text => main.indexOf(text));
    expect(positions).toEqual([...positions].sort((a, b) => a - b));
    expect(positions).not.toContain(-1);
  });

  it('料金は「無料」と割合で終わる行も表の行にし、表を途中で割らない', () => {
    render(
      <ModernMenuPage
        store={store}
        siteConfig={{
          pricing_description: 'セット 60分 10,000円\n入会金 無料\n深夜料金 20%増\n指名料 3,000円',
        }}
      />
    );

    expect(screen.getAllByRole('table')).toHaveLength(1);
    expect(screen.getAllByRole('rowheader').map(cell => cell.textContent)).toEqual([
      'セット 60分',
      '入会金',
      '深夜料金',
      '指名料',
    ]);
  });

  it('料金が金額を含まない文章だけなら、表を作らず書いたとおりに出す', () => {
    render(
      <ModernMenuPage
        store={store}
        siteConfig={{ pricing_description: '料金はお電話でご案内します。\n営業は 20時から' }}
      />
    );

    expect(screen.queryByRole('table')).not.toBeInTheDocument();
    expect(screen.getByText(/料金はお電話でご案内します。/).textContent).toBe(
      '料金はお電話でご案内します。\n営業は 20時から'
    );
  });

  it('料金が未設定なら準備中の案内を出す', () => {
    render(<ModernMenuPage store={store} siteConfig={{}} />);

    expect(screen.getByText('料金情報は準備中です')).toBeInTheDocument();
  });

  it('店舗情報は設定された項目だけを出し、電話は発信のリンクにする', () => {
    render(
      <ModernAboutPage
        store={store}
        siteConfig={{
          address: '東京都中央区銀座 1-1',
          phone: '03-0000-0000',
          access_note: '銀座駅より徒歩 3 分',
        }}
      />
    );

    expect(
      screen.getByRole('heading', { level: 1, name: '店舗情報・アクセス' })
    ).toBeInTheDocument();
    expect(
      screen.getAllByRole('term').map(term => [term.textContent, term.nextSibling?.textContent])
    ).toEqual([
      ['住所', '東京都中央区銀座 1-1'],
      ['電話', '03-0000-0000'],
      ['アクセス', '銀座駅より徒歩 3 分'],
    ]);
    expect(hrefsOf('03-0000-0000')).toEqual(['tel:03-0000-0000']);
  });

  it('口コミは本文を改行のまま、表示名（無ければ匿名）と受領日を出す', () => {
    render(
      <ModernReviewsPage
        store={store}
        siteConfig={{}}
        page={0}
        reviews={pageOf([
          { display_name: '常連', body: '一行目\n二行目', received_on: '2026-01-02' },
          { display_name: null, body: '名無しの感想', received_on: '2026-10-15' },
        ])}
      />
    );

    expect(screen.getByRole('heading', { level: 1, name: '口コミ' })).toBeInTheDocument();
    const [first, second] = screen.getAllByRole('article');
    expect(within(first).getByText(/一行目/).textContent).toBe('一行目\n二行目');
    expect(within(first).getByText('常連')).toBeInTheDocument();
    expect(within(first).getByText('2026年1月2日')).toHaveAttribute('datetime', '2026-01-02');
    expect(within(second).getByText('匿名')).toBeInTheDocument();
    expect(screen.queryByRole('navigation', { name: 'ページネーション' })).not.toBeInTheDocument();
  });

  it('口コミの取得の失敗・0 件・範囲外のページを言い分ける', () => {
    const { rerender } = render(
      <ModernReviewsPage store={store} siteConfig={{}} page={1} reviews={null} />
    );
    expect(screen.getByRole('alert')).toHaveTextContent('口コミを読み込めませんでした');
    expect(hrefsOf('再読み込み')).toEqual(['/reviews?page=2']);

    rerender(<ModernReviewsPage store={store} siteConfig={{}} page={0} reviews={pageOf([])} />);
    expect(screen.getByText('掲載中の口コミはありません')).toBeInTheDocument();
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();

    rerender(
      <ModernReviewsPage
        store={store}
        siteConfig={{}}
        page={1}
        reviews={pageOf([], { number: 1, total_elements: 10 })}
      />
    );
    expect(screen.getByText('このページに口コミはありません')).toBeInTheDocument();
    expect(hrefsOf('最初のページへ')).toEqual(['/reviews']);
  });

  it('ページ送りは件数と範囲、現在のページ、前後のリンクを出し、端では押せない形にする', () => {
    const content = Array.from({ length: 10 }, (_, i) => ({
      display_name: `客${i}`,
      body: `本文${i}`,
      received_on: '2026-10-01',
    }));
    const { rerender } = render(
      <ModernReviewsPage
        store={store}
        siteConfig={{}}
        page={1}
        reviews={pageOf(content, { number: 1, total_pages: 3, total_elements: 25 })}
      />
    );

    expect(screen.getByText('25 件中 11-20 を表示')).toBeInTheDocument();
    const nav = screen.getByRole('navigation', { name: 'ページネーション' });
    expect(within(nav).getByRole('link', { name: '2' })).toHaveAttribute('aria-current', 'page');
    expect(within(nav).getByRole('link', { name: '1' })).toHaveAttribute('href', '/reviews');
    expect(within(nav).getByRole('link', { name: '前へ' })).toHaveAttribute('href', '/reviews');
    expect(within(nav).getByRole('link', { name: '次へ' })).toHaveAttribute(
      'href',
      '/reviews?page=3'
    );

    rerender(
      <ModernReviewsPage
        store={store}
        siteConfig={{}}
        page={2}
        reviews={pageOf(content.slice(0, 5), { number: 2, total_pages: 3, total_elements: 25 })}
      />
    );
    expect(screen.getByText('25 件中 21-25 を表示')).toBeInTheDocument();
    const lastNav = screen.getByRole('navigation', { name: 'ページネーション' });
    expect(within(lastNav).queryByRole('link', { name: '次へ' })).not.toBeInTheDocument();
    expect(within(lastNav).getByText('次へ')).toHaveAttribute('aria-disabled', 'true');
  });

  it('お知らせ一覧は表題のリンクに表紙と公開日を収め、表紙の無い記事は画像を出さない', () => {
    render(
      <ModernNewsPage
        store={store}
        siteConfig={{}}
        page={0}
        articles={pageOf(
          [
            {
              id: 'a1',
              title: '秋のイベント',
              cover_image_url: 'https://img.example/a1.png',
              publish_from: '2026-10-31T15:30:00Z',
            },
            { id: 'a2', title: '営業時間の変更', publish_from: null },
          ],
          { total_pages: 2, total_elements: 12 }
        )}
      />
    );

    expect(screen.getByRole('heading', { level: 1, name: 'お知らせ' })).toBeInTheDocument();
    const first = screen.getByRole('link', { name: /秋のイベント/ });
    expect(first).toHaveAttribute('href', '/news/a1');
    expect(first.querySelector('img')).toHaveAttribute('src', 'https://img.example/a1.png');
    expect(within(first).getByText('2026年11月1日')).toBeInTheDocument();
    const second = screen.getByRole('link', { name: /営業時間の変更/ });
    expect(second.querySelector('img')).toBeNull();
    expect(second.querySelector('time')).toBeNull();
    expect(screen.getByRole('navigation', { name: 'ページネーション' })).toBeInTheDocument();
  });

  it('お知らせの取得の失敗・0 件・範囲外のページを言い分ける', () => {
    const { rerender } = render(
      <ModernNewsPage store={store} siteConfig={{}} page={2} articles={null} />
    );
    expect(screen.getByRole('alert')).toHaveTextContent('お知らせを読み込めませんでした');
    expect(hrefsOf('再読み込み')).toEqual(['/news?page=3']);

    rerender(<ModernNewsPage store={store} siteConfig={{}} page={0} articles={pageOf([])} />);
    expect(screen.getByText('掲載中のお知らせはありません')).toBeInTheDocument();

    rerender(
      <ModernNewsPage
        store={store}
        siteConfig={{}}
        page={3}
        articles={pageOf([], { number: 3, total_elements: 4 })}
      />
    );
    expect(screen.getByText('このページにお知らせはありません')).toBeInTheDocument();
    expect(hrefsOf('最初のページへ')).toEqual(['/news']);
  });

  it('記事は表題・公開日・表紙と、記法を解釈しない本文を出す', () => {
    render(
      <ModernNewsDetailPage
        store={store}
        siteConfig={{}}
        article={{
          id: 'a1',
          title: '秋のイベント',
          cover_image_url: 'https://img.example/a1.png',
          publish_from: '2026-10-31T15:30:00Z',
          body: '一行目\n<b>二行目</b>',
        }}
      />
    );

    expect(screen.getByRole('heading', { level: 1, name: '秋のイベント' })).toBeInTheDocument();
    expect(screen.getByText('2026年11月1日')).toBeInTheDocument();
    expect(document.querySelector('img[src="https://img.example/a1.png"]')).not.toBeNull();
    expect(screen.getByText(/一行目/).textContent).toBe('一行目\n<b>二行目</b>');
    expect(hrefsOf('お知らせ一覧へ')).toEqual(['/news']);
  });

  describe('予約フォーム', () => {
    const mockedApi = guestOrderApplicationApi as jest.Mocked<typeof guestOrderApplicationApi>;

    beforeEach(() => {
      mockedApi.consent.mockResolvedValue({
        version: 'v1',
        business_text: '業務連絡に同意します',
        marketing_text: 'お知らせを受け取ります',
      });
      mockedApi.request.mockReset();
    });

    const submit = () =>
      fireEvent.click(screen.getByRole('button', { name: 'この内容で予約を希望する' }));

    it('入力の誤りを欄の傍に出し、欄と結び付け、送信しない', async () => {
      render(<ModernReservationPage store={store} siteConfig={{}} />);
      expect(screen.getByRole('heading', { level: 1, name: 'ご予約' })).toBeInTheDocument();
      await screen.findByRole('checkbox', { name: /業務連絡に同意します/ });

      submit();

      const nameError = await screen.findByText('お名前をご入力ください');
      const name = screen.getByLabelText('お名前');
      expect(name).toHaveAttribute('aria-invalid', 'true');
      expect(name).toBeRequired();
      expect(name).toHaveAccessibleDescription(nameError.textContent ?? '');
      expect(screen.getByLabelText('お電話番号')).toHaveAccessibleDescription(
        '電話・メール・LINEのいずれかをご入力ください'
      );
      expect(screen.getByLabelText('ご希望日')).toHaveAccessibleDescription(
        'ご希望日を選択してください'
      );
      expect(screen.getByText('今回の予約に関する業務連絡への同意が必要です')).toBeInTheDocument();
      expect(mockedApi.request).not.toHaveBeenCalled();
    });

    it('正しい入力を店舗の id と同意の版とともに送り、受付番号と未確定の案内を出す', async () => {
      mockedApi.request.mockResolvedValue({ id: 'app-9' });
      render(<ModernReservationPage store={store} siteConfig={{}} />);
      const consent = await screen.findByRole('checkbox', { name: /業務連絡に同意します/ });

      fireEvent.change(screen.getByLabelText('お名前'), { target: { value: 'ゲスト花子' } });
      fireEvent.change(screen.getByLabelText('メール'), { target: { value: 'guest@example.com' } });
      fireEvent.change(screen.getByLabelText('LINE ID'), { target: { value: 'guest-line' } });
      fireEvent.change(screen.getByLabelText('ご希望日'), { target: { value: '2026-11-20' } });
      fireEvent.change(screen.getByLabelText('ご希望時刻'), { target: { value: '21:00' } });
      fireEvent.change(screen.getByLabelText('ご人数'), { target: { value: '2' } });
      fireEvent.change(screen.getByLabelText('ご要望（任意）'), { target: { value: '窓側' } });
      fireEvent.click(consent);
      submit();

      expect(await screen.findByText('受付番号: app-9')).toBeInTheDocument();
      expect(screen.getByRole('status')).toHaveFocus();
      expect(screen.getByText(/この時点ではまだ予約は確定しておりません/)).toBeInTheDocument();
      expect(mockedApi.request).toHaveBeenCalledWith('7', {
        contact_consent: { version: 'v1', business_allowed: true, marketing_allowed: false },
        business_date: '2026-11-20',
        arrival_scheduled_start_time: '21:00',
        pax: 2,
        contact_snapshot: {
          name: 'ゲスト花子',
          phone_number: '',
          email: 'guest@example.com',
          line_id: 'guest-line',
        },
        remarks: '窓側',
      });
    });

    it('送信の失敗を知らせ、入力した内容を残す', async () => {
      mockedApi.request.mockRejectedValue(new Error('down'));
      render(<ModernReservationPage store={store} siteConfig={{}} />);
      const consent = await screen.findByRole('checkbox', { name: /業務連絡に同意します/ });

      fireEvent.change(screen.getByLabelText('お名前'), { target: { value: 'ゲスト花子' } });
      fireEvent.change(screen.getByLabelText('お電話番号'), { target: { value: '09000000000' } });
      fireEvent.change(screen.getByLabelText('ご希望日'), { target: { value: '2026-11-20' } });
      fireEvent.click(consent);
      submit();

      expect(await screen.findByRole('alert')).toHaveTextContent(
        'ご予約の送信に失敗しました。時間をおいてお試しください'
      );
      expect(screen.getByLabelText('お名前')).toHaveValue('ゲスト花子');
    });

    it('同意文面の取得に失敗したら再試行を出し、押すと取り直す', async () => {
      mockedApi.consent.mockRejectedValueOnce(new Error('down'));
      render(<ModernReservationPage store={store} siteConfig={{}} />);

      fireEvent.click(await screen.findByRole('button', { name: '再試行' }));

      expect(
        await screen.findByRole('checkbox', { name: /業務連絡に同意します/ })
      ).toBeInTheDocument();
    });
  });
});
