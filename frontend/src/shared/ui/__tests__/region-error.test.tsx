import { fireEvent, render, screen, within } from '@testing-library/react';
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
        fallback={{ href: '/platform/stores', label: '店舗一覧へ' }}
      />
    );

    const region = screen.getByRole('alert');
    expect(within(region).queryByRole('button')).not.toBeInTheDocument();
    expect(within(region).getByRole('link', { name: '店舗一覧へ' })).toHaveAttribute(
      'href',
      '/platform/stores'
    );
    // 外側の配置は呼び出し側だけが決める。
    expect(region).toHaveClass('mt-4');
  });
});
