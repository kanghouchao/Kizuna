import { act, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { SurveysPage } from '../ui/SurveysPage';
import { surveyApi, Revision, Answer, Survey } from '@/entities/survey';
import { readTokenClaims } from '@/shared/lib';
let mockStoreId = '1';
jest.mock('next/navigation', () => ({ useParams: () => ({ storeId: mockStoreId }) }));
jest.mock('@/entities/survey', () => ({
  surveyApi: {
    list: jest.fn(),
    survey: jest.fn(),
    revisions: jest.fn(),
    revision: jest.fn(),
    answers: jest.fn(),
    answer: jest.fn(),
    counts: jest.fn(),
    history: jest.fn(),
    write: jest.fn(),
  },
}));
jest.mock('@/shared/lib', () => ({
  ...jest.requireActual('@/shared/lib'),
  readTokenClaims: jest.fn(),
}));
jest.mock('@/shared/notify', () => ({ notify: { success: jest.fn(), error: jest.fn() } }));
const api = jest.mocked(surveyApi);
const claims = jest.mocked(readTokenClaims);
const permission = (...names: string[]) =>
  claims.mockReturnValue({
    userType: 'STAFF',
    storeBridge: true,
    authorities: names.map(p => `PERM_${p}`),
  });
const revision: Revision = {
  id: '20',
  survey_id: '10',
  revision_number: 1,
  title: '店舗の問票',
  status: 'OPEN',
  version: 1,
  created_at: '2026-01-01T00:00:00Z',
  opened_at: '2026-01-01T00:00:00Z',
  questions: [
    {
      question_key: 'q1',
      type: 'TEXT',
      prompt: '担当者が入力した質問',
      required: true,
      options: [],
    },
  ],
  created_by: { id: '1', display_name: '担当' },
  retention_policy: 'NOT_CONFIGURED',
};
const survey: Survey = {
  id: '10',
  latest_revision_id: '20',
  latest_revision_number: 1,
  latest_title: '店舗の問票',
  latest_status: 'OPEN',
  open_revision_id: '20',
  created_at: '2026-01-01T00:00:00Z',
  created_by: { id: '1', display_name: '担当' },
  retention_policy: 'NOT_CONFIGURED',
};
const answer: Answer = {
  id: '30',
  survey_id: '10',
  revision_id: '20',
  revision_number: 1,
  intake_source: 'STAFF_RECORDED',
  received_via: 'PAPER',
  received_at: '2026-01-01T00:00:00Z',
  created_at: '2026-01-01T00:00:00Z',
  status: 'ACTIVE',
  version: 0,
  answers: [{ question_key: 'q1', text: '保護された原回答' }],
  recorded_by: { id: '1', display_name: '担当' },
  retention_policy: 'NOT_CONFIGURED',
};
function fixtures() {
  api.list.mockResolvedValue({ rows: [survey], page: 0, pageCount: 1, total: 1 });
  api.survey.mockResolvedValue(survey);
  api.revisions.mockResolvedValue({ rows: [revision], page: 0, pageCount: 1, total: 1 });
  api.revision.mockResolvedValue(revision);
  api.answers.mockResolvedValue({ rows: [answer], page: 0, pageCount: 1, total: 1 });
  api.answer.mockResolvedValue(answer);
  api.counts.mockResolvedValue({
    survey_id: '10',
    revision_id: '20',
    total_records: 1,
    active_records: 1,
    withdrawn_records: 0,
  });
  api.history.mockResolvedValue({ rows: [], nextCursor: null });
}
async function showRevision() {
  fireEvent.click(await screen.findByRole('button', { name: '版と回答' }));
  fireEvent.click(await screen.findByRole('button', { name: '設問・回答' }));
  await screen.findByText('この版の記録件数');
}
async function createForm() {
  fireEvent.click(await screen.findByRole('button', { name: 'アンケートを作成' }));
  const dialog = await screen.findByRole('dialog');
  fireEvent.change(within(dialog).getByLabelText('題名'), { target: { value: '新しい問票' } });
  fireEvent.change(within(dialog).getByLabelText('設問文'), {
    target: { value: 'スタッフ入力の設問' },
  });
  return dialog;
}
beforeEach(() => {
  jest.resetAllMocks();
  mockStoreId = '1';
  permission('SURVEY_VIEW');
  api.list.mockResolvedValue({ rows: [], page: 0, pageCount: 0, total: 0 });
});
test('閲覧権限なしでは取得せず、閲覧のみでは設問作成を表示しない', async () => {
  permission('SURVEY_MANAGE');
  const view = render(<SurveysPage />);
  expect(screen.getByRole('alert')).toHaveTextContent('閲覧権限がありません');
  expect(api.list).not.toHaveBeenCalled();
  permission('SURVEY_VIEW');
  view.rerender(<SurveysPage />);
  expect(await screen.findByText('アンケートはありません')).toBeVisible();
  expect(screen.queryByRole('button', { name: 'アンケートを作成' })).not.toBeInTheDocument();
});

test('閲覧のみでは設問版・回答・記録件数を確認し、管理操作を持たない', async () => {
  fixtures();
  render(<SurveysPage />);
  await showRevision();
  expect(screen.getByText(/回答者数や回答率ではありません/)).toBeVisible();
  expect(screen.queryByRole('button', { name: '回答を受付' })).not.toBeInTheDocument();
  expect(screen.queryByRole('button', { name: '受付を終了' })).not.toBeInTheDocument();
  fireEvent.click(screen.getByRole('button', { name: '回答・履歴' }));
  expect(await screen.findByText('保護された原回答')).toBeVisible();
  expect(screen.queryByRole('button', { name: '回答を訂正' })).not.toBeInTheDocument();
});

test('受付権限は設問管理を含まず、任意回答を省略して原文と取得時刻を送る', async () => {
  fixtures();
  permission('SURVEY_VIEW', 'SURVEY_RECORD');
  api.revision.mockResolvedValue({
    ...revision,
    questions: [
      ...revision.questions,
      {
        question_key: 'optional',
        type: 'TEXT',
        prompt: '任意の設問',
        required: false,
        options: [],
      },
    ],
  });
  api.write.mockResolvedValue({
    answer,
    operation: {
      id: '50',
      type: 'RECEIVED',
      resource_id: '30',
      committed_version: 0,
      replayed: false,
    },
  });
  render(<SurveysPage />);
  await showRevision();
  expect(screen.queryByRole('button', { name: '受付を終了' })).not.toBeInTheDocument();
  fireEvent.click(screen.getByRole('button', { name: '回答を受付' }));
  fireEvent.change(await screen.findByLabelText('担当者が入力した質問（必須）'), {
    target: { value: '  入力した原文  ' },
  });
  fireEvent.change(screen.getByLabelText('受領日時'), { target: { value: '2026-01-01T09:00' } });
  fireEvent.click(screen.getByRole('button', { name: '回答を記録' }));
  await waitFor(() => expect(api.write).toHaveBeenCalledTimes(1));
  expect(api.write.mock.calls[0][0]).toMatchObject({
    kind: 'RECEIVE',
    sid: '10',
    rid: '20',
    input: { revision_version: 1, answers: [{ question_key: 'q1', text: '  入力した原文  ' }] },
  });
  expect(await screen.findByText('保護された原回答')).toBeVisible();
});

test('作成の結果不明を閉じても入力を保持し、同じキーで再確認する', async () => {
  fixtures();
  permission('SURVEY_VIEW', 'SURVEY_MANAGE');
  api.write.mockRejectedValueOnce(new Error('network')).mockResolvedValueOnce({
    revision,
    operation: {
      id: '50',
      type: 'DRAFT_CREATED',
      resource_id: '20',
      committed_version: 0,
      replayed: true,
    },
  });
  render(<SurveysPage />);
  const dialog = await createForm();
  fireEvent.click(within(dialog).getByRole('button', { name: '下書きを保存' }));
  await screen.findByText(/結果が確認できません。同じ要求/);
  const command = api.write.mock.calls[0][0];
  expect(command.input.dedupe_key).toBeTruthy();
  fireEvent.click(within(dialog).getByRole('button', { name: 'Close' }));
  await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
  expect(screen.getByRole('button', { name: 'アンケートを作成' })).toBeDisabled();
  fireEvent.click(screen.getByRole('button', { name: '入力画面へ戻る' }));
  expect(await screen.findByLabelText('題名')).toHaveValue('新しい問票');
  expect(screen.getByLabelText('設問文')).toHaveValue('スタッフ入力の設問');
  fireEvent.click(screen.getByRole('button', { name: '同じ要求で結果を確認' }));
  await waitFor(() => expect(api.write).toHaveBeenCalledTimes(2));
  expect(api.write.mock.calls[1][0]).toEqual(command);
});

test('送信連打は一度だけ実行し、店舗切替後の旧書込み結果を捨てる', async () => {
  fixtures();
  permission('SURVEY_VIEW', 'SURVEY_MANAGE');
  let finish!: (value: Awaited<ReturnType<typeof surveyApi.write>>) => void;
  api.write.mockReturnValue(
    new Promise(resolve => {
      finish = resolve;
    })
  );
  const view = render(<SurveysPage />);
  const dialog = await createForm();
  const save = within(dialog).getByRole('button', { name: '下書きを保存' });
  fireEvent.click(save);
  fireEvent.click(save);
  await waitFor(() => expect(api.write).toHaveBeenCalledTimes(1));
  mockStoreId = '2';
  api.list.mockResolvedValue({ rows: [], page: 0, pageCount: 0, total: 0 });
  view.rerender(<SurveysPage />);
  await screen.findByText('アンケートはありません');
  await act(async () =>
    finish({
      revision,
      operation: {
        id: '50',
        type: 'DRAFT_CREATED',
        resource_id: '20',
        committed_version: 0,
        replayed: false,
      },
    })
  );
  expect(screen.queryByText('この版の記録件数')).not.toBeInTheDocument();
  expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
});

test('権限拒否で機密の詳細と編集中の内容を破棄する', async () => {
  fixtures();
  permission('SURVEY_VIEW', 'SURVEY_RECORD');
  api.write.mockRejectedValue({ response: { status: 403 } });
  render(<SurveysPage />);
  await showRevision();
  fireEvent.click(screen.getByRole('button', { name: '回答・履歴' }));
  await screen.findByText('保護された原回答');
  fireEvent.click(screen.getByRole('button', { name: '回答を取り下げ' }));
  fireEvent.change(await screen.findByLabelText('操作理由'), { target: { value: '撤回の理由' } });
  fireEvent.click(screen.getByRole('button', { name: '確認へ' }));
  fireEvent.click(await screen.findByRole('button', { name: '確定する' }));
  await waitFor(() => expect(screen.getByRole('alert')).toHaveTextContent('現在の閲覧・操作権限'));
  expect(screen.queryByText('保護された原回答')).not.toBeInTheDocument();
  expect(screen.queryByLabelText('操作理由')).not.toBeInTheDocument();
});

test('受付409で入力を保持し、終了した版へ新しい要求を自動送信しない', async () => {
  fixtures();
  permission('SURVEY_VIEW', 'SURVEY_RECORD');
  api.write.mockRejectedValue({ response: { status: 409 } });
  render(<SurveysPage />);
  await showRevision();
  fireEvent.click(screen.getByRole('button', { name: '回答を受付' }));
  fireEvent.change(await screen.findByLabelText('担当者が入力した質問（必須）'), {
    target: { value: '保持する回答' },
  });
  fireEvent.change(screen.getByLabelText('受領日時'), { target: { value: '2026-01-01T09:00' } });
  fireEvent.click(screen.getByRole('button', { name: '回答を記録' }));
  await screen.findByText(/状態が更新されています/);
  api.revision.mockResolvedValue({ ...revision, status: 'CLOSED', version: 2 });
  fireEvent.click(screen.getByRole('button', { name: '最新状態を確認' }));
  await screen.findByText(/現在の状態ではこの操作を実行できません/);
  expect(screen.getByLabelText('担当者が入力した質問（必須）')).toHaveValue('保持する回答');
  expect(screen.getByRole('button', { name: '回答を記録' })).toBeDisabled();
  expect(api.write).toHaveBeenCalledTimes(1);
});

test.each([false, true])(
  '同じ版の訂正は日時変更=%sに従って受領日時を保持する',
  async changeReceivedAt => {
    fixtures();
    const originalReceivedAt = '2026-01-01T12:34:56.123456Z';
    api.answer.mockResolvedValue({ ...answer, received_at: originalReceivedAt });
    permission('SURVEY_VIEW', 'SURVEY_RECORD');
    api.write.mockResolvedValue({
      answer: { ...answer, id: '31', supersedes_id: '30' },
      operation: {
        id: '50',
        type: 'CORRECTION_RECEIVED',
        resource_id: '31',
        committed_version: 0,
        replayed: false,
      },
    });
    render(<SurveysPage />);
    await showRevision();
    fireEvent.click(screen.getByRole('button', { name: '回答・履歴' }));
    await screen.findByText('保護された原回答');
    fireEvent.click(screen.getByRole('button', { name: '回答を訂正' }));
    fireEvent.change(await screen.findByLabelText('担当者が入力した質問（必須）'), {
      target: { value: '訂正後の回答' },
    });
    if (changeReceivedAt) {
      fireEvent.change(screen.getByLabelText('受領日時'), {
        target: { value: '2026-01-02T10:15' },
      });
    }
    fireEvent.change(screen.getByLabelText('操作理由'), { target: { value: '転記の修正' } });
    fireEvent.click(screen.getByRole('button', { name: '確認へ' }));
    const confirm = await screen.findByRole('alertdialog');
    expect(within(confirm).getByText(/同じ版へ訂正/)).toBeVisible();
    fireEvent.click(within(confirm).getByRole('button', { name: '確定する' }));
    await waitFor(() => expect(api.write).toHaveBeenCalledTimes(1));
    expect(api.write.mock.calls[0][0]).toMatchObject({
      kind: 'CORRECT',
      aid: '30',
      input: {
        version: 0,
        reason: '転記の修正',
        received_at: changeReceivedAt
          ? new Date('2026-01-02T10:15').toISOString()
          : originalReceivedAt,
        answers: [{ question_key: 'q1', text: '訂正後の回答' }],
      },
    });
  }
);

test('遅れて失敗した旧一覧要求で新しい検索結果の権限を消さない', async () => {
  fixtures();
  let rejectOld!: (error: unknown) => void;
  api.list.mockReturnValueOnce(
    new Promise((_, reject) => {
      rejectOld = reject;
    })
  );
  render(<SurveysPage />);
  fireEvent.change(screen.getByLabelText('最新版の題名'), { target: { value: '新しい検索' } });
  fireEvent.click(screen.getByRole('button', { name: '検索' }));
  await screen.findByRole('button', { name: '版と回答' });
  await act(async () => rejectOld({ response: { status: 403 } }));
  expect(screen.getByRole('button', { name: '版と回答' })).toBeVisible();
  expect(screen.queryByText(/現在の閲覧・操作権限/)).not.toBeInTheDocument();
});

test('改版元404でも親アンケートが存在すればフォームを保持する', async () => {
  fixtures();
  permission('SURVEY_VIEW', 'SURVEY_MANAGE');
  api.write.mockRejectedValue({ response: { status: 404 } });
  render(<SurveysPage />);
  await showRevision();
  fireEvent.click(screen.getByRole('button', { name: '新しい版を作成' }));
  fireEvent.change(await screen.findByLabelText('題名'), { target: { value: '保持する改版案' } });
  fireEvent.click(screen.getByRole('button', { name: '下書きを保存' }));
  await screen.findByText(/対象が見つかりません。入力は保持/);
  api.revision.mockRejectedValue({ response: { status: 404 } });
  fireEvent.click(screen.getByRole('button', { name: '最新状態を確認' }));
  await waitFor(() => expect(api.survey).toHaveBeenCalledWith('10'));
  expect(screen.getByLabelText('題名')).toHaveValue('保持する改版案');
  expect(screen.getByRole('button', { name: '下書きを保存' })).toBeDisabled();
});

test('設問管理は版の開始を理由付きで確定し、回答受付を許さない', async () => {
  fixtures();
  permission('SURVEY_VIEW', 'SURVEY_MANAGE');
  api.revision.mockResolvedValue({ ...revision, status: 'DRAFT', version: 0 });
  api.survey.mockResolvedValue({
    ...survey,
    latest_status: 'DRAFT',
    open_revision_id: undefined,
    draft_revision_id: '20',
  });
  api.write.mockResolvedValue({
    revision,
    operation: {
      id: '50',
      type: 'OPENED',
      resource_id: '20',
      committed_version: 1,
      replayed: false,
    },
  });
  render(<SurveysPage />);
  await showRevision();
  expect(screen.queryByRole('button', { name: '回答を受付' })).not.toBeInTheDocument();
  fireEvent.click(screen.getByRole('button', { name: '受付を開始' }));
  fireEvent.change(await screen.findByLabelText('操作理由'), { target: { value: '設問を確認' } });
  fireEvent.click(screen.getByRole('button', { name: '確認へ' }));
  fireEvent.click(await screen.findByRole('button', { name: '確定する' }));
  await waitFor(() => expect(api.write).toHaveBeenCalledTimes(1));
  expect(api.write.mock.calls[0][0]).toMatchObject({
    kind: 'OPEN',
    sid: '10',
    rid: '20',
    input: { version: 0, reason: '設問を確認' },
  });
});

test('任意の単一選択は空から選択でき、空の質問票は保存しない', async () => {
  fixtures();
  permission('SURVEY_VIEW', 'SURVEY_MANAGE');
  render(<SurveysPage />);
  fireEvent.click(await screen.findByRole('button', { name: 'アンケートを作成' }));
  fireEvent.click(await screen.findByRole('button', { name: '下書きを保存' }));
  expect(await screen.findByText('題名を1〜120文字で入力してください')).toBeVisible();
  expect(screen.getByLabelText('題名')).toHaveAttribute('aria-invalid', 'true');
  await waitFor(() => expect(screen.getByLabelText('題名')).toHaveFocus());
  expect(api.write).not.toHaveBeenCalled();
  fireEvent.change(screen.getByLabelText('題名'), { target: { value: '選択の問票' } });
  fireEvent.change(screen.getByLabelText('設問文'), { target: { value: 'どちらですか' } });
  fireEvent.click(screen.getByLabelText('回答形式'));
  const option = await screen.findByRole('option', { name: '単一選択' });
  fireEvent.pointerDown(option);
  fireEvent.click(option);
  fireEvent.change(await screen.findByLabelText('選択肢 1'), { target: { value: '一つ目' } });
  fireEvent.change(screen.getByLabelText('選択肢 2'), { target: { value: '二つ目' } });
  api.write.mockResolvedValue({
    revision,
    operation: {
      id: '50',
      type: 'DRAFT_CREATED',
      resource_id: '20',
      committed_version: 0,
      replayed: false,
    },
  });
  fireEvent.click(screen.getByRole('button', { name: '下書きを保存' }));
  await waitFor(() => expect(api.write).toHaveBeenCalledTimes(1));
  expect(api.write.mock.calls[0][0]).toMatchObject({
    kind: 'CREATE',
    input: {
      questions: [{ type: 'SINGLE_CHOICE', options: [{ label: '一つ目' }, { label: '二つ目' }] }],
    },
  });
});

test('契約で許可された設問キーをフォーム内部のプロパティと混同しない', async () => {
  fixtures();
  permission('SURVEY_VIEW', 'SURVEY_RECORD');
  api.revision.mockResolvedValue({
    ...revision,
    questions: [{ ...revision.questions[0], question_key: '__proto__' }],
  });
  api.write.mockRejectedValue(new Error('network'));
  render(<SurveysPage />);
  await showRevision();
  fireEvent.click(screen.getByRole('button', { name: '回答を受付' }));
  fireEvent.change(await screen.findByLabelText('担当者が入力した質問（必須）'), {
    target: { value: '記録する回答' },
  });
  fireEvent.change(screen.getByLabelText('受領日時'), { target: { value: '2026-01-01T09:00' } });
  fireEvent.click(screen.getByRole('button', { name: '回答を記録' }));
  await waitFor(() => expect(api.write).toHaveBeenCalledTimes(1));
  expect(api.write.mock.calls[0][0]).toMatchObject({
    input: { answers: [{ question_key: '__proto__', text: '記録する回答' }] },
  });
});

test('対象から離れると閉じた未送信フォームを破棄する', async () => {
  fixtures();
  permission('SURVEY_VIEW', 'SURVEY_RECORD');
  render(<SurveysPage />);
  await showRevision();
  fireEvent.click(screen.getByRole('button', { name: '回答を受付' }));
  fireEvent.change(await screen.findByLabelText('担当者が入力した質問（必須）'), {
    target: { value: '対象固有の入力' },
  });
  fireEvent.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Close' }));
  fireEvent.click(screen.getByRole('button', { name: 'アンケート一覧へ' }));
  await screen.findByRole('button', { name: '版と回答' });
  expect(screen.queryByRole('button', { name: '入力画面へ戻る' })).not.toBeInTheDocument();
  await showRevision();
  fireEvent.click(screen.getByRole('button', { name: '回答を受付' }));
  expect(await screen.findByLabelText('担当者が入力した質問（必須）')).toHaveValue('');
});

test('改版競合では最新系列を確認し入力を保って明示的に新基準を選ぶ', async () => {
  fixtures();
  permission('SURVEY_VIEW', 'SURVEY_MANAGE');
  api.write.mockRejectedValue({ response: { status: 409 } });
  render(<SurveysPage />);
  await showRevision();
  fireEvent.click(screen.getByRole('button', { name: '新しい版を作成' }));
  fireEvent.change(await screen.findByLabelText('題名'), { target: { value: '自分の改版案' } });
  fireEvent.click(screen.getByRole('button', { name: '下書きを保存' }));
  await screen.findByText(/状態が更新されています/);
  api.survey.mockResolvedValue({ ...survey, latest_revision_id: '21', latest_revision_number: 2 });
  api.revision.mockImplementation(async (_sid, rid) => ({
    ...revision,
    id: rid,
    revision_number: rid === '21' ? 2 : 1,
  }));
  fireEvent.click(screen.getByRole('button', { name: '最新状態を確認' }));
  expect(await screen.findByText(/最新版は版 2/)).toBeVisible();
  expect(screen.getByLabelText('題名')).toHaveValue('自分の改版案');
  expect(screen.getByRole('button', { name: '下書きを保存' })).toBeDisabled();
  fireEvent.click(screen.getByRole('button', { name: 'この最新版を改版元にする' }));
  fireEvent.click(screen.getByRole('button', { name: '下書きを保存' }));
  await waitFor(() => expect(api.write).toHaveBeenCalledTimes(2));
  expect(api.write.mock.calls[1][0]).toMatchObject({
    input: { based_on_revision_id: '21', title: '自分の改版案' },
  });
});

test('改版元が不在でも系列確認の通信障害で入力を消さない', async () => {
  fixtures();
  permission('SURVEY_VIEW', 'SURVEY_MANAGE');
  api.write.mockRejectedValue({ response: { status: 404 } });
  render(<SurveysPage />);
  await showRevision();
  fireEvent.click(screen.getByRole('button', { name: '新しい版を作成' }));
  fireEvent.change(await screen.findByLabelText('題名'), { target: { value: '消さない改版案' } });
  fireEvent.click(screen.getByRole('button', { name: '下書きを保存' }));
  await screen.findByText(/対象が見つかりません。入力は保持/);
  api.survey.mockRejectedValue(new Error('network'));
  fireEvent.click(screen.getByRole('button', { name: '最新状態を確認' }));
  expect(await screen.findByText(/最新状態を確認できません。入力は保持しています/)).toBeVisible();
  expect(screen.getByLabelText('題名')).toHaveValue('消さない改版案');
});

test('詳細の404は再試行せず一覧へ戻す', async () => {
  fixtures();
  api.revision.mockRejectedValue({ response: { status: 404 } });
  render(<SurveysPage />);
  fireEvent.click(await screen.findByRole('button', { name: '版と回答' }));
  fireEvent.click(await screen.findByRole('button', { name: '設問・回答' }));
  expect(await screen.findByText('設問版が見つかりません')).toBeVisible();
  expect(screen.queryByRole('button', { name: /再試行/ })).not.toBeInTheDocument();
  fireEvent.click(screen.getByRole('button', { name: '一覧へ戻る' }));
  expect(await screen.findByRole('button', { name: '版と回答' })).toBeVisible();
});

test('数字の選択肢キーでも設問版に記録した順序で表示する', async () => {
  fixtures();
  permission('SURVEY_VIEW', 'SURVEY_RECORD');
  api.revision.mockResolvedValue({
    ...revision,
    questions: [
      {
        question_key: 'choice',
        type: 'SINGLE_CHOICE',
        prompt: '順序付き選択',
        required: true,
        options: [
          { option_key: '2', label: '先に表示' },
          { option_key: '1', label: '後に表示' },
        ],
      },
    ],
  });
  render(<SurveysPage />);
  await showRevision();
  fireEvent.click(screen.getByRole('button', { name: '回答を受付' }));
  fireEvent.click(await screen.findByLabelText('順序付き選択（必須）'));
  const options = await screen.findAllByRole('option');
  expect(options.map(option => option.textContent)).toEqual(['先に表示', '後に表示']);
});
