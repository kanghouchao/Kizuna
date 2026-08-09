import { act, renderHook, waitFor } from '@testing-library/react';
import { useResource } from '@/shared/lib';

/** isNotFound が読む形（axios の応答）。 */
const notFoundError = { response: { status: 404 } };

describe('useResource', () => {
  it('マウント時に取得し data と isLoading を管理する', async () => {
    const fetcher = jest.fn(async () => ({ id: '1' }));
    const { result } = renderHook(() => useResource(fetcher));

    expect(result.current.isLoading).toBe(true);
    await waitFor(() => expect(result.current.isLoading).toBe(false));
    expect(result.current.data).toEqual({ id: '1' });
    expect(result.current.failure).toBeNull();
    expect(fetcher).toHaveBeenCalledTimes(1);
  });

  it('deps が変わったときだけ取り直す', async () => {
    const fetcher = jest.fn(async (id: string) => ({ id }));
    const { result, rerender } = renderHook(({ id }) => useResource(() => fetcher(id), [id]), {
      initialProps: { id: 'a' },
    });
    await waitFor(() => expect(result.current.data).toEqual({ id: 'a' }));

    // 同じ deps の再レンダーでは取り直さない（fetcher は毎レンダー新しい関数だが依存ではない）
    rerender({ id: 'a' });
    expect(fetcher).toHaveBeenCalledTimes(1);

    rerender({ id: 'b' });
    await waitFor(() => expect(result.current.data).toEqual({ id: 'b' }));
    expect(fetcher).toHaveBeenCalledTimes(2);
  });

  it('fetcher が null の間は取りに行かず、読み込み中にもしない', async () => {
    const fetcher = jest.fn(async () => 'value');
    const { result, rerender } = renderHook(
      ({ enabled }) => useResource(enabled ? fetcher : null, [enabled]),
      { initialProps: { enabled: false } }
    );

    expect(result.current.isLoading).toBe(false);
    expect(fetcher).not.toHaveBeenCalled();

    rerender({ enabled: true });
    await waitFor(() => expect(result.current.data).toBe('value'));
    expect(fetcher).toHaveBeenCalledTimes(1);
  });

  it('無効なまま mount したときは一度も読み込み中にならない', () => {
    // 効果で解くだけでは、取りに行かない領域が最初の 1 フレームだけ「読み込み中...」を出す。
    // レンダーごとの値を記録して、その 1 フレームまで見る
    const seen: boolean[] = [];
    renderHook(() => {
      const resource = useResource<string>(null, []);
      seen.push(resource.isLoading);
      return resource;
    });

    expect(seen).not.toContain(true);
  });

  it('在途のまま無効化されたら読み込み中を解く', () => {
    // 解かないと、閉じた区画が「読み込み中」のまま固まる（応答は来ても無効化済みで捨てる）
    const fetcher = jest.fn(() => new Promise<string>(() => {}));
    const { result, rerender } = renderHook(
      ({ enabled }) => useResource(enabled ? fetcher : null, [enabled]),
      { initialProps: { enabled: true } }
    );
    expect(result.current.isLoading).toBe(true);

    rerender({ enabled: false });

    expect(result.current.isLoading).toBe(false);
  });

  it('無効化しても読めていた値は捨てない（取りに行かないだけ）', async () => {
    // 閉じたモーダルが持つ選択肢のように、再び有効になるまで表示されない値まで捨てると
    // 開き直した瞬間に生の id が出る。無効化は「取りに行かない」であって「忘れる」ではない
    const fetcher = jest.fn(async () => 'value');
    const { result, rerender } = renderHook(
      ({ enabled }) => useResource(enabled ? fetcher : null, [enabled]),
      { initialProps: { enabled: true } }
    );
    await waitFor(() => expect(result.current.data).toBe('value'));

    rerender({ enabled: false });
    expect(result.current.data).toBe('value');
    expect(result.current.isLoading).toBe(false);
  });

  it('古いリクエストの遅延応答は新しい結果を上書きしない', async () => {
    const resolvers: Array<(value: string) => void> = [];
    const fetcher = jest.fn(() => new Promise<string>(resolve => resolvers.push(resolve)));
    const { result } = renderHook(() => useResource(fetcher));

    // マウント時の取得（1件目）が在途のまま 2 件目を発火し、2件目 → 1件目の順に解決する
    act(() => {
      void result.current.reload();
    });
    await act(async () => {
      resolvers[1]('newer');
    });
    await act(async () => {
      resolvers[0]('stale');
    });

    expect(result.current.data).toBe('newer');
    expect(result.current.isLoading).toBe(false);
  });

  it('古いリクエストの遅延失敗は新しい成功を消さない', async () => {
    // 失敗が data を捨てるため、守衛を落とすと在途の古い失敗が新しく届いた値を消し、
    // 領域をエラー態へ倒してしまう（StrictMode の二重 mount がこの順序を日常的に作る）
    let rejectFirst!: (reason?: unknown) => void;
    let resolveSecond!: (value: string) => void;
    const fetcher = jest
      .fn<Promise<string>, []>()
      .mockImplementationOnce(() => new Promise((_, reject) => (rejectFirst = reject)))
      .mockImplementationOnce(() => new Promise(resolve => (resolveSecond = resolve)));
    const { result } = renderHook(() => useResource(fetcher));

    act(() => {
      void result.current.reload();
    });
    await act(async () => {
      resolveSecond('newer');
    });
    await act(async () => {
      rejectFirst(new Error('boom'));
    });

    expect(result.current.data).toBe('newer');
    expect(result.current.failure).toBeNull();
  });

  it('二度目が在途のまま古い失敗が着いても、読み込み表示を畳まない', async () => {
    // 上の 1 本は成功・catch の比較しか固定しない（どちらの飛行も着いた後で観測するため、
    // 在途の setIsLoading(false) は既に false の旗へ落ちる）。finally の比較は 2 度目を
    // 在途のまま留めて初めて観測できる
    let rejectFirst!: (reason?: unknown) => void;
    const fetcher = jest
      .fn<Promise<string>, []>()
      .mockImplementationOnce(() => new Promise((_, reject) => (rejectFirst = reject)))
      .mockImplementationOnce(() => new Promise(() => {}));
    const { result } = renderHook(() => useResource(fetcher));

    act(() => {
      void result.current.reload();
    });
    await act(async () => {
      rejectFirst(new Error('boom'));
    });

    expect(result.current.isLoading).toBe(true);
    expect(result.current.failure).toBeNull();
  });

  it('失敗時は failure を立てて data を捨て、loading を解除する', async () => {
    // 読めなかった値を残すと、それが最新に見える
    let succeed = true;
    const { result } = renderHook(() =>
      useResource(async () => {
        if (!succeed) throw new Error('boom');
        return 'value';
      })
    );
    await waitFor(() => expect(result.current.data).toBe('value'));

    succeed = false;
    await act(async () => {
      await result.current.reload();
    });

    expect(result.current.failure).toBe('error');
    expect(result.current.data).toBeNull();
    expect(result.current.isLoading).toBe(false);
  });

  it('404 は notFound として分類する', async () => {
    // 何度押しても取れない 404 と、押し直せば取れる失敗を呼び出し側が区別できるようにする
    const { result } = renderHook(() =>
      useResource(async () => {
        throw notFoundError;
      })
    );

    await waitFor(() => expect(result.current.failure).toBe('notFound'));
    expect(result.current.data).toBeNull();
  });

  it('再取得を始めた時点で失敗表示を畳む', async () => {
    let succeed = false;
    const { result } = renderHook(() =>
      useResource(async () => {
        if (!succeed) throw new Error('boom');
        return 'value';
      })
    );
    await waitFor(() => expect(result.current.failure).toBe('error'));

    succeed = true;
    await act(async () => {
      await result.current.reload();
    });

    expect(result.current.failure).toBeNull();
    expect(result.current.data).toBe('value');
  });

  it('在途のまま失敗表示を畳む（再試行が効いているか分かるように）', async () => {
    let resolveSecond!: (value: string) => void;
    const fetcher = jest
      .fn<Promise<string>, []>()
      .mockImplementationOnce(async () => {
        throw new Error('boom');
      })
      .mockImplementationOnce(() => new Promise(resolve => (resolveSecond = resolve)));
    const { result } = renderHook(() => useResource(fetcher));
    await waitFor(() => expect(result.current.failure).toBe('error'));

    act(() => {
      void result.current.reload();
    });

    // 応答を待たずに畳む。着いてから畳むと、押した再試行が効いているのか分からない
    expect(result.current.failure).toBeNull();
    expect(result.current.isLoading).toBe(true);

    await act(async () => {
      resolveSecond('value');
    });
    expect(result.current.data).toBe('value');
  });

  it('reload は再レンダー後の最新の fetcher を使う', async () => {
    const first = jest.fn(async () => 'first');
    const second = jest.fn(async () => 'second');
    const { result, rerender } = renderHook(({ fetcher }) => useResource(fetcher), {
      initialProps: { fetcher: first as () => Promise<string> },
    });
    await waitFor(() => expect(result.current.data).toBe('first'));

    rerender({ fetcher: second });
    await act(async () => {
      await result.current.reload();
    });

    expect(second).toHaveBeenCalledTimes(1);
    expect(result.current.data).toBe('second');
  });

  it('setData で取得済みの値を差し替えられる', async () => {
    // 保存の応答をそのまま反映する場面。取り直しに倒すと、直したばかりの欄が一瞬消える
    const { result } = renderHook(() => useResource(async () => 'value'));
    await waitFor(() => expect(result.current.data).toBe('value'));

    act(() => {
      result.current.setData('updated');
    });

    expect(result.current.data).toBe('updated');
  });
});
