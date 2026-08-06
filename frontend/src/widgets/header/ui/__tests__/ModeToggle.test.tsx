import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { ModeToggle } from '../ModeToggle';

// next-themes 本体（永続化と OS 追随）はライブラリの責務で、根 layout の props 錨が
// 差し替えられていないことを保証する。ここで検証するのは本 UI が持つ配線、
// すなわち「どの項目がどの値で setTheme を呼ぶか」に限る。
const mockSetTheme = jest.fn();
const mockTheme = { current: 'system' as string | undefined };
jest.mock('next-themes', () => ({
  useTheme: () => ({ theme: mockTheme.current, setTheme: mockSetTheme }),
}));

function openModeMenu() {
  fireEvent.click(screen.getByRole('button', { name: '表示モード' }));
}

describe('ModeToggle', () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  it('切替はキーボードだけで開ける', async () => {
    render(<ModeToggle />);
    openModeMenu();

    expect(await screen.findByRole('menuitemradio', { name: 'ライト' })).toBeInTheDocument();
    expect(screen.getByRole('menuitemradio', { name: 'ダーク' })).toBeInTheDocument();
    expect(screen.getByRole('menuitemradio', { name: 'システム' })).toBeInTheDocument();
  });

  it.each([
    ['ライト', 'light'],
    ['ダーク', 'dark'],
    ['システム', 'system'],
  ])('%s を選ぶと setTheme に %s を渡す', async (label, value) => {
    render(<ModeToggle />);
    openModeMenu();
    fireEvent.click(await screen.findByRole('menuitemradio', { name: label }));

    expect(mockSetTheme).toHaveBeenCalledWith(value);
  });

  it('選ぶとメニューは閉じる', async () => {
    // ライブラリ既定の radio 項目は選んでも開いたまま（複数切り替え向け）。開けっぱなしだと
    // 覆いが残り、背後の画面が押せなくなる。
    render(<ModeToggle />);
    openModeMenu();
    fireEvent.click(await screen.findByRole('menuitemradio', { name: 'ダーク' }));

    await waitFor(() =>
      expect(screen.queryByRole('menuitemradio', { name: 'ダーク' })).not.toBeInTheDocument()
    );
  });

  it('表示は現在値ではなく .dark の有無で決まる（初回フレームから確定する）', () => {
    const { container } = render(<ModeToggle />);

    // 明側アイコンは既定で可視・ダーク時に隠れ、暗側はその逆。
    // どちらも常に描かれるので、サーバ描画と初回フレームが一致する。
    expect(container.querySelector('.dark\\:hidden')).toBeInTheDocument();
    expect(container.querySelector('.hidden.dark\\:block')).toBeInTheDocument();
  });

  it('現在の設定が選択中として示される', async () => {
    mockTheme.current = 'system';

    render(<ModeToggle />);
    openModeMenu();

    expect(await screen.findByRole('menuitemradio', { name: 'システム' })).toHaveAttribute(
      'aria-checked',
      'true'
    );
    expect(screen.getByRole('menuitemradio', { name: 'ライト' })).toHaveAttribute(
      'aria-checked',
      'false'
    );
  });
});
