import { act, renderHook } from '@testing-library/react';
import { notify } from '@/shared/notify';
import { useDeleteAction } from '@/shared/lib';

jest.mock('@/shared/notify', () => ({
  notify: { success: jest.fn(), error: jest.fn(), warning: jest.fn() },
}));

interface Row {
  id: string;
}

const setup = (remove: (target: Row) => Promise<void>, reload = jest.fn()) => {
  const rendered = renderHook(() =>
    useDeleteAction<Row>({
      remove,
      successMessage: '削除しました',
      errorMessage: '削除に失敗しました',
      reload,
    })
  );
  return { ...rendered, reload };
};

describe('useDeleteAction', () => {
  beforeEach(() => jest.clearAllMocks());

  it('cancel は確認を閉じ、退出中の表示用に対象を保持する', async () => {
    const remove = jest.fn(async () => {});
    const { result } = setup(remove);

    expect(result.current.target).toBeNull();
    act(() => result.current.ask({ id: '1' }));
    expect(result.current.target).toEqual({ id: '1' });
    expect(result.current.open).toBe(true);
    act(() => result.current.cancel());
    expect(result.current.open).toBe(false);
    expect(result.current.target).toEqual({ id: '1' });
    await act(async () => result.current.confirm());
    expect(remove).not.toHaveBeenCalled();
    act(() => result.current.ask({ id: '2' }));
    expect(result.current.open).toBe(true);
    expect(result.current.target).toEqual({ id: '2' });
  });

  it('confirm は確認中の対象を削除し、成功文言を出して一覧を取り直す', async () => {
    const remove = jest.fn(async () => {});
    const { result, reload } = setup(remove);

    act(() => result.current.ask({ id: '1' }));
    await act(async () => {
      await result.current.confirm();
    });

    expect(remove).toHaveBeenCalledWith({ id: '1' });
    expect(notify.success).toHaveBeenCalledWith('削除しました');
    expect(reload).toHaveBeenCalledTimes(1);
  });

  it('失敗時はサーバが返した文言を出し、一覧は取り直さない', async () => {
    const remove = jest.fn(async () => {
      throw { response: { data: { error: 'このロールは付与中のため削除できません' } } };
    });
    const { result, reload } = setup(remove);

    act(() => result.current.ask({ id: '1' }));
    await act(async () => {
      await result.current.confirm();
    });

    expect(notify.error).toHaveBeenCalledWith('このロールは付与中のため削除できません');
    expect(notify.success).not.toHaveBeenCalled();
    expect(reload).not.toHaveBeenCalled();
  });

  it('409 は一覧をその場で取り直し、次の削除を止めない', async () => {
    const remove = jest.fn(async () => {
      throw { response: { status: 409, data: { error: '媒体が更新されています' } } };
    });
    const { result, reload } = setup(remove);

    act(() => result.current.ask({ id: '1' }));
    await act(async () => {
      await result.current.confirm();
    });
    expect(reload).toHaveBeenCalledTimes(1);

    act(() => result.current.ask({ id: '1' }));
    await act(async () => {
      await result.current.confirm();
    });
    expect(remove).toHaveBeenCalledTimes(2);
  });

  it('前の削除の応答待ちに確認した削除は送らず、そのことを伝える', async () => {
    let finish: () => void = () => {};
    const remove = jest.fn(() => new Promise<void>(resolve => (finish = resolve)));
    const { result } = setup(remove);

    act(() => result.current.ask({ id: '1' }));
    let first: Promise<void> = Promise.resolve();
    act(() => {
      first = result.current.confirm();
    });
    act(() => result.current.ask({ id: '2' }));
    await act(async () => {
      await result.current.confirm();
    });

    expect(remove).toHaveBeenCalledTimes(1);
    expect(notify.error).toHaveBeenCalledWith('前の操作が終わってから、もう一度実行してください');
    await act(async () => {
      finish();
      await first;
    });
  });

  it('サーバが理由を返さないときだけ既定の文言へ落ちる', async () => {
    const remove = jest.fn(async () => {
      throw new Error('boom');
    });
    const { result } = setup(remove);

    act(() => result.current.ask({ id: '1' }));
    await act(async () => {
      await result.current.confirm();
    });

    expect(notify.error).toHaveBeenCalledWith('削除に失敗しました');
  });

  it('対象がないまま confirm しても削除しない', async () => {
    const remove = jest.fn(async () => {});
    const { result } = setup(remove);

    await act(async () => {
      await result.current.confirm();
    });

    expect(remove).not.toHaveBeenCalled();
  });
});
