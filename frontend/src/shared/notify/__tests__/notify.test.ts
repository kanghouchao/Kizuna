import { notify, toastManager } from '@/shared/notify';

// 呼び出し側はこの層を mock するため、toast ライブラリを観測するのはここだけ。
// duration は描かれた DOM に現れないので、発行時の引数で固定する。
const add = jest.spyOn(toastManager, 'add').mockReturnValue('toast-id');

describe('notify', () => {
  beforeEach(() => add.mockClear());

  it('success は 3000ms で、文言をそのまま渡す', () => {
    notify.success('保存しました');

    expect(add).toHaveBeenCalledWith({
      type: 'success',
      title: '保存しました',
      timeout: 3000,
    });
  });

  it('error は 5000ms で、前置語は付かない', () => {
    notify.error('保存に失敗しました');

    expect(add).toHaveBeenCalledWith({
      type: 'error',
      title: '保存に失敗しました',
      timeout: 5000,
    });
  });

  it('warning は 10000ms で、重大度を文言へ入れる——呼び出し側は事実の文だけを渡す', () => {
    notify.warning('他の担当者が先に更新しました');

    expect(add).toHaveBeenCalledWith({
      type: 'warning',
      title: '警告：他の担当者が先に更新しました',
      timeout: 10000,
    });
  });

  it('段の指定以外は何も渡さない——姿は全て描画側が型から引く', () => {
    notify.success('保存しました');
    notify.error('保存に失敗しました');
    notify.warning('他の担当者が先に更新しました');

    for (const [options] of add.mock.calls) {
      expect(Object.keys(options).sort()).toEqual(['timeout', 'title', 'type']);
    }
  });
});
