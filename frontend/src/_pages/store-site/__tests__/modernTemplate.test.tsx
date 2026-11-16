import { fireEvent, render, screen, within } from '@testing-library/react';
import { STORE_SITE_NAV_PAGES } from '../model/navigation';
import type { SiteConfig } from '../model/types';
import ModernCastDetailPage from '../templates/modern/cast-detail';
import ModernCastsPage from '../templates/modern/casts';
import ModernTopPage from '../templates/modern/page';

jest.mock('next/navigation', () => ({ usePathname: () => '/' }));
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
});
