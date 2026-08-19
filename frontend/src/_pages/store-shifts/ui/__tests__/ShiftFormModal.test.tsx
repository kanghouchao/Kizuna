import { act, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { ShiftFormModal } from '../ShiftFormModal';
import { CastResponse } from '@/entities/cast';
import { ShiftResponse, shiftApi } from '@/entities/shift';

jest.mock('@/entities/shift', () => ({
  shiftApi: {
    create: jest.fn(),
    update: jest.fn(),
    delete: jest.fn(),
  },
}));

jest.mock('@/shared/notify', () => ({
  notify: { success: jest.fn(), error: jest.fn(), warning: jest.fn() },
}));

const mockedCreate = shiftApi.create as jest.Mock;
const mockedUpdate = shiftApi.update as jest.Mock;
const mockedDelete = shiftApi.delete as jest.Mock;

const cast = (id: string, name: string): CastResponse => ({
  id,
  name,
  status: 'ACTIVE',
  invitation_status: 'NOT_INVITED',
  created_at: '2026-01-01T00:00:00Z',
  updated_at: '2026-01-01T00:00:00Z',
});

const CASTS = [cast('cast-1', 'キャストA'), cast('cast-2', 'キャストB')];

const EDITING: ShiftResponse = {
  id: 'shift-1',
  cast_id: 'cast-2',
  work_date: '2026-08-02',
  start_time: '19:30:00',
  end_time: '01:00:00',
  status: 'CONFIRMED',
  created_at: '2026-01-01T00:00:00Z',
  updated_at: '2026-01-01T00:00:00Z',
};

/** モーダル内のセレクトは上から キャスト・ステータス の順に並ぶ。 */
const CAST_SELECT = 0;
const STATUS_SELECT = 1;

function renderModal(props: Partial<React.ComponentProps<typeof ShiftFormModal>> = {}) {
  const onClose = jest.fn();
  const onSaved = jest.fn();
  const view = render(
    <ShiftFormModal
      open
      onClose={onClose}
      casts={CASTS}
      editing={null}
      defaultDate="2026-08-01"
      onSaved={onSaved}
      {...props}
    />
  );
  return { ...view, onClose, onSaved };
}

function selectAt(index: number) {
  return screen.getAllByRole('combobox')[index];
}

/**
 * 選択中の項目の表示文言。素の select は選択済み option、shadcn Select はトリガの内容。
 */
function selectedLabel(index: number) {
  const el = selectAt(index);
  if (el.tagName === 'SELECT') {
    return (el as HTMLSelectElement).selectedOptions[0]?.textContent ?? '';
  }
  return el.textContent ?? '';
}

/**
 * 表示文言で項目を選ぶ。素の select は change、shadcn Select は開いてから項目を押す
 * （項目の確定には pointerdown が要る——click だけでは無視される）。
 */
async function pickOption(index: number, optionName: string) {
  const el = selectAt(index);
  if (el.tagName === 'SELECT') {
    const option = within(el).getByRole('option', { name: optionName }) as HTMLOptionElement;
    fireEvent.change(el, { target: { value: option.value } });
    return;
  }
  fireEvent.click(el);
  const option = await screen.findByRole('option', { name: optionName });
  // Base UI の Item は pointerdown を経ていない mouse click を無視する
  fireEvent.pointerDown(option);
  fireEvent.click(option);
}

function submit() {
  fireEvent.click(screen.getByRole('button', { name: '保存する' }));
}

describe('シフトフォームのセレクト配線と送信ペイロード', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    mockedCreate.mockResolvedValue({});
    mockedUpdate.mockResolvedValue({});
    mockedDelete.mockResolvedValue(undefined);
  });

  it('新規作成の既定値が先頭キャスト・未確定・秒付き時刻で送られること', async () => {
    renderModal();

    submit();

    await waitFor(() => expect(mockedCreate).toHaveBeenCalledTimes(1));
    const payload = mockedCreate.mock.calls[0][0];
    expect(payload).toEqual({
      cast_id: 'cast-1',
      work_date: '2026-08-01',
      start_time: '18:00:00',
      end_time: '23:00:00',
      status: 'TENTATIVE',
      // 公開が常態・隠蔽が例外。既定 = 非公開は毎日の明示公開を強い、漏れ＝出勤表が空白になる
      published: true,
    });
    // キー集合そのものが移行で増減しないことを固定する
    expect(Object.keys(payload).sort()).toEqual([
      'cast_id',
      'end_time',
      'published',
      'start_time',
      'status',
      'work_date',
    ]);
  });

  it('公開の切替を外して追加すると非公開で出生すること', async () => {
    // 内密の出勤は追加の瞬間から非公開でなければ守れない（ADR 0015）
    renderModal();

    fireEvent.click(screen.getByRole('switch', { name: '公式サイトに公開する' }));
    submit();

    await waitFor(() => expect(mockedCreate).toHaveBeenCalledTimes(1));
    expect(mockedCreate.mock.calls[0][0].published).toBe(false);
  });

  it('編集では公開の切替を出さないこと', async () => {
    // 既にタイムラインの目玉と公開パネルという二つの入口がある
    renderModal({ editing: EDITING });

    await waitFor(() => expect(selectedLabel(STATUS_SELECT)).toBe('確定'));
    expect(screen.queryByRole('switch', { name: '公式サイトに公開する' })).not.toBeInTheDocument();
  });

  it('新規作成時に先頭キャストと未確定が表示されること', async () => {
    renderModal();

    await waitFor(() => expect(selectedLabel(CAST_SELECT)).toBe('キャストA'));
    expect(selectedLabel(STATUS_SELECT)).toBe('未確定');
  });

  it('キャストを選び直すと選んだ ID が送られること', async () => {
    renderModal();

    await pickOption(CAST_SELECT, 'キャストB');
    submit();

    await waitFor(() => expect(mockedCreate).toHaveBeenCalledTimes(1));
    expect(mockedCreate.mock.calls[0][0].cast_id).toBe('cast-2');
  });

  it('ステータスを確定へ変えると CONFIRMED が送られること', async () => {
    renderModal();

    await pickOption(STATUS_SELECT, '確定');
    submit();

    await waitFor(() => expect(mockedCreate).toHaveBeenCalledTimes(1));
    expect(mockedCreate.mock.calls[0][0].status).toBe('CONFIRMED');
  });

  it('編集時は既存のキャストとステータスが表示されること', async () => {
    renderModal({ editing: EDITING });

    await waitFor(() => expect(selectedLabel(CAST_SELECT)).toBe('キャストB'));
    expect(selectedLabel(STATUS_SELECT)).toBe('確定');
  });

  it('編集を未変更で保存すると既存値がそのまま更新に送られること', async () => {
    const { onSaved, onClose } = renderModal({ editing: EDITING });

    await waitFor(() => expect(selectedLabel(CAST_SELECT)).toBe('キャストB'));
    submit();

    await waitFor(() => expect(mockedUpdate).toHaveBeenCalledTimes(1));
    expect(mockedUpdate).toHaveBeenCalledWith('shift-1', {
      cast_id: 'cast-2',
      work_date: '2026-08-02',
      start_time: '19:30:00',
      end_time: '01:00:00',
      status: 'CONFIRMED',
    });
    expect(mockedCreate).not.toHaveBeenCalled();
    await waitFor(() => expect(onSaved).toHaveBeenCalledTimes(1));
    expect(onClose).toHaveBeenCalledTimes(1);
  });

  it('編集でステータスを未確定へ戻すと TENTATIVE が送られること', async () => {
    renderModal({ editing: EDITING });

    await waitFor(() => expect(selectedLabel(STATUS_SELECT)).toBe('確定'));
    await pickOption(STATUS_SELECT, '未確定');
    submit();

    await waitFor(() => expect(mockedUpdate).toHaveBeenCalledTimes(1));
    expect(mockedUpdate.mock.calls[0][1].status).toBe('TENTATIVE');
  });

  it('編集で別のキャストへ付け替えると新しい ID が送られること', async () => {
    renderModal({ editing: EDITING });

    await waitFor(() => expect(selectedLabel(CAST_SELECT)).toBe('キャストB'));
    await pickOption(CAST_SELECT, 'キャストA');
    submit();

    await waitFor(() => expect(mockedUpdate).toHaveBeenCalledTimes(1));
    expect(mockedUpdate.mock.calls[0][1].cast_id).toBe('cast-1');
  });

  it('一覧に無いキャストを編集しても別のキャストが表示されないこと', async () => {
    renderModal({ editing: { ...EDITING, cast_id: 'cast-ghost' } });

    // ステータスの反映を待つことで初期化が済んだ状態を確かめてから空表示を見る
    await waitFor(() => expect(selectedLabel(STATUS_SELECT)).toBe('確定'));
    expect(selectedLabel(CAST_SELECT)).toBe('');
  });

  it('キャストが未登録のときは未登録の案内が表示され、保存しても送信されないこと', async () => {
    const { onSaved } = renderModal({ casts: [] });

    await waitFor(() => expect(selectedLabel(CAST_SELECT)).toBe('キャストが未登録です'));
    await act(async () => submit());

    expect(mockedCreate).not.toHaveBeenCalled();
    expect(onSaved).not.toHaveBeenCalled();
  });

  it('キャストが未登録のときに案内項目を選び直しても送信されないこと', async () => {
    const { onSaved } = renderModal({ casts: [] });

    await waitFor(() => expect(selectedLabel(CAST_SELECT)).toBe('キャストが未登録です'));
    await pickOption(CAST_SELECT, 'キャストが未登録です');
    await act(async () => submit());

    expect(mockedCreate).not.toHaveBeenCalled();
    expect(onSaved).not.toHaveBeenCalled();
  });

  // キャスト取得が失敗しても既存シフトは描けるため、未登録の案内項目と編集値が同時に存在しうる。
  // このときだけ案内項目は「選択されていない項目」になり、選ぶと値が空へ戻って送信が止まる。
  it('キャストが未登録のまま編集を開き案内項目を選ぶと送信されないこと', async () => {
    const { onSaved } = renderModal({ casts: [], editing: EDITING });

    await pickOption(CAST_SELECT, 'キャストが未登録です');
    await act(async () => submit());

    expect(mockedUpdate).not.toHaveBeenCalled();
    expect(mockedCreate).not.toHaveBeenCalled();
    expect(onSaved).not.toHaveBeenCalled();
  });

  it('時刻を消して保存すると欄ごとの文言を出し、作成を呼ばないこと', async () => {
    renderModal();

    fireEvent.change(screen.getByLabelText('開始'), { target: { value: '' } });
    fireEvent.change(screen.getByLabelText('終了'), { target: { value: '' } });
    fireEvent.click(screen.getByRole('button', { name: '保存する' }));

    expect(await screen.findByText('開始時刻を入力してください')).toBeInTheDocument();
    expect(screen.getByText('終了時刻を入力してください')).toBeInTheDocument();
    expect(mockedCreate).not.toHaveBeenCalled();
    // 検証を理由にボタンを塞がない
    expect(screen.getByRole('button', { name: '保存する' })).toBeEnabled();
  });

  // Select は button が焦点要素で、field.ref が trigger へ届かないと handleSubmit の
  // 焦点移動だけが静かに落ちる（文言は出るので他の症状が無い）。
  it('キャストが未登録なら理由を名乗り、その trigger へ焦点が移ること', async () => {
    renderModal({ casts: [] });

    fireEvent.click(screen.getByRole('button', { name: '保存する' }));

    expect(await screen.findByText('キャストを選択してください')).toBeInTheDocument();
    expect(mockedCreate).not.toHaveBeenCalled();
    expect(selectAt(CAST_SELECT)).toHaveFocus();
  });

  it('削除は確認を経て呼ばれ、取り消すと呼ばれないこと', async () => {
    const { onSaved } = renderModal({ editing: EDITING });

    fireEvent.click(screen.getByRole('button', { name: '削除' }));
    const dialog = await screen.findByRole('alertdialog');
    expect(dialog).toHaveTextContent('このシフトを削除しますか？');

    fireEvent.click(screen.getByRole('button', { name: 'キャンセル' }));
    await waitFor(() => expect(screen.queryByRole('alertdialog')).not.toBeInTheDocument());
    expect(mockedDelete).not.toHaveBeenCalled();

    fireEvent.click(screen.getByRole('button', { name: '削除' }));
    fireEvent.click(await screen.findByRole('button', { name: '削除する' }));

    await waitFor(() => expect(mockedDelete).toHaveBeenCalledWith('shift-1'));
    await waitFor(() => expect(onSaved).toHaveBeenCalledTimes(1));
  });
});
