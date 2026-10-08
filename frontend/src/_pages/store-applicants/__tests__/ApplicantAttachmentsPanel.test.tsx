import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { applicantAttachmentApi } from '@/entities/applicant';
import { ApplicantAttachmentsPanel } from '../ui/ApplicantAttachmentsPanel';
jest.mock('@/entities/applicant', () => ({
  applicantAttachmentApi: {
    policy: jest.fn(),
    list: jest.fn(),
    uploads: jest.fn(),
    upload: jest.fn(),
    download: jest.fn(),
  },
}));
jest.mock('@/shared/notify', () => ({ notify: { success: jest.fn(), error: jest.fn() } }));
const policy = {
  configured: true,
  allowed_media_types: ['image/jpeg', 'image/png'],
  max_file_bytes: 1024,
  max_applicant_files: 20,
};
beforeEach(() => {
  jest.resetAllMocks();
  (applicantAttachmentApi.policy as jest.Mock).mockResolvedValue(policy);
  (applicantAttachmentApi.list as jest.Mock).mockResolvedValue({ rows: [], nextCursor: null });
  (applicantAttachmentApi.uploads as jest.Mock).mockResolvedValue({ rows: [], nextCursor: null });
});
test('未設定の保存先には一覧やアップロードを要求しない', async () => {
  (applicantAttachmentApi.policy as jest.Mock).mockResolvedValue({ ...policy, configured: false });
  render(<ApplicantAttachmentsPanel id="a" canManage editable />);
  expect(
    await screen.findByText('非公開ストレージが未設定のため、添付画像は利用できません。')
  ).toBeVisible();
  expect(applicantAttachmentApi.list).not.toHaveBeenCalled();
  expect(applicantAttachmentApi.uploads).not.toHaveBeenCalled();
});
test('閲覧権限だけの場合は未完了情報と入力を取得しない', async () => {
  render(<ApplicantAttachmentsPanel id="a" canManage={false} editable />);
  expect(await screen.findByText('添付画像はありません。')).toBeVisible();
  expect(screen.queryByLabelText('追加する画像')).not.toBeInTheDocument();
  expect(applicantAttachmentApi.uploads).not.toHaveBeenCalled();
});
test('空入力と不正形式は入力に紐づくエラーを表示し送信しない', async () => {
  render(<ApplicantAttachmentsPanel id="a" canManage editable />);
  fireEvent.click(await screen.findByRole('button', { name: '画像を追加' }));
  await waitFor(() =>
    expect(screen.getByLabelText('追加する画像')).toHaveAccessibleDescription(
      '画像を選択してください'
    )
  );
  fireEvent.change(screen.getByLabelText('追加する画像'), {
    target: { files: [new File(['html'], 'unsafe.html', { type: 'text/html' })] },
  });
  fireEvent.click(screen.getByRole('button', { name: '画像を追加' }));
  await waitFor(() =>
    expect(screen.getByLabelText('追加する画像')).toHaveAccessibleDescription(
      'JPEG または PNG の画像を選択してください'
    )
  );
  expect(applicantAttachmentApi.upload).not.toHaveBeenCalled();
});
test('失敗後の再試行は同じファイルと操作キーを保持する', async () => {
  (applicantAttachmentApi.upload as jest.Mock).mockRejectedValue(new Error('通信失敗'));
  render(<ApplicantAttachmentsPanel id="a" canManage editable />);
  const file = new File(['png'], 'original.png', { type: 'image/png' });
  fireEvent.change(await screen.findByLabelText('追加する画像'), { target: { files: [file] } });
  fireEvent.click(screen.getByRole('button', { name: '画像を追加' }));
  await waitFor(() => expect(applicantAttachmentApi.upload).toHaveBeenCalledTimes(1));
  await waitFor(() => expect(screen.getByRole('button', { name: '画像を追加' })).toBeEnabled());
  fireEvent.change(screen.getByLabelText('追加する画像'), {
    target: { files: [new File(['png'], 'original.png', { type: 'image/png' })] },
  });
  fireEvent.click(screen.getByRole('button', { name: '画像を追加' }));
  await waitFor(() => expect(applicantAttachmentApi.upload).toHaveBeenCalledTimes(2));
  const calls = (applicantAttachmentApi.upload as jest.Mock).mock.calls;
  expect(calls[1]).toEqual(calls[0]);
  expect(calls[0][1]).toBe(file);
});
test('再送は保存済みの操作キーと識別子を使い成功後に未完了一覧を更新する', async () => {
  (applicantAttachmentApi.uploads as jest.Mock).mockResolvedValue({
    rows: [
      {
        id: 'pending',
        idempotency_key: 'original-key',
        status: 'RECOVERY_REQUIRED',
        media_type: 'image/png',
        size_bytes: 3,
        created_at: '2026-10-07T00:00:00Z',
      },
    ],
    nextCursor: null,
  });
  (applicantAttachmentApi.upload as jest.Mock).mockResolvedValue({ id: 'pending' });
  render(<ApplicantAttachmentsPanel id="a" canManage editable />);
  const file = new File(['png'], 'original.png', { type: 'image/png' });
  fireEvent.change(await screen.findByLabelText('最初に送信した画像'), {
    target: { files: [file] },
  });
  fireEvent.click(screen.getByRole('button', { name: '同じ画像を再送' }));
  await waitFor(() =>
    expect(applicantAttachmentApi.upload).toHaveBeenCalledWith(
      'a',
      file,
      'original-key',
      'pending',
      expect.any(AbortSignal)
    )
  );
  await waitFor(() => expect(applicantAttachmentApi.uploads).toHaveBeenCalledTimes(2));
});

test('画面を離れた後のダウンロード応答は保存操作を開始しない', async () => {
  (applicantAttachmentApi.list as jest.Mock).mockResolvedValue({
    rows: [
      { id: 'ready', media_type: 'image/png', size_bytes: 3, created_at: '2026-10-07T00:00:00Z' },
    ],
    nextCursor: null,
  });
  let resolve: (value: Blob) => void = () => {};
  (applicantAttachmentApi.download as jest.Mock).mockImplementation(
    () =>
      new Promise(done => {
        resolve = done;
      })
  );
  URL.createObjectURL = jest.fn();
  const view = render(<ApplicantAttachmentsPanel id="a" canManage={false} editable />);
  fireEvent.click(await screen.findByRole('button', { name: 'ダウンロード' }));
  view.unmount();
  const signal = (applicantAttachmentApi.download as jest.Mock).mock.calls[0][2];
  expect(signal.aborted).toBe(true);
  resolve(new Blob(['png']));
  await waitFor(() => expect(URL.createObjectURL).not.toHaveBeenCalled());
});

test('別の回復フォームで成功した操作を新規入力から取り除き次の画像を登録できる', async () => {
  const upload = applicantAttachmentApi.upload as jest.Mock;
  upload.mockRejectedValueOnce(new Error('応答消失')).mockResolvedValue({ id: 'ready' });
  (applicantAttachmentApi.uploads as jest.Mock).mockImplementation(async () => ({
    rows:
      upload.mock.calls.length === 1
        ? [
            {
              id: 'pending',
              idempotency_key: upload.mock.calls[0][2],
              status: 'RECOVERY_REQUIRED',
              media_type: 'image/png',
              size_bytes: 3,
              created_at: '2026-10-07T00:00:00Z',
            },
          ]
        : [],
    nextCursor: null,
  }));
  render(<ApplicantAttachmentsPanel id="a" canManage editable />);
  const original = new File(['png'], 'original.png', { type: 'image/png' });
  fireEvent.change(await screen.findByLabelText('追加する画像'), { target: { files: [original] } });
  fireEvent.click(screen.getByRole('button', { name: '画像を追加' }));
  fireEvent.change(await screen.findByLabelText('最初に送信した画像'), {
    target: { files: [original] },
  });
  fireEvent.click(screen.getByRole('button', { name: '同じ画像を再送' }));
  await waitFor(() =>
    expect(screen.queryByLabelText('最初に送信した画像')).not.toBeInTheDocument()
  );
  fireEvent.change(screen.getByLabelText('追加する画像'), {
    target: { files: [new File(['new'], 'next.png', { type: 'image/png' })] },
  });
  fireEvent.click(screen.getByRole('button', { name: '画像を追加' }));
  await waitFor(() => expect(upload).toHaveBeenCalledTimes(3));
  expect(upload.mock.calls[1][2]).toBe(upload.mock.calls[0][2]);
  expect(upload.mock.calls[2][2]).not.toBe(upload.mock.calls[0][2]);
});
test('回復失敗時は未完了フォームを再取得で破棄せず入力を残す', async () => {
  (applicantAttachmentApi.uploads as jest.Mock)
    .mockResolvedValueOnce({
      rows: [
        {
          id: 'pending',
          idempotency_key: 'original-key',
          status: 'RECOVERY_REQUIRED',
          media_type: 'image/png',
          size_bytes: 3,
          created_at: '2026-10-07T00:00:00Z',
        },
      ],
      nextCursor: null,
    })
    .mockRejectedValue(new Error('一覧取得不可'));
  (applicantAttachmentApi.upload as jest.Mock).mockRejectedValue(new Error('再送失敗'));
  render(<ApplicantAttachmentsPanel id="a" canManage editable />);
  const original = new File(['png'], 'original.png', { type: 'image/png' });
  const input = await screen.findByLabelText('最初に送信した画像');
  fireEvent.change(input, { target: { files: [original] } });
  fireEvent.click(screen.getByRole('button', { name: '同じ画像を再送' }));
  await waitFor(() => expect(applicantAttachmentApi.upload).toHaveBeenCalledTimes(1));
  await waitFor(() => expect(screen.getByRole('button', { name: '同じ画像を再送' })).toBeEnabled());
  expect((input as HTMLInputElement).files?.[0]).toBe(original);
  expect(applicantAttachmentApi.uploads).toHaveBeenCalledTimes(1);
});

test('安全コンテキスト専用UUID APIなしでも同じ契約の操作キーを生成する', async () => {
  const randomUuid = jest.spyOn(crypto, 'randomUUID').mockImplementation(() => {
    throw new Error('利用不可');
  });
  (applicantAttachmentApi.upload as jest.Mock).mockResolvedValue({ id: 'ready' });
  try {
    render(<ApplicantAttachmentsPanel id="a" canManage editable />);
    fireEvent.change(await screen.findByLabelText('追加する画像'), {
      target: { files: [new File(['png'], 'image.png', { type: 'image/png' })] },
    });
    fireEvent.click(screen.getByRole('button', { name: '画像を追加' }));
    await waitFor(() => expect(applicantAttachmentApi.upload).toHaveBeenCalledTimes(1));
    expect((applicantAttachmentApi.upload as jest.Mock).mock.calls[0][2]).toMatch(
      /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/
    );
    expect(randomUuid).not.toHaveBeenCalled();
  } finally {
    randomUuid.mockRestore();
  }
});
