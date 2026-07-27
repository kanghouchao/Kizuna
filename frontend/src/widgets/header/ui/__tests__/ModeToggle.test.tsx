import { render, screen, fireEvent } from '@testing-library/react';
import { ModeToggle } from '../ModeToggle';

// next-themes 本体（永続化と OS 追随）はライブラリの責務で、根 layout の props 錨が
// 差し替えられていないことを保証する。ここで検証するのは本 UI が持つ配線、
// すなわち「どの項目がどの値で setTheme を呼ぶか」に限る。
const mockSetTheme = jest.fn();
jest.mock('next-themes', () => ({
  useTheme: () => ({ setTheme: mockSetTheme }),
}));

// Radix のトリガーは pointerdown/キー入力で開く。jsdom の fireEvent.click は
// pointerdown を合成しないため、キーボードでメニューを開く。
function openModeMenu() {
  fireEvent.keyDown(screen.getByRole('button', { name: '表示モード' }), { key: 'Enter' });
}

describe('ModeToggle', () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  it('切替はキーボードだけで開ける', async () => {
    render(<ModeToggle />);
    openModeMenu();

    expect(await screen.findByRole('menuitem', { name: 'ライト' })).toBeInTheDocument();
    expect(screen.getByRole('menuitem', { name: 'ダーク' })).toBeInTheDocument();
    expect(screen.getByRole('menuitem', { name: 'システム' })).toBeInTheDocument();
  });

  it.each([
    ['ライト', 'light'],
    ['ダーク', 'dark'],
    ['システム', 'system'],
  ])('%s を選ぶと setTheme に %s を渡す', async (label, value) => {
    render(<ModeToggle />);
    openModeMenu();
    fireEvent.click(await screen.findByRole('menuitem', { name: label }));

    expect(mockSetTheme).toHaveBeenCalledWith(value);
  });

  it('表示は現在値ではなく .dark の有無で決まる（初回フレームから確定する）', () => {
    const { container } = render(<ModeToggle />);

    // 明側アイコンは既定で可視・ダーク時に隠れ、暗側はその逆。
    // どちらも常に描かれるので、サーバ描画と初回フレームが一致する。
    expect(container.querySelector('.dark\\:hidden')).toBeInTheDocument();
    expect(container.querySelector('.hidden.dark\\:block')).toBeInTheDocument();
  });
});
