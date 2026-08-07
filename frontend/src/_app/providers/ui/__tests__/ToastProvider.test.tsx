import { act, fireEvent, render, screen, waitFor } from '@testing-library/react';

import { notify } from '@/shared/notify';

import { ToastProvider, toastOptions } from '../ToastProvider';

const show = (emit: () => void) => {
  render(<ToastProvider />);
  act(emit);
  return screen.getByRole('dialog');
};

describe('ToastProvider', () => {
  it('語義層が出した通知が描かれ、段が data-type に載る（配色 class が当たる面）', () => {
    expect(show(() => notify.success('保存しました'))).toHaveAttribute('data-type', 'success');
  });

  it('warning は重大度を前置語として持つ——記号は aria-hidden で読み上げに届かない', () => {
    const toast = show(() => notify.warning('他の担当者が先に更新しました'));

    expect(toast).toHaveAttribute('data-type', 'warning');
    expect(toast).toHaveTextContent('警告：他の担当者が先に更新しました');
  });

  it('段の記号は潰れ防止の shrink-0 を持つ', () => {
    const toast = show(() => notify.error('保存に失敗しました'));

    // 潰れは実ブラウザでしか描かれず jsdom では見えない。だから指定そのものを
    // 記号の一部として固定する（機序は toast.tsx の注記が持つ）。
    const icon = toast.querySelector('[data-slot="toast-icon"]');
    expect(icon).toHaveClass('shrink-0');
  });

  it('閉じるボタンで閉じられる——時間切れを待たずに済む唯一の手段', async () => {
    const toast = show(() => notify.error('保存に失敗しました'));

    // ロールでは引けない。ライブラリはこのボタンへ `aria-hidden` を当て、ビューポートが
    // hover か focus を得たときだけ外す（通知そのものは live region が読み上げるので、
    // 操作子は近づいたときだけ支援技術へ現れればよい、という設計）。
    fireEvent.click(toast.querySelector('[data-slot="toast-close"]')!);

    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
  });

  it('語義層へ移り切っていない呼び出しの姿も規範値に揃っている', () => {
    // 旧ライブラリを直接呼ぶ箇所が残る間だけの暫定。移行が終われば丸ごと消える。
    expect(toastOptions.success?.duration).toBe(3000);
    expect(toastOptions.error?.duration).toBe(5000);
    // 既定のアイコン配色は図形閾 3:1 に届かず、どちらの色も測定外。形は変えず色だけ移す。
    expect(toastOptions.success?.iconTheme).toEqual({
      primary: 'transparent',
      secondary: 'var(--success-foreground)',
    });
    expect(toastOptions.error?.iconTheme).toEqual({
      primary: 'transparent',
      secondary: 'var(--destructive-foreground)',
    });
  });
});
