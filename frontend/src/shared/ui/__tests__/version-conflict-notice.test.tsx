import { fireEvent, render, screen } from '@testing-library/react';
import { LOAD_LATEST_LABEL, VersionConflictNotice } from '../version-conflict-notice';

describe('VersionConflictNotice', () => {
  it('止めている理由を alert で名乗り、読み込みのボタンで onLoad を呼ぶ', () => {
    const onLoad = jest.fn();
    render(<VersionConflictNotice busy={false} onLoad={onLoad} />);

    expect(screen.getByRole('alert')).toHaveTextContent('入力中の内容は置き換わります');
    fireEvent.click(screen.getByRole('button', { name: LOAD_LATEST_LABEL }));
    expect(onLoad).toHaveBeenCalledTimes(1);
  });

  it('書き込み中は読み込みを押せない', () => {
    render(<VersionConflictNotice busy onLoad={jest.fn()} />);

    expect(screen.getByRole('button', { name: LOAD_LATEST_LABEL })).toBeDisabled();
  });

  // 入力のたびに送ると、打鍵中の利用者を案内の位置へ引き戻す
  it('見える位置へ送るのは出たときの一度だけで、描き直しでは送らない', () => {
    // setupTests の代用は全テストで共有の mock なので、ここまでの呼び出しを数えない
    const scrollIntoView = jest.spyOn(Element.prototype, 'scrollIntoView').mockClear();
    const { rerender } = render(<VersionConflictNotice busy={false} onLoad={jest.fn()} />);
    rerender(<VersionConflictNotice busy onLoad={jest.fn()} />);

    expect(scrollIntoView).toHaveBeenCalledTimes(1);
    expect(scrollIntoView).toHaveBeenCalledWith({ block: 'nearest' });
  });
});
