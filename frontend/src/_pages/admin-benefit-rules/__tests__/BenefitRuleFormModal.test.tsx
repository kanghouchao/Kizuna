import { act, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { notify } from '@/shared/notify';
import { benefitRuleApi } from '@/entities/benefit-rule';
import { BenefitRuleFormModal } from '../ui/BenefitRuleFormModal';

jest.mock('@/entities/benefit-rule', () => ({
  ...jest.requireActual('@/entities/benefit-rule'),
  benefitRuleApi: { get: jest.fn(), create: jest.fn(), update: jest.fn() },
}));

jest.mock('@/shared/notify', () => ({
  notify: { success: jest.fn(), error: jest.fn(), warning: jest.fn() },
}));

const mockedApi = benefitRuleApi as jest.Mocked<typeof benefitRuleApi>;

function renderCreateModal() {
  render(
    <BenefitRuleFormModal
      onClose={jest.fn()}
      editingId={null}
      onSaved={jest.fn()}
      stores={[]}
      storesLoading={false}
      storesFailed={false}
      onReloadStores={jest.fn()}
    />
  );
}

async function pick(comboboxName: string, optionName: string) {
  fireEvent.click(screen.getByRole('combobox', { name: comboboxName }));
  const option = await screen.findByRole('option', { name: optionName });
  fireEvent.pointerDown(option);
  fireEvent.click(option);
}

// 開いた一覧は閉じない（Escape は Dialog ごと閉じる）ので、各テストの最後に読む。
async function optionNamesOf(comboboxName: string): Promise<string[]> {
  fireEvent.click(screen.getByRole('combobox', { name: comboboxName }));
  const options = await screen.findAllByRole('option');
  return options.map(o => o.textContent ?? '');
}

describe('特典規則フォームの重複可否', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    mockedApi.create.mockResolvedValue({ id: 1, store_count: 0, enabled: true });
  });

  it('ログインを選ぶと重複可否は一日一回・一人一回限りの二値に絞られ、既定は一日一回になる', async () => {
    renderCreateModal();
    await pick('種別', 'ログイン');

    await waitFor(() =>
      expect(screen.getByRole('combobox', { name: '重複可否' })).toHaveTextContent('一日一回')
    );
    expect(screen.getByText(/ログイン規則は毎回を取れません/)).toBeInTheDocument();
    expect(await optionNamesOf('重複可否')).toEqual(['一日一回', '一人一回限り']);
  });

  it('ログイン規則の作成は一日一回の重複可否を送る', async () => {
    renderCreateModal();
    await pick('種別', 'ログイン');
    fireEvent.change(screen.getByRole('textbox', { name: '規則名' }), {
      target: { value: 'ログインボーナス' },
    });
    fireEvent.change(screen.getByRole('spinbutton', { name: '付与ポイント' }), {
      target: { value: '10' },
    });
    await waitFor(() =>
      expect(screen.getByRole('combobox', { name: '重複可否' })).toHaveTextContent('一日一回')
    );

    fireEvent.click(screen.getByRole('button', { name: '保存する' }));

    await waitFor(() => expect(mockedApi.create).toHaveBeenCalledTimes(1));
    expect(mockedApi.create).toHaveBeenCalledWith(
      expect.objectContaining({
        type: 'LOGIN',
        repeat_policy: 'ONCE_PER_DAY',
        store_scope_type: 'ALL_STORES',
        points: 10,
      })
    );
  });

  it('来店に戻すと毎回・一人一回限りへ戻り、ログインでしか取れない一日一回は残らない', async () => {
    renderCreateModal();
    await pick('種別', 'ログイン');
    await waitFor(() =>
      expect(screen.getByRole('combobox', { name: '重複可否' })).toHaveTextContent('一日一回')
    );

    await pick('種別', '来店');

    await waitFor(() =>
      expect(screen.getByRole('combobox', { name: '重複可否' })).toHaveTextContent('毎回')
    );
    expect(await optionNamesOf('重複可否')).toEqual(['毎回', '一人一回限り']);
  });
});

describe('特典規則フォームの更新の競合', () => {
  const detail = (version: number, name: string) => ({
    id: 7,
    name,
    type: 'VISIT' as const,
    store_scope_type: 'ALL_STORES' as const,
    store_ids: [],
    store_count: 0,
    repeat_policy: 'EVERY_TIME' as const,
    points: 500,
    enabled: true,
    version,
  });

  it('409 は警告を出して一覧も取り直させ、詳細の取り直しの間は欄を出さず、最新の版で保存し直せる', async () => {
    jest.clearAllMocks();
    let finish: (value: ReturnType<typeof detail>) => void = () => {};
    mockedApi.get
      .mockResolvedValueOnce(detail(3, '来店ボーナス'))
      .mockImplementationOnce(() => new Promise(resolve => (finish = resolve)));
    mockedApi.update
      .mockRejectedValueOnce({ response: { status: 409 } })
      .mockResolvedValueOnce(detail(5, '別の管理者の変更'));
    const onSaved = jest.fn();
    render(
      <BenefitRuleFormModal
        onClose={jest.fn()}
        editingId={7}
        onSaved={onSaved}
        stores={[]}
        storesLoading={false}
        storesFailed={false}
        onReloadStores={jest.fn()}
      />
    );
    fireEvent.click(await screen.findByRole('button', { name: '保存する' }));

    expect(await screen.findByText('読み込み中...')).toBeInTheDocument();
    expect(notify.warning).toHaveBeenCalledWith(
      '他の管理者が更新しました。最新の内容を確認してください'
    );
    expect(onSaved).toHaveBeenCalledTimes(1);
    expect(screen.queryByRole('button', { name: '保存する' })).not.toBeInTheDocument();

    await act(async () => finish(detail(4, '別の管理者の変更')));
    fireEvent.click(await screen.findByRole('button', { name: '保存する' }));
    await waitFor(() => expect(mockedApi.update).toHaveBeenCalledTimes(2));
    expect(mockedApi.update.mock.calls[1][1]).toEqual(expect.objectContaining({ version: 4 }));
  });
});
