import { act, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { CastFieldCreateModal } from '../ui/CastFieldCreateModal';
import { castFieldDefinitionApi } from '@/entities/cast';

jest.mock('@/entities/cast', () => ({
  castFieldDefinitionApi: {
    create: jest.fn(),
  },
}));

jest.mock('react-hot-toast', () => ({
  toast: { success: jest.fn(), error: jest.fn() },
}));

const mockedApi = castFieldDefinitionApi as jest.Mocked<typeof castFieldDefinitionApi>;

describe('カスタムフィールド定義の新規作成モーダルは予約キーをクライアント側で拒否する', () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  const fillAndSubmit = (key: string) => {
    fireEvent.change(screen.getByLabelText('key'), { target: { value: key } });
    fireEvent.change(screen.getByLabelText('label'), { target: { value: '血液型' } });
    fireEvent.click(screen.getByRole('button', { name: '追加する' }));
  };

  it.each(['constructor', 'prototype'])(
    '予約キー「%s」はクライアント検証で弾かれ作成 API を呼ばないこと',
    async key => {
      mockedApi.create.mockResolvedValue({} as never);
      render(<CastFieldCreateModal open onClose={jest.fn()} onCreated={jest.fn()} />);

      fillAndSubmit(key);

      // react-hook-form の submit バリデーションは非同期。マイクロ／マクロタスクを
      // フラッシュして送信サイクルの完了を待ってから未送信を確認する。
      await act(async () => {
        await new Promise(resolve => setTimeout(resolve, 0));
      });
      expect(mockedApi.create).not.toHaveBeenCalled();
    }
  );

  it('予約語を部分的に含むが一致しないキーは作成 API を呼ぶこと（退行防止）', async () => {
    mockedApi.create.mockResolvedValue({} as never);
    render(<CastFieldCreateModal open onClose={jest.fn()} onCreated={jest.fn()} />);

    fillAndSubmit('constructors');

    await waitFor(() => expect(mockedApi.create).toHaveBeenCalledTimes(1));
    expect(mockedApi.create.mock.calls[0][0]).toMatchObject({ key: 'constructors' });
  });
});

describe('カスタムフィールド定義の新規作成モーダルの送信ペイロード', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    mockedApi.create.mockResolvedValue({} as never);
  });

  it('閉じているときは何も描画しない', () => {
    render(<CastFieldCreateModal open={false} onClose={jest.fn()} onCreated={jest.fn()} />);

    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
  });

  it('key・label・公開設定の3項目だけを送ること', async () => {
    render(<CastFieldCreateModal open onClose={jest.fn()} onCreated={jest.fn()} />);

    fireEvent.change(screen.getByLabelText('key'), { target: { value: 'blood_type' } });
    fireEvent.change(screen.getByLabelText('label'), { target: { value: '血液型' } });
    fireEvent.click(screen.getByRole('button', { name: '追加する' }));

    await waitFor(() => expect(mockedApi.create).toHaveBeenCalledTimes(1));
    expect(mockedApi.create.mock.calls[0][0]).toEqual({
      key: 'blood_type',
      label: '血液型',
      is_public: false,
    });
  });

  it('公開チェックは真偽値で送られること', async () => {
    render(<CastFieldCreateModal open onClose={jest.fn()} onCreated={jest.fn()} />);

    fireEvent.change(screen.getByLabelText('key'), { target: { value: 'blood_type' } });
    fireEvent.change(screen.getByLabelText('label'), { target: { value: '血液型' } });
    fireEvent.click(screen.getByLabelText('公開する(公開詳細ページに表示)'));
    fireEvent.click(screen.getByRole('button', { name: '追加する' }));

    await waitFor(() => expect(mockedApi.create).toHaveBeenCalledTimes(1));
    expect(mockedApi.create.mock.calls[0][0].is_public).toBe(true);
  });

  it('作成成功で onCreated と onClose を呼ぶこと', async () => {
    const onClose = jest.fn();
    const onCreated = jest.fn();
    render(<CastFieldCreateModal open onClose={onClose} onCreated={onCreated} />);

    fireEvent.change(screen.getByLabelText('key'), { target: { value: 'blood_type' } });
    fireEvent.change(screen.getByLabelText('label'), { target: { value: '血液型' } });
    fireEvent.click(screen.getByRole('button', { name: '追加する' }));

    await waitFor(() => expect(onCreated).toHaveBeenCalledTimes(1));
    expect(onClose).toHaveBeenCalledTimes(1);
  });

  it('キャンセルは作成せず閉じること', () => {
    const onClose = jest.fn();
    render(<CastFieldCreateModal open onClose={onClose} onCreated={jest.fn()} />);

    fireEvent.click(screen.getByRole('button', { name: 'キャンセル' }));

    expect(onClose).toHaveBeenCalledTimes(1);
    expect(mockedApi.create).not.toHaveBeenCalled();
  });
});
