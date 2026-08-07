import { act, renderHook } from '@testing-library/react';
import { notify } from '@/shared/notify';
import { useDeleteAction } from '@/shared/lib';

jest.mock('@/shared/notify', () => ({
  notify: { success: jest.fn(), error: jest.fn(), warning: jest.fn() },
}));

interface Row {
  id: string;
}

const setup = (remove: (target: Row) => Promise<void>, onDeleted = jest.fn()) => {
  const rendered = renderHook(() =>
    useDeleteAction<Row>({
      remove,
      successMessage: '削除しました',
      errorMessage: '削除に失敗しました',
      onDeleted,
    })
  );
  return { ...rendered, onDeleted };
};

describe('useDeleteAction', () => {
  beforeEach(() => jest.clearAllMocks());

  it('ask で対象を保持し cancel で手放す', () => {
    const { result } = setup(jest.fn(async () => {}));

    expect(result.current.target).toBeNull();
    act(() => result.current.ask({ id: '1' }));
    expect(result.current.target).toEqual({ id: '1' });
    act(() => result.current.cancel());
    expect(result.current.target).toBeNull();
  });

  it('confirm は確認中の対象を削除し、成功文言と後始末を走らせる', async () => {
    const remove = jest.fn(async () => {});
    const { result, onDeleted } = setup(remove);

    act(() => result.current.ask({ id: '1' }));
    await act(async () => {
      await result.current.confirm();
    });

    expect(remove).toHaveBeenCalledWith({ id: '1' });
    expect(notify.success).toHaveBeenCalledWith('削除しました');
    expect(onDeleted).toHaveBeenCalledTimes(1);
  });

  it('失敗時はサーバが返した文言を出し、後始末は走らせない', async () => {
    const remove = jest.fn(async () => {
      throw { response: { data: { error: 'このロールは付与中のため削除できません' } } };
    });
    const { result, onDeleted } = setup(remove);

    act(() => result.current.ask({ id: '1' }));
    await act(async () => {
      await result.current.confirm();
    });

    expect(notify.error).toHaveBeenCalledWith('このロールは付与中のため削除できません');
    expect(notify.success).not.toHaveBeenCalled();
    expect(onDeleted).not.toHaveBeenCalled();
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
