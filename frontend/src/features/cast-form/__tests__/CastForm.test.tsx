import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { CastForm } from '../ui/CastForm';
import { CastFieldDefinitionResponse, castFieldDefinitionApi } from '@/entities/cast';

jest.mock('next/navigation', () => ({
  useRouter: () => ({ push: jest.fn(), back: jest.fn() }),
}));

jest.mock('@/entities/cast', () => {
  const actual = jest.requireActual('@/entities/cast');
  return {
    ...actual,
    castFieldDefinitionApi: {
      ...actual.castFieldDefinitionApi,
      list: jest.fn(),
    },
  };
});

const mockedApi = castFieldDefinitionApi as jest.Mocked<typeof castFieldDefinitionApi>;

const definition = (key: string, label: string): CastFieldDefinitionResponse => ({
  id: `def-${key}`,
  key,
  label,
  display_order: 0,
  is_public: false,
  created_at: '2026-07-01T00:00:00Z',
  updated_at: '2026-07-01T00:00:00Z',
});

describe('カスタムフィールドの初期値は自身が所有するキーのみ採用する', () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  it('existingCustomFields が所有しない（プロトタイプ継承の）キーの値は初期値に採用せず空にすること', async () => {
    // 本番のトリガーは key='constructor'（Object.prototype 由来）だが、react-hook-form 自身が
    // 'constructor' を含むフィールド名の register で内部クラッシュする別バグがあり描画に至れない。
    // ここでは同じ「継承プロパティを素朴なブラケットアクセスで拾う」経路を、register 可能な
    // 非予約キーとプロトタイプ注入で再現し、hasOwn ガード（本修正）のみを切り出して検証する。
    const inherited = Object.create({ blood_type: 'INHERITED_VALUE' }) as Record<string, string>;
    mockedApi.list.mockResolvedValue([definition('blood_type', '血液型')]);

    render(
      <CastForm
        initialData={{ name: '花子' }}
        existingCustomFields={inherited}
        onSubmit={jest.fn()}
      />
    );

    const input = (await screen.findByLabelText('血液型')) as HTMLInputElement;
    expect(input.value).toBe('');
  });

  it('existingCustomFields が自身で所有するキーの値は初期値として採用すること（正常系の退行防止）', async () => {
    mockedApi.list.mockResolvedValue([definition('blood_type', '血液型')]);

    render(
      <CastForm
        initialData={{ name: '花子' }}
        existingCustomFields={{ blood_type: 'A' }}
        onSubmit={jest.fn()}
      />
    );

    const input = (await screen.findByLabelText('血液型')) as HTMLInputElement;
    expect(input.value).toBe('A');
  });
});

/**
 * noValidate は type="number" の暗黙の step=1 まで止める。プロフィールの数値欄はいずれも
 * サーバ側が Integer なので、引き継ぎが無いと小数がそのまま届く。
 */
describe('プロフィールの数値欄は整数のみ受け付ける', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    mockedApi.list.mockResolvedValue([]);
  });

  it.each([
    ['年齢', '年齢は整数で入力してください'],
    ['身長 (cm)', '身長は整数で入力してください'],
    ['バスト (cm)', 'バストは整数で入力してください'],
    ['ウエスト (cm)', 'ウエストは整数で入力してください'],
    ['ヒップ (cm)', 'ヒップは整数で入力してください'],
    ['表示順', '表示順は整数で入力してください'],
  ])('%s に小数を入れると文言を出して送信しないこと', async (label, message) => {
    const onSubmit = jest.fn();
    render(<CastForm onSubmit={onSubmit} />);

    fireEvent.change(screen.getByLabelText(label), { target: { value: '1.5' } });
    fireEvent.change(screen.getByLabelText('源氏名 *'), { target: { value: '花子' } });
    fireEvent.click(screen.getByRole('button', { name: '保存する' }));

    expect(await screen.findByText(message)).toBeInTheDocument();
    expect(onSubmit).not.toHaveBeenCalled();
  });

  it('整数なら従来どおり送信できること', async () => {
    const onSubmit = jest.fn();
    render(<CastForm onSubmit={onSubmit} />);

    fireEvent.change(screen.getByLabelText('源氏名 *'), { target: { value: '花子' } });
    fireEvent.change(screen.getByLabelText('年齢'), { target: { value: '25' } });
    fireEvent.click(screen.getByRole('button', { name: '保存する' }));

    await waitFor(() => expect(onSubmit).toHaveBeenCalledTimes(1));
    expect(onSubmit.mock.calls[0][0].age).toBe(25);
  });

  it('空欄のままなら整数規則は黙っていること（任意項目を塞がない）', async () => {
    const onSubmit = jest.fn();
    render(<CastForm onSubmit={onSubmit} />);

    fireEvent.change(screen.getByLabelText('源氏名 *'), { target: { value: '花子' } });
    fireEvent.click(screen.getByRole('button', { name: '保存する' }));

    await waitFor(() => expect(onSubmit).toHaveBeenCalledTimes(1));
    expect(screen.queryByText(/整数で入力してください/)).not.toBeInTheDocument();
  });
});

it('公開項目と内部項目を分けて編集し、両方の値を保存する', async () => {
  mockedApi.list.mockResolvedValue([
    { ...definition('hobby', '趣味'), is_public: true },
    definition('memo', '管理メモ'),
  ]);
  const onSubmit = jest.fn();
  render(
    <CastForm
      initialData={{ name: '花子' }}
      existingCustomFields={{ hobby: '読書', memo: '確認済み' }}
      onSubmit={onSubmit}
    />
  );
  await screen.findByLabelText('趣味');
  const publicFields = screen.getByRole('region', { name: '公開プロフィール' });
  const internalFields = screen.getByRole('region', { name: '内部情報' });
  expect(within(publicFields).getByLabelText('源氏名 *')).toHaveValue('花子');
  expect(within(publicFields).getByLabelText('趣味')).toHaveValue('読書');
  expect(within(publicFields).queryByLabelText('管理メモ')).not.toBeInTheDocument();
  fireEvent.change(within(internalFields).getByLabelText('管理メモ'), {
    target: { value: '更新済み' },
  });
  fireEvent.click(screen.getByRole('button', { name: '保存する' }));
  await waitFor(() => expect(onSubmit).toHaveBeenCalled());
  expect(onSubmit.mock.calls[0][0].custom_fields).toEqual({ hobby: '読書', memo: '更新済み' });
});

it('定義取得に失敗したまま保存しても既存カスタムフィールドを消さない', async () => {
  mockedApi.list.mockRejectedValueOnce(new Error('network'));
  const onSubmit = jest.fn();
  render(
    <CastForm
      initialData={{ name: '花子' }}
      existingCustomFields={{ memo: '保持する値' }}
      onSubmit={onSubmit}
    />
  );
  await screen.findByText('カスタムフィールド定義の取得に失敗しました');
  expect(screen.queryByText('カスタムフィールドは登録されていません')).not.toBeInTheDocument();
  fireEvent.click(screen.getByRole('button', { name: '保存する' }));
  await waitFor(() => expect(onSubmit).toHaveBeenCalled());
  expect(onSubmit.mock.calls[0][0].custom_fields).toBeUndefined();
});
