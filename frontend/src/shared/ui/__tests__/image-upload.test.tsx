import { fireEvent, render, waitFor } from '@testing-library/react';
import { fileApi } from '@/shared/api';
import ImageUpload from '../image-upload';

jest.mock('@/shared/api', () => ({ fileApi: { upload: jest.fn() } }));
jest.mock('@/shared/notify', () => ({ notify: { success: jest.fn(), error: jest.fn() } }));

const upload = jest.mocked(fileApi.upload);

describe('ImageUpload', () => {
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
});
