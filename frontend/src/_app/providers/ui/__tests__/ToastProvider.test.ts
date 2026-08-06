import { toastOptions } from '../ToastProvider';

describe('toastOptions', () => {
  it('success は規範の 3000ms、error は 5000ms', () => {
    expect(toastOptions.success?.duration).toBe(3000);
    expect(toastOptions.error?.duration).toBe(5000);
  });

  it('アイコンの配色を自前の token へ引き取る（ライブラリ既定はマトリクス外）', () => {
    // 是正は色だけで形は変えない（primary＝円の塗り、secondary＝線）。
    expect(toastOptions.success?.iconTheme).toEqual({
      primary: 'transparent',
      secondary: 'var(--success-foreground)',
    });
    expect(toastOptions.error?.iconTheme).toEqual({
      primary: 'transparent',
      secondary: 'var(--destructive-foreground)',
    });
  });

  it('三段目 warning はここに現れない（型別キーが無く、語義層が姿ごと持つ）', () => {
    // blank へ warning の姿を置くと、型無しの toast() が全てその段の姿を継ぐ＝
    // 重大度が偶然に獲得され得る。blank ブロックは意図的に空のまま。
    expect(toastOptions.blank).toBeUndefined();
    expect(JSON.stringify(toastOptions)).not.toContain('warning');
  });
});
