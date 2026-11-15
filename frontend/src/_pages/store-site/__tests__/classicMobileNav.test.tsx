import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import MobileNav from '../templates/classic/MobileNav';

jest.mock('../model/useMemberReservationUrl', () => ({
  useMemberReservationUrl: () => 'https://app.example/member/reservations/new?store=s',
}));

describe('classic の引き出し', () => {
  const renderNav = () => render(<MobileNav storeName="店舗A" current="/casts" />);

  it('閉じている間は、中の項目がどこにも無い', () => {
    renderNav();

    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    expect(screen.queryByRole('link', { name: 'キャスト' })).not.toBeInTheDocument();
    expect(screen.queryByRole('link', { name: 'ご予約' })).not.toBeInTheDocument();
  });

  it('開くと全ページと予約・ログインの入口が出て、現在地を示す', async () => {
    renderNav();

    fireEvent.click(screen.getByRole('button', { name: '品書き' }));

    const dialog = await screen.findByRole('dialog', { name: '品書き' });
    expect(dialog).toBeInTheDocument();
    for (const name of ['TOP', 'キャスト', '出勤表', 'お知らせ', '口コミ', '料金', '店舗情報']) {
      expect(screen.getByRole('link', { name })).toBeInTheDocument();
    }
    expect(screen.getByRole('link', { name: 'キャスト' })).toHaveAttribute('aria-current', 'page');
    expect(
      screen.getAllByRole('link', { name: 'ご予約' }).map(link => link.getAttribute('href'))
    ).toEqual(['/reservation', '/reservation']);
    expect(screen.getByRole('link', { name: '会員予約' })).toHaveAttribute(
      'href',
      'https://app.example/member/reservations/new?store=s'
    );
    expect(screen.getByRole('link', { name: 'ログイン' })).toHaveAttribute('href', '/auth/login');
  });

  it('Escape で閉じ、中の項目が消える', async () => {
    renderNav();
    fireEvent.click(screen.getByRole('button', { name: '品書き' }));
    const dialog = await screen.findByRole('dialog', { name: '品書き' });

    fireEvent.keyDown(dialog, { key: 'Escape' });

    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
    expect(screen.queryByRole('link', { name: 'キャスト' })).not.toBeInTheDocument();
  });

  it('項目を選ぶと閉じる', async () => {
    renderNav();
    fireEvent.click(screen.getByRole('button', { name: '品書き' }));

    const link = await screen.findByRole('link', { name: '料金' });
    // jsdom は遷移を実装していないので、リンクの既定の動作だけ止める
    link.addEventListener('click', event => event.preventDefault());
    fireEvent.click(link);

    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
  });
});
