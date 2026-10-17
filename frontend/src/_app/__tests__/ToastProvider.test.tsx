import { act, fireEvent, render, screen, waitFor } from '@testing-library/react';

import { notify } from '@/shared/notify';

import { ToastProvider } from '../ToastProvider';

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

  it('文言は dialog の名前になるが、ページの見出し階層には入らない', () => {
    const toast = show(() => notify.error('保存に失敗しました'));

    // 名前は届く（`aria-labelledby` の指す先が文言）。
    expect(toast).toHaveAccessibleName('保存に失敗しました');
    // それでいて見出しではない——通知はページの節ではないため。
    expect(screen.queryByRole('heading')).not.toBeInTheDocument();
  });

  it('段の記号は潰れ防止の shrink-0 を持つ', () => {
    const toast = show(() => notify.error('保存に失敗しました'));

    // 潰れは実ブラウザでしか描かれず jsdom では見えない。だから指定そのものを
    // 記号の一部として固定する（機序は toast.tsx の注記が持つ）。
    const icon = toast.querySelector('[data-slot="toast-icon"]');
    expect(icon).toHaveClass('shrink-0');
  });

  it('本体の焦点環は段の地の上へ内向きに描かれる', () => {
    const toast = show(() => notify.error('保存に失敗しました'));

    // <kbd>F6</kbd> のあと最初に焦点が載るのがこの本体。環は既定で外側＝下の画面の上に
    // 描かれるので、内向きにしないと測れない色の上に乗る（機序は toast.tsx の注記が持つ）。
    // 焦点の描画自体は jsdom では起きないため、指定を固定する。
    expect(toast).toHaveClass('focus-visible:ring-inset', 'focus-visible:ring-current');
  });

  it('ビューポートは焦点の輪郭をブラウザ既定に委ねる', () => {
    render(<ToastProvider />);
    act(() => notify.error('保存に失敗しました'));

    // 器は透明で、環が乗る地は下にある画面の中身＝書く時点では未知の色。自前の環を置けない
    // ので二色で描かれるブラウザ既定に任せる。`outline-none` を足すと F6 が画面上で何も
    // 起こさなくなり、キーが効いていないように見える。
    const viewport = document.querySelector('[data-slot="toast-viewport"]');
    expect(viewport).not.toHaveClass('outline-none');
  });

  it('閉じるボタンで閉じられる——時間切れを待たずに済む唯一の手段', async () => {
    const toast = show(() => notify.error('保存に失敗しました'));

    // ロールでは引けない。ライブラリはこのボタンへ `aria-hidden` を当て、ビューポートが
    // hover か focus を得たときだけ外す（通知そのものは live region が読み上げるので、
    // 操作子は近づいたときだけ支援技術へ現れればよい、という設計）。
    fireEvent.click(toast.querySelector('[data-slot="toast-close"]')!);

    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
  });
});
