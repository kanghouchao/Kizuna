import { fireEvent, render, screen, within } from '@testing-library/react';
import { PortalTierProvider } from '../portal-tier';
import { RegionError } from '../region-error';

describe('RegionError', () => {
  it('文言と再試行ボタンを同じ容器の中に描き、押下でハンドラを呼ぶこと', () => {
    const onRetry = jest.fn();
    render(<RegionError message="予約申請を取得できませんでした。" onRetry={onRetry} />);

    // 告知は role="alert" の暗黙の aria-atomic で容器ごと読まれる。ボタンが容器の外へ出ると
    // 「再試行できる」ことが文言と一緒に届かなくなるため、包含関係まで固定する。
    const region = screen.getByRole('alert');
    expect(within(region).getByText('予約申請を取得できませんでした。')).toBeInTheDocument();

    const retry = within(region).getByRole('button', { name: '再試行' });
    // Button は素の <button> を描くため既定は submit。①の領域はフォーム内にも立つ（Select の選択肢）。
    expect(retry).toHaveAttribute('type', 'button');

    fireEvent.click(retry);
    expect(onRetry).toHaveBeenCalledTimes(1);
  });

  it('再試行ハンドラが無ければ再試行を出さず、代替導線だけを出すこと', () => {
    render(
      <RegionError
        className="mt-4"
        message="この店舗は見つかりませんでした。"
        fallback={{ href: '/admin/stores', label: '店舗一覧へ' }}
      />
    );

    const region = screen.getByRole('alert');
    expect(within(region).queryByRole('button')).not.toBeInTheDocument();
    expect(within(region).getByRole('link', { name: '店舗一覧へ' })).toHaveAttribute(
      'href',
      '/admin/stores'
    );
    // 外側の配置は呼び出し側だけが決める。
    expect(region).toHaveClass('mt-4');
  });

  it('外枠の外（管理画面）では小さい文言と sm のボタンのままにすること', () => {
    render(<RegionError message="読めませんでした。" onRetry={() => {}} />);

    expect(within(screen.getByRole('alert')).getByText('読めませんでした。')).toHaveClass(
      'text-sm'
    );
    const retry = screen.getByRole('button', { name: '再試行' });
    expect(retry).toHaveClass('h-8');
    expect(retry).not.toHaveClass('min-h-11');
    expect(retry).not.toHaveClass('min-h-12');
  });

  it.each([
    ['member', 'text-lg', 'min-h-12'],
    ['cast', 'text-base', 'min-h-11'],
  ] as const)(
    '%s の外枠の中では文言とボタンをその段の本文と押せる領域に揃えること',
    (tier, body, target) => {
      render(
        <PortalTierProvider value={tier}>
          <RegionError message="読めませんでした。" onRetry={() => {}} />
          <RegionError
            message="見つかりませんでした。"
            fallback={{ href: '/p', label: '一覧へ' }}
          />
        </PortalTierProvider>
      );

      const [retryRegion, fallbackRegion] = screen.getAllByRole('alert');
      const message = within(retryRegion).getByText('読めませんでした。');
      expect(message).toHaveClass(body);
      expect(message).not.toHaveClass('text-sm');
      // Button の基底 text-sm は twMerge で段の本文に置き換わる。
      for (const control of [
        within(retryRegion).getByRole('button', { name: '再試行' }),
        within(fallbackRegion).getByRole('link', { name: '一覧へ' }),
      ]) {
        expect(control).toHaveClass(body, target);
        expect(control).not.toHaveClass('text-sm');
      }
    }
  );
});
