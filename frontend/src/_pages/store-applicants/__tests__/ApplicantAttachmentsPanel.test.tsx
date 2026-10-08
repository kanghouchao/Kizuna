import { act, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { applicantAttachmentApi } from '@/entities/applicant';
import { ApplicantAttachmentsPanel } from '../ui/ApplicantAttachmentsPanel';
jest.mock('@/entities/applicant', () => ({
  applicantAttachmentApi: {
    policy: jest.fn(),
    list: jest.fn(),
    uploads: jest.fn(),
    operation: jest.fn(),
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
const pending = {
  id: 'pending',
  idempotency_key: 'original-key',
  status: 'RECOVERY_REQUIRED',
  failure_code: 'STORAGE_UNAVAILABLE',
  media_type: 'image/png',
  size_bytes: 3,
  created_at: '2026-10-07T00:00:00Z',
};
const page = (rows: unknown[] = []) => ({ rows, nextCursor: null });
const missing = { response: { status: 404 } };
const file = () => new File(['png'], 'original.png', { type: 'image/png' });
const upload = applicantAttachmentApi.upload as jest.Mock;
const operation = applicantAttachmentApi.operation as jest.Mock;
const uploads = applicantAttachmentApi.uploads as jest.Mock;
const readyList = applicantAttachmentApi.list as jest.Mock;
beforeEach(() => {
  jest.resetAllMocks();
  (applicantAttachmentApi.policy as jest.Mock).mockResolvedValue(policy);
  readyList.mockResolvedValue(page());
  uploads.mockResolvedValue(page());
  operation.mockRejectedValue(missing);
});
async function select(label = '追加する画像', selected = file()) {
  const input = await screen.findByLabelText(label);
  fireEvent.change(input, { target: { files: [selected] } });
  return input;
}
function send(input: HTMLElement) {
  fireEvent.submit(input.closest('form')!);
}
function resend(input: HTMLElement) {
  return within(input.closest('form')!).getByRole('button', { name: '同じ画像を再送' });
}

test('一覧行を取得できていれば別の初期状態照会を発行せず再送できる', async () => {
  uploads.mockResolvedValueOnce(page([pending])).mockRejectedValue(new Error('冗長照会'));
  render(<ApplicantAttachmentsPanel id="a" canManage editable />);
  const input = await select('最初に送信した画像');
  expect(resend(input)).toBeEnabled();
  expect(uploads).toHaveBeenCalledTimes(1);
  expect(operation).not.toHaveBeenCalled();
});

test.each(['POST', 'PUT'])(
  '%sの応答消失を正確なREADYで照合し次の画像に新しいキーを使う',
  async method => {
    if (method === 'PUT')
      uploads.mockResolvedValue(page([{ ...pending, failure_code: 'CONTENT_MISMATCH' }]));
    upload.mockRejectedValueOnce(new Error('応答消失')).mockResolvedValue({ id: 'next' });
    operation.mockImplementation(async (_id, key) => ({
      ...pending,
      idempotency_key: key,
      status: 'READY',
      failure_code: undefined,
    }));
    readyList.mockImplementation(async () => page(upload.mock.calls.length ? [pending] : []));
    render(<ApplicantAttachmentsPanel id="a" canManage editable />);
    const input = await select(method === 'PUT' ? '最初に送信した画像' : '追加する画像');
    if (method === 'PUT') fireEvent.click(screen.getByRole('checkbox'));
    send(input);
    expect(await screen.findByRole('button', { name: 'ダウンロード' })).toBeVisible();
    await waitFor(() => expect(screen.getByLabelText('追加する画像')).toBeEnabled());
    expect(screen.queryByLabelText('最初に送信した画像')).not.toBeInTheDocument();
    const second = new File(['next'], 'next.png', { type: 'image/png' });
    send(await select('追加する画像', second));
    await waitFor(() => expect(upload).toHaveBeenCalledTimes(2));
    expect(upload.mock.calls[1][1]).toBe(second);
    expect(upload.mock.calls[1][2]).not.toBe(upload.mock.calls[0][2]);
    expect(operation).toHaveBeenCalledWith('a', upload.mock.calls[0][2]);
  }
);

test('同じ描画中の二重送信を遮断し送信中の入力を維持する', async () => {
  upload.mockImplementation(() => new Promise(() => {}));
  render(<ApplicantAttachmentsPanel id="a" canManage editable />);
  const input = await select();
  await act(async () => {
    send(input);
    send(input);
  });
  expect(upload).toHaveBeenCalledTimes(1);
  expect(input).toBeDisabled();
});

test('404は元Fileとキーを保持して重送でき、別の画像に置換しない', async () => {
  upload.mockRejectedValueOnce(new Error('未予約で断線')).mockResolvedValue({ id: 'ready' });
  render(<ApplicantAttachmentsPanel id="a" canManage editable />);
  const original = file();
  const input = await select('追加する画像', original);
  send(input);
  await waitFor(() => expect(resend(input)).toBeEnabled());
  expect(input).toBeDisabled();
  fireEvent.change(input, {
    target: { files: [new File(['other'], 'other.png', { type: 'image/png' })] },
  });
  fireEvent.click(resend(input));
  await waitFor(() => expect(upload).toHaveBeenCalledTimes(2));
  expect(upload.mock.calls[1][1]).toBe(original);
  expect(upload.mock.calls[1][2]).toBe(upload.mock.calls[0][2]);
  await waitFor(() => expect(input).toBeEnabled());
});

test('404後の状態再確認で遅れて完了した元POSTを確認し重送せず清掃する', async () => {
  upload.mockRejectedValue(new Error('応答消失'));
  render(<ApplicantAttachmentsPanel id="a" canManage editable />);
  const input = await select();
  send(input);
  await waitFor(() => expect(resend(input)).toBeEnabled());
  operation.mockResolvedValue({
    ...pending,
    status: 'READY',
    idempotency_key: upload.mock.calls[0][2],
  });
  fireEvent.click(screen.getByRole('button', { name: '状態を再確認' }));
  await waitFor(() => expect(input).toBeEnabled());
  expect(screen.getByRole('button', { name: '画像を追加' })).toBeEnabled();
  expect(upload).toHaveBeenCalledTimes(1);
});

test('無関係なREADY行と空の未完了一覧から自分の成功を推測しない', async () => {
  readyList.mockResolvedValue(page([{ ...pending, id: 'other-ready' }]));
  upload.mockRejectedValue(new Error('応答消失'));
  render(<ApplicantAttachmentsPanel id="a" canManage editable />);
  const input = await select();
  send(input);
  await waitFor(() => expect(resend(input)).toBeEnabled());
  expect(input).toBeDisabled();
  expect(screen.getByRole('button', { name: 'ダウンロード' })).toBeVisible();
});

test('400の確定拒否と未予約を確認すれば同じキーで画像を訂正できる', async () => {
  upload
    .mockRejectedValueOnce({ response: { status: 400, data: { error: '画像を解読できません' } } })
    .mockResolvedValue({ id: 'ready' });
  render(<ApplicantAttachmentsPanel id="a" canManage editable />);
  const input = await select();
  send(input);
  expect(await screen.findByText(/画像が拒否され、予約も確認されませんでした/)).toBeVisible();
  expect(input).toBeEnabled();
  const corrected = new File(['fixed'], 'fixed.png', { type: 'image/png' });
  fireEvent.change(input, { target: { files: [corrected] } });
  send(input);
  await waitFor(() => expect(upload).toHaveBeenCalledTimes(2));
  expect(upload.mock.calls[1][1]).toBe(corrected);
  expect(upload.mock.calls[1][2]).toBe(upload.mock.calls[0][2]);
});

test('400でも既存の予約があれば元Fileを置換できない', async () => {
  upload.mockRejectedValue({ response: { status: 400 } });
  operation.mockImplementation(async (_id, key) => ({ ...pending, idempotency_key: key }));
  render(<ApplicantAttachmentsPanel id="a" canManage editable />);
  const input = await select();
  send(input);
  await waitFor(() => expect(resend(input)).toBeEnabled());
  expect(input).toBeDisabled();
});

test.each(['PENDING', 'STORAGE_UNAVAILABLE', 'NORMALIZER_UNAVAILABLE'])(
  '精確照合の%sは同じFileとキーで重送する',
  async state => {
    uploads.mockResolvedValue(page([pending]));
    upload.mockRejectedValue(new Error('再送失敗'));
    operation.mockResolvedValue({
      ...pending,
      status: state === 'PENDING' ? 'PENDING' : 'RECOVERY_REQUIRED',
      failure_code: state === 'PENDING' ? undefined : state,
    });
    render(<ApplicantAttachmentsPanel id="a" canManage editable />);
    const original = file();
    const input = await select('最初に送信した画像', original);
    send(input);
    await waitFor(() => expect(operation).toHaveBeenCalledTimes(1));
    await waitFor(() => expect(resend(input)).toBeEnabled());
    fireEvent.click(resend(input));
    await waitFor(() => expect(upload).toHaveBeenCalledTimes(2));
    expect(upload.mock.calls[1][1]).toBe(original);
    expect(upload.mock.calls[1][2]).toBe('original-key');
    expect(screen.queryByRole('checkbox')).not.toBeInTheDocument();
  }
);

test('新しいCONTENT_MISMATCHを旧pending快照で上書きせず失敗後は修復確認を解除する', async () => {
  uploads.mockResolvedValue(page([pending]));
  upload.mockRejectedValue(new Error('保存失敗'));
  operation.mockResolvedValue({ ...pending, failure_code: 'CONTENT_MISMATCH' });
  render(<ApplicantAttachmentsPanel id="a" canManage editable />);
  const input = await select('最初に送信した画像');
  send(input);
  const button = await screen.findByRole('button', { name: '修復後の同じ画像を再送' });
  expect(button).toBeDisabled();
  fireEvent.click(screen.getByRole('checkbox'));
  fireEvent.click(button);
  await waitFor(() => expect(upload).toHaveBeenCalledTimes(2));
  await waitFor(() => expect(button).toBeDisabled());
  expect(screen.getByRole('checkbox')).not.toBeChecked();
  expect(uploads).toHaveBeenCalledTimes(1);
});

test('一操作の照合失敗は他の行を禁用せずファイルを保持して照会のみ再試行する', async () => {
  uploads.mockResolvedValue(
    page([pending, { ...pending, id: 'other', idempotency_key: 'other-key' }])
  );
  upload.mockRejectedValue(new Error('応答消失'));
  operation.mockRejectedValue(new Error('照会失敗'));
  render(<ApplicantAttachmentsPanel id="a" canManage editable />);
  const inputs = await screen.findAllByLabelText('最初に送信した画像');
  const original = file();
  fireEvent.change(inputs[0], { target: { files: [original] } });
  send(inputs[0]);
  expect(
    await screen.findByText(
      '操作の状態を確認できません。ファイルを保持したまま状態を再確認してください。'
    )
  ).toBeVisible();
  expect(resend(inputs[0])).toBeDisabled();
  expect(resend(inputs[1])).toBeEnabled();
  expect((inputs[0] as HTMLInputElement).files?.[0]).toBe(original);
  operation.mockResolvedValue({ ...pending, status: 'READY' });
  fireEvent.click(screen.getByRole('button', { name: '再試行' }));
  await waitFor(() => expect(screen.getAllByLabelText('最初に送信した画像')).toHaveLength(1));
  expect(upload).toHaveBeenCalledTimes(1);
});

test('照会中は失敗表示せず最新要求の結果だけを使う', async () => {
  upload.mockRejectedValue(new Error('応答消失'));
  operation.mockRejectedValueOnce(missing);
  render(<ApplicantAttachmentsPanel id="a" canManage editable />);
  const input = await select();
  send(input);
  await waitFor(() => expect(resend(input)).toBeEnabled());
  let finish!: (value: unknown) => void;
  operation.mockImplementationOnce(
    () =>
      new Promise(resolve => {
        finish = resolve;
      })
  );
  fireEvent.click(screen.getByRole('button', { name: '状態を再確認' }));
  expect(await screen.findByRole('status')).toHaveTextContent('操作の状態を確認中');
  expect(resend(input)).toBeDisabled();
  expect(screen.queryByRole('button', { name: '再試行' })).not.toBeInTheDocument();
  await act(async () =>
    finish({
      ...pending,
      idempotency_key: upload.mock.calls[0][2],
      failure_code: 'CONTENT_MISMATCH',
    })
  );
  expect(await screen.findByRole('button', { name: '修復後の同じ画像を再送' })).toBeDisabled();
});

test('新規操作がpendingに見えても同じキーの二重フォームを作らない', async () => {
  let finishList!: (value: unknown) => void;
  uploads.mockImplementation(
    () =>
      new Promise(resolve => {
        finishList = resolve;
      })
  );
  upload.mockRejectedValue(new Error('応答消失'));
  operation.mockImplementation(async (_id, key) => ({
    ...pending,
    idempotency_key: key,
    failure_code: 'CONTENT_MISMATCH',
  }));
  render(<ApplicantAttachmentsPanel id="a" canManage editable />);
  const input = await select();
  send(input);
  expect(await screen.findByRole('checkbox')).toBeVisible();
  await act(async () =>
    finishList(page([{ ...pending, idempotency_key: upload.mock.calls[0][2] }]))
  );
  expect(screen.getAllByRole('checkbox')).toHaveLength(1);
  expect(screen.queryByLabelText('最初に送信した画像')).not.toBeInTheDocument();
});

test('応募者切替後の旧照会は新フォームを清掃せず送信を中止する', async () => {
  let finish!: (value: unknown) => void;
  upload.mockRejectedValue(new Error('応答消失'));
  operation.mockImplementation(
    () =>
      new Promise(resolve => {
        finish = resolve;
      })
  );
  const view = render(<ApplicantAttachmentsPanel id="a" canManage editable />);
  const input = await select();
  send(input);
  await waitFor(() => expect(operation).toHaveBeenCalledTimes(1));
  const oldKey = upload.mock.calls[0][2];
  const signal = upload.mock.calls[0][4];
  view.rerender(<ApplicantAttachmentsPanel id="b" canManage editable />);
  const next = new File(['next'], 'next.png', { type: 'image/png' });
  const newInput = await select('追加する画像', next);
  await act(async () => finish({ ...pending, status: 'READY', idempotency_key: oldKey }));
  expect(signal.aborted).toBe(true);
  expect((newInput as HTMLInputElement).files?.[0]).toBe(next);
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

test('同じキーの古いREADY応答は新しい内容不一致を清掃しない', async () => {
  upload.mockRejectedValue(new Error('応答消失'));
  render(<ApplicantAttachmentsPanel id="a" canManage editable />);
  const input = await select();
  send(input);
  await waitFor(() => expect(resend(input)).toBeEnabled());
  const key = upload.mock.calls[0][2];
  let old!: (value: unknown) => void;
  operation
    .mockImplementationOnce(
      () =>
        new Promise(resolve => {
          old = resolve;
        })
    )
    .mockResolvedValueOnce({ ...pending, idempotency_key: key, failure_code: 'CONTENT_MISMATCH' });
  const query = screen.getByRole('button', { name: '状態を再確認' });
  await act(async () => {
    fireEvent.click(query);
    fireEvent.click(query);
  });
  expect(await screen.findByRole('button', { name: '修復後の同じ画像を再送' })).toBeDisabled();
  await act(async () => old({ ...pending, idempotency_key: key, status: 'READY' }));
  expect(screen.getByRole('button', { name: '修復後の同じ画像を再送' })).toBeDisabled();
  expect(input).toBeDisabled();
});

test('回復の原画像不一致409は予約を照合して正しい原画像を選び直せる', async () => {
  uploads.mockResolvedValue(page([pending]));
  operation.mockResolvedValue(pending);
  upload
    .mockRejectedValueOnce({
      response: {
        status: 409,
        data: { error: '元のアップロードと同じ画像・形式・操作キーで再送してください' },
      },
    })
    .mockResolvedValue({ id: pending.id });
  render(<ApplicantAttachmentsPanel id="a" canManage editable />);
  const input = await select(
    '最初に送信した画像',
    new File(['wrong'], 'wrong.png', { type: 'image/png' })
  );
  send(input);
  await waitFor(() => expect(operation).toHaveBeenCalledTimes(1));
  await waitFor(() => expect(input).toBeEnabled());
  const original = file();
  fireEvent.change(input, { target: { files: [original] } });
  send(input);
  await waitFor(() => expect(upload).toHaveBeenCalledTimes(2));
  expect(upload.mock.calls[1][1]).toBe(original);
  expect(upload.mock.calls[1][2]).toBe('original-key');
});

test.each(['同じ画像を処理中です', '元の画像変換方式を利用できません。回復を待ってください'])(
  '409の%sでは原Fileを置換しない',
  async message => {
    uploads.mockResolvedValue(page([pending]));
    operation.mockResolvedValue({ ...pending, status: 'PENDING', failure_code: undefined });
    upload.mockRejectedValue({ response: { status: 409, data: { error: message } } });
    render(<ApplicantAttachmentsPanel id="a" canManage editable />);
    const original = file();
    const input = await select('最初に送信した画像', original);
    send(input);
    await waitFor(() => expect(operation).toHaveBeenCalledTimes(1));
    await waitFor(() => expect(resend(input)).toBeEnabled());
    expect(input).toBeDisabled();
    fireEvent.change(input, {
      target: { files: [new File(['other'], 'other.png', { type: 'image/png' })] },
    });
    send(input);
    await waitFor(() => expect(upload).toHaveBeenCalledTimes(2));
    expect(upload.mock.calls[1][1]).toBe(original);
  }
);
