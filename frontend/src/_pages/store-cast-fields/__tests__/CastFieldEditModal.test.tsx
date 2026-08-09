import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { notify } from '@/shared/notify';
import type { CastFieldDefinitionResponse } from '@/entities/cast';
import { castFieldDefinitionApi } from '@/entities/cast';
import { CastFieldEditModal } from '../ui/CastFieldEditModal';

jest.mock('@/entities/cast', () => ({
  castFieldDefinitionApi: { update: jest.fn() },
}));

jest.mock('@/shared/notify', () => ({
  notify: { success: jest.fn(), error: jest.fn(), warning: jest.fn() },
}));

const mockedApi = castFieldDefinitionApi as jest.Mocked<typeof castFieldDefinitionApi>;

const definition = (
  override: Partial<CastFieldDefinitionResponse> = {}
): CastFieldDefinitionResponse =>
  ({
    id: 'def-1',
    key: 'blood_type',
    label: '血液型',
    display_order: 3,
    is_public: false,
    ...override,
  }) as CastFieldDefinitionResponse;

describe('カスタムフィールド定義の編集モーダル', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    mockedApi.update.mockResolvedValue({} as never);
  });

  it('編集対象が null なら開いていても描画しない', () => {
    render(<CastFieldEditModal open definition={null} onClose={jest.fn()} onUpdated={jest.fn()} />);

    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
  });

  it('key は編集できず表示のみとする', () => {
    render(
      <CastFieldEditModal
        open
        definition={definition()}
        onClose={jest.fn()}
        onUpdated={jest.fn()}
      />
    );

    expect(screen.getByText('blood_type')).toBeInTheDocument();
    expect(screen.queryByLabelText('key')).not.toBeInTheDocument();
  });

  it('既存値がフォームへ初期表示される', () => {
    render(
      <CastFieldEditModal
        open
        definition={definition()}
        onClose={jest.fn()}
        onUpdated={jest.fn()}
      />
    );

    expect(screen.getByLabelText('label')).toHaveValue('血液型');
    expect(screen.getByLabelText('表示順')).toHaveValue(3);
  });

  it('表示順は数値型のまま更新 API へ送られる', async () => {
    render(
      <CastFieldEditModal
        open
        definition={definition()}
        onClose={jest.fn()}
        onUpdated={jest.fn()}
      />
    );

    fireEvent.change(screen.getByLabelText('表示順'), { target: { value: '12' } });
    fireEvent.click(screen.getByRole('button', { name: '保存する' }));

    await waitFor(() => expect(mockedApi.update).toHaveBeenCalledTimes(1));
    expect(mockedApi.update.mock.calls[0][0]).toBe('def-1');
    const body = mockedApi.update.mock.calls[0][1];
    expect(body).toEqual({ label: '血液型', display_order: 12, is_public: false });
    expect(typeof body.display_order).toBe('number');
  });

  it('公開チェックは真偽値で送られる', async () => {
    render(
      <CastFieldEditModal
        open
        definition={definition()}
        onClose={jest.fn()}
        onUpdated={jest.fn()}
      />
    );

    fireEvent.click(screen.getByLabelText('公開する(公開詳細ページに表示)'));
    fireEvent.click(screen.getByRole('button', { name: '保存する' }));

    await waitFor(() => expect(mockedApi.update).toHaveBeenCalledTimes(1));
    const body = mockedApi.update.mock.calls[0][1];
    expect(body.is_public).toBe(true);
  });

  it('label が空なら文言を欄の傍に出し、更新 API を呼ばない', async () => {
    render(
      <CastFieldEditModal
        open
        definition={definition()}
        onClose={jest.fn()}
        onUpdated={jest.fn()}
      />
    );

    fireEvent.change(screen.getByLabelText('label'), { target: { value: '' } });
    fireEvent.click(screen.getByRole('button', { name: '保存する' }));

    const message = await screen.findByText('label を入力してください');
    expect(screen.getByLabelText('label')).toHaveAttribute(
      'aria-describedby',
      expect.stringContaining(message.id)
    );
    expect(mockedApi.update).not.toHaveBeenCalled();
  });

  // 空欄の数値入力は NaN であって null でも空文字でもないため required 単独では素通りし、
  // display_order: null が送信されてしまう。文言を出せているのは validate の側。
  it('表示順が空なら文言を出し、display_order を欠いたまま送らないこと', async () => {
    render(
      <CastFieldEditModal
        open
        definition={definition()}
        onClose={jest.fn()}
        onUpdated={jest.fn()}
      />
    );

    fireEvent.change(screen.getByLabelText('表示順'), { target: { value: '' } });
    fireEvent.click(screen.getByRole('button', { name: '保存する' }));

    expect(await screen.findByText('表示順を入力してください')).toBeInTheDocument();
    expect(mockedApi.update).not.toHaveBeenCalled();
  });

  it('更新成功で onUpdated と onClose を呼ぶ', async () => {
    const onClose = jest.fn();
    const onUpdated = jest.fn();
    render(
      <CastFieldEditModal open definition={definition()} onClose={onClose} onUpdated={onUpdated} />
    );

    fireEvent.click(screen.getByRole('button', { name: '保存する' }));

    await waitFor(() => expect(onUpdated).toHaveBeenCalledTimes(1));
    expect(notify.success).toHaveBeenCalledWith('フィールドを更新しました');
    expect(onClose).toHaveBeenCalledTimes(1);
  });

  it('キャンセルは更新せず閉じる', () => {
    const onClose = jest.fn();
    render(
      <CastFieldEditModal open definition={definition()} onClose={onClose} onUpdated={jest.fn()} />
    );

    fireEvent.click(screen.getByRole('button', { name: 'キャンセル' }));

    expect(onClose).toHaveBeenCalledTimes(1);
    expect(mockedApi.update).not.toHaveBeenCalled();
  });

  // noValidate は type="number" の暗黙の step=1 まで止める。引き継ぎが無いと 1.5 が
  // Integer の displayOrder へ届き、欄の傍ではなくサーバから返ってくる。
  it('表示順に小数を入れると文言を出して更新を呼ばないこと', async () => {
    render(
      <CastFieldEditModal
        open
        definition={definition()}
        onClose={jest.fn()}
        onUpdated={jest.fn()}
      />
    );

    fireEvent.change(screen.getByLabelText('表示順'), { target: { value: '1.5' } });
    fireEvent.click(screen.getByRole('button', { name: '保存する' }));

    expect(await screen.findByText('表示順は整数で入力してください')).toBeInTheDocument();
    expect(mockedApi.update).not.toHaveBeenCalled();
  });
});
