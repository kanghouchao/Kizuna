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

describe('カスタムフィールド定義の新規作成モーダルは予約キーをクライアント側で拒否する（#277）', () => {
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

  it('予約語を部分的に含むが一致しないキーは従来どおり作成 API を呼ぶこと（退行防止）', async () => {
    mockedApi.create.mockResolvedValue({} as never);
    render(<CastFieldCreateModal open onClose={jest.fn()} onCreated={jest.fn()} />);

    fillAndSubmit('constructors');

    await waitFor(() => expect(mockedApi.create).toHaveBeenCalledTimes(1));
    expect(mockedApi.create.mock.calls[0][0]).toMatchObject({ key: 'constructors' });
  });
});
