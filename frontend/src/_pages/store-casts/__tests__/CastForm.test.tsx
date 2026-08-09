import { fireEvent, render, screen, waitFor } from '@testing-library/react';
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
    fireEvent.change(screen.getByLabelText('名前 *'), { target: { value: '花子' } });
    fireEvent.click(screen.getByRole('button', { name: '保存する' }));

    expect(await screen.findByText(message)).toBeInTheDocument();
    expect(onSubmit).not.toHaveBeenCalled();
  });

  it('整数なら従来どおり送信できること', async () => {
    const onSubmit = jest.fn();
    render(<CastForm onSubmit={onSubmit} />);

    fireEvent.change(screen.getByLabelText('名前 *'), { target: { value: '花子' } });
    fireEvent.change(screen.getByLabelText('年齢'), { target: { value: '25' } });
    fireEvent.click(screen.getByRole('button', { name: '保存する' }));

    await waitFor(() => expect(onSubmit).toHaveBeenCalledTimes(1));
    expect(onSubmit.mock.calls[0][0].age).toBe(25);
  });

  it('空欄のままなら整数規則は黙っていること（任意項目を塞がない）', async () => {
    const onSubmit = jest.fn();
    render(<CastForm onSubmit={onSubmit} />);

    fireEvent.change(screen.getByLabelText('名前 *'), { target: { value: '花子' } });
    fireEvent.click(screen.getByRole('button', { name: '保存する' }));

    await waitFor(() => expect(onSubmit).toHaveBeenCalledTimes(1));
    expect(screen.queryByText(/整数で入力してください/)).not.toBeInTheDocument();
  });
});
