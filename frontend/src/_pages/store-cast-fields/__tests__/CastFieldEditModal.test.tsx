import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { toast } from 'react-hot-toast';
import type { CastFieldDefinitionResponse } from '@/entities/cast';
import { castFieldDefinitionApi } from '@/entities/cast';
import { CastFieldEditModal } from '../ui/CastFieldEditModal';

jest.mock('@/entities/cast', () => ({
  castFieldDefinitionApi: { update: jest.fn() },
}));

jest.mock('react-hot-toast', () => ({
  toast: { success: jest.fn(), error: jest.fn() },
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
    render(
      <CastFieldEditModal open definition={null} onClose={jest.fn()} onUpdated={jest.fn()} />
    );

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

  it('label が空なら更新 API を呼ばない', async () => {
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

    await waitFor(() => expect(screen.getByRole('button', { name: '保存する' })).toBeEnabled());
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
    expect(toast.success).toHaveBeenCalledWith('フィールドを更新しました');
    expect(onClose).toHaveBeenCalledTimes(1);
  });

  it('キャンセルは更新せず閉じる', () => {
    const onClose = jest.fn();
    render(
      <CastFieldEditModal
        open
        definition={definition()}
        onClose={onClose}
        onUpdated={jest.fn()}
      />
    );

    fireEvent.click(screen.getByRole('button', { name: 'キャンセル' }));

    expect(onClose).toHaveBeenCalledTimes(1);
    expect(mockedApi.update).not.toHaveBeenCalled();
  });
});
