import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { fileApi } from '@/shared/api';
import ImageUpload from '../image-upload';

jest.mock('@/shared/api', () => ({ fileApi: { upload: jest.fn() } }));
jest.mock('@/shared/notify', () => ({ notify: { success: jest.fn(), error: jest.fn() } }));

const upload = jest.mocked(fileApi.upload);

describe('ImageUpload', () => {
  beforeEach(() => jest.clearAllMocks());

  it('選択した画像を呼び出し元の用途でアップロードし、返った URL を渡すこと', async () => {
    upload.mockResolvedValue({ url: '/static/uploads/public/1/store-mv/a.png', size: 1 });
    const onChange = jest.fn();
    const { container } = render(<ImageUpload purpose="store-mv" onChange={onChange} />);

    const image = new File(['x'], 'a.png', { type: 'image/png' });
    fireEvent.change(container.querySelector('input[type="file"]')!, {
      target: { files: [image] },
    });

    await waitFor(() =>
      expect(onChange).toHaveBeenCalledWith('/static/uploads/public/1/store-mv/a.png')
    );
    expect(upload).toHaveBeenCalledWith(image, 'store-mv');
  });

  it('アップロード中は削除も別画像の選択も受け付けず、先の結果だけを渡すこと', async () => {
    let finish: (value: { url: string; size: number }) => void = () => {};
    upload.mockReturnValue(new Promise(resolve => (finish = resolve)));
    const onChange = jest.fn();
    const { container } = render(
      <ImageUpload purpose="article-cover" value="/old.png" onChange={onChange} />
    );
    const input = container.querySelector('input[type="file"]')!;

    fireEvent.change(input, {
      target: { files: [new File(['a'], 'a.png', { type: 'image/png' })] },
    });
    await waitFor(() => expect(screen.queryByRole('button')).toBeNull());
    fireEvent.change(input, {
      target: { files: [new File(['b'], 'b.png', { type: 'image/png' })] },
    });
    expect(upload).toHaveBeenCalledTimes(1);

    finish({ url: '/a.png', size: 1 });
    await waitFor(() => expect(onChange).toHaveBeenCalledWith('/a.png'));
    expect(onChange).toHaveBeenCalledTimes(1);
    expect(screen.getByRole('button')).toBeInTheDocument();
  });
});
