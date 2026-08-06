import type { ReactElement } from 'react';
import { TriangleAlertIcon } from 'lucide-react';
import { toast } from 'react-hot-toast';
import { notify } from '@/shared/notify';

// 呼び出し側はこの層を mock するため、toast ライブラリを観測するのはここだけ。
jest.mock('react-hot-toast', () => {
  const toast = jest.fn();
  return { toast: Object.assign(toast, { success: jest.fn(), error: jest.fn() }) };
});

const mockedToast = toast as unknown as jest.Mock & {
  success: jest.Mock;
  error: jest.Mock;
};

/** warning が渡した素の toast() の第二引数 */
const warningOptions = () => mockedToast.mock.calls[0][1];

describe('notify', () => {
  beforeEach(() => jest.clearAllMocks());

  it('success は文言だけを型別 API へ渡す（duration も配色もプロバイダ側）', () => {
    notify.success('保存しました');

    expect(mockedToast.success).toHaveBeenCalledWith('保存しました');
  });

  it('error も文言だけ。前置語は付かない', () => {
    notify.error('保存に失敗しました');

    expect(mockedToast.error).toHaveBeenCalledWith('保存に失敗しました');
  });

  it('warning は素の toast() で出す（型別キーが無く、blank に姿を置かないため）', () => {
    notify.warning('他の担当者が先に更新しました');

    expect(mockedToast).toHaveBeenCalledTimes(1);
    expect(mockedToast.success).not.toHaveBeenCalled();
    expect(mockedToast.error).not.toHaveBeenCalled();
  });

  it('warning は重大度を文言へ入れる——呼び出し側は事実の文だけを渡す', () => {
    notify.warning('他の担当者が先に更新しました');

    expect(mockedToast.mock.calls[0][0]).toBe('警告：他の担当者が先に更新しました');
  });

  it('warning の duration は 10000ms（呼び出し側の値が最優先で解決される）', () => {
    notify.warning('他の担当者が先に更新しました');

    expect(warningOptions().duration).toBe(10000);
  });

  it('warning のアイコンは 20px の三角で、潰れ防止の shrink-0 を持つ', () => {
    notify.warning('他の担当者が先に更新しました');

    // 潰れは実ブラウザでしか描かれず jsdom では見えない。だから指定そのものを
    // アイコンの一部として固定する（機序は index.tsx の注記が持つ）。
    const icon = warningOptions().icon as ReactElement<{ size: number; className: string }>;
    expect(icon.type).toBe(TriangleAlertIcon);
    expect(icon.props.size).toBe(20);
    expect(icon.props.className).toBe('shrink-0');
  });

  it('warning の配色は背景と文字色だけを渡す（基底の borderRadius を残すため）', () => {
    notify.warning('他の担当者が先に更新しました');

    expect(warningOptions().style).toEqual({
      background: 'var(--warning)',
      color: 'var(--warning-foreground)',
    });
  });

  it('ARIA はどの段にも書かない（三段とも既定の polite のまま）', () => {
    notify.warning('他の担当者が先に更新しました');

    expect(warningOptions().ariaProps).toBeUndefined();
  });
});
