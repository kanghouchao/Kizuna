import { render, screen, within } from '@testing-library/react';
import { HomeIcon, CalendarDaysIcon, CircleUserRoundIcon } from 'lucide-react';
import { RegionError } from '@/shared/ui';
import { PortalShell, type PortalScreens, type PortalTab } from '../PortalShell';

let mockPathname = '/p';
jest.mock('next/navigation', () => ({
  usePathname: () => mockPathname,
}));

const TABS: readonly PortalTab[] = [
  { href: '/p', label: 'ホーム', icon: HomeIcon },
  {
    href: '/p/schedule',
    label: 'スケジュール',
    icon: CalendarDaysIcon,
    segments: [
      { href: '/p/schedule', label: '出勤予定' },
      { href: '/p/requests', label: '希望提出' },
    ],
  },
  { href: '/p/account', label: 'アカウント', icon: CircleUserRoundIcon },
];

const SCREENS: PortalScreens = {
  '/p': { title: 'ホーム', tab: '/p' },
  '/p/schedule': { title: 'スケジュール', tab: '/p/schedule' },
  '/p/requests': { title: 'スケジュール', tab: '/p/schedule' },
  '/p/account': { title: 'アカウント', tab: '/p/account' },
  '/p/terms': { title: 'サービス条件', back: '/p/account' },
  '/p/schedule/[day]': { title: '一日の予定', back: '/p/schedule' },
};

function renderShell(props: Partial<Parameters<typeof PortalShell>[0]> = {}) {
  return render(
    <PortalShell tier="cast" tabs={TABS} screens={SCREENS} {...props}>
      <p>子要素</p>
    </PortalShell>
  );
}

function tabBar() {
  return screen.getByRole('navigation', { name: 'メニュー' });
}

describe('PortalShell', () => {
  beforeEach(() => {
    mockPathname = '/p';
  });

  it('タブの画面では画面名を見出しに出し、戻る導線は出さずに下部タブを出す', () => {
    mockPathname = '/p/account';
    renderShell();

    expect(screen.getByRole('heading', { level: 1, name: 'アカウント' })).toBeInTheDocument();
    expect(screen.queryByRole('link', { name: '戻る' })).not.toBeInTheDocument();
    expect(screen.getByText('子要素')).toBeInTheDocument();
    const links = within(tabBar()).getAllByRole('link');
    expect(links.map(link => link.textContent)).toEqual(['ホーム', 'スケジュール', 'アカウント']);
    expect(within(tabBar()).getByRole('link', { name: 'アカウント' })).toHaveAttribute(
      'aria-current',
      'page'
    );
    expect(within(tabBar()).getByRole('link', { name: 'ホーム' })).not.toHaveAttribute(
      'aria-current'
    );
  });

  it('下位画面では戻る導線を出し、下部タブを出さない', () => {
    mockPathname = '/p/terms/';
    renderShell();

    expect(screen.getByRole('heading', { level: 1, name: 'サービス条件' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: '戻る' })).toHaveAttribute('href', '/p/account');
    expect(screen.queryByRole('navigation', { name: 'メニュー' })).not.toBeInTheDocument();
    expect(screen.getByText('子要素')).toBeInTheDocument();
  });

  it('[名前] のキーは URL の一つの区切りに当て、下位画面として戻り先を出す', () => {
    mockPathname = '/p/schedule/2026-11-05/';
    renderShell();

    expect(screen.getByRole('heading', { level: 1, name: '一日の予定' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: '戻る' })).toHaveAttribute('href', '/p/schedule');
    expect(screen.queryByRole('navigation', { name: 'メニュー' })).not.toBeInTheDocument();
  });

  it('[名前] のキーは区切りの数が違う URL には当てない', () => {
    mockPathname = '/p/schedule/2026-11-05/extra';
    renderShell();

    expect(screen.queryByRole('heading', { level: 1, name: '一日の予定' })).not.toBeInTheDocument();
  });

  it('ホームのタブは前方一致させず、ホームの画面でだけ選択状態にする', () => {
    mockPathname = '/p/schedule';
    renderShell();

    expect(within(tabBar()).getByRole('link', { name: 'ホーム' })).not.toHaveAttribute(
      'aria-current'
    );
  });

  it.each(['/p/schedule', '/p/requests/'])(
    'タブの中の切替のどちらの画面 %s でも同じタブを選択状態にし、切替の側は今の画面を選ぶ',
    path => {
      mockPathname = path;
      renderShell();

      expect(within(tabBar()).getByRole('link', { name: 'スケジュール' })).toHaveAttribute(
        'aria-current',
        'page'
      );
      const segments = screen.getByRole('navigation', { name: 'スケジュールの切替' });
      const selected = within(segments)
        .getAllByRole('link')
        .filter(link => link.getAttribute('aria-current') === 'page');
      expect(selected).toHaveLength(1);
      expect(selected[0]).toHaveAttribute('href', path.replace(/\/$/, ''));
    }
  );

  it('切替を持たないタブの画面には切替を出さない', () => {
    mockPathname = '/p/account';
    renderShell();

    expect(screen.queryByRole('navigation', { name: /の切替$/ })).not.toBeInTheDocument();
  });

  it('件数バッジは 1 件以上のタブにだけ出し、0 件のタブの名前は変えない', () => {
    renderShell({ badges: { '/p': 3, '/p/account': 0 } });

    expect(within(tabBar()).getByRole('link', { name: 'ホーム 3件' })).toBeInTheDocument();
    expect(within(tabBar()).getByRole('link', { name: 'アカウント' })).toBeInTheDocument();
  });

  it('案内は題名帯の後、内容の前に置く', () => {
    mockPathname = '/p/account';
    renderShell({ notice: <p>案内</p> });

    const heading = screen.getByRole('heading', { level: 1 });
    const notice = screen.getByText('案内');
    expect(heading.compareDocumentPosition(notice) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
    expect(
      notice.compareDocumentPosition(screen.getByText('子要素')) & Node.DOCUMENT_POSITION_FOLLOWING
    ).toBeTruthy();
  });

  it('内容の中の共有部品へ段を伝え、領域内エラーを段の大きさで出す', () => {
    render(
      <PortalShell tier="member" tabs={TABS} screens={SCREENS}>
        <RegionError message="読めませんでした。" onRetry={() => {}} />
      </PortalShell>
    );

    const region = within(screen.getByRole('main')).getByRole('alert');
    expect(within(region).getByRole('button', { name: '再試行' })).toHaveClass(
      'min-h-12',
      'text-lg'
    );
  });
});
