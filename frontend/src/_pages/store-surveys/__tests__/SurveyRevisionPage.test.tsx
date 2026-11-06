import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { SurveyRevisionPage } from '../ui/SurveyRevisionPage';
import {
  answer,
  api,
  fixtures,
  permission,
  resetSurveyTest,
  revision,
  survey,
  written,
} from '../testing/survey-test-support';

jest.mock('@/entities/user', () => ({
  ...jest.requireActual('@/entities/user'),
  useStoreContext: () => ({
    stores: [{ id: 1, name: 'テスト店舗' }],
    currentStoreId: '1',
    currentStore: { id: 1, name: 'テスト店舗' },
    loadFailed: false,
    reload: jest.fn(),
    switchStore: jest.fn(),
  }),
}));
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
jest.mock('@/entities/user/model/MeContext', () => ({
  ...jest.requireActual('@/entities/user/model/MeContext'),
  useMe: jest.fn(),
}));
jest.mock('@/shared/notify', () => ({ notify: { success: jest.fn(), error: jest.fn() } }));
const mockPush = jest.fn();
jest.mock('next/navigation', () => ({
  useRouter: () => ({ push: mockPush, replace: jest.fn(), back: jest.fn() }),
  useParams: () => ({ sid: '10', rid: '20' }),
}));

beforeEach(resetSurveyTest);

async function shown() {
  await screen.findByRole('heading', { level: 1, name: '店舗の問票' });
}

test('URL の版を直接表示し、一覧・ほかの版・回答の詳細へリンクで移る', async () => {
  fixtures();
  api.revisions.mockResolvedValue({
    rows: [{ ...revision, id: '21', revision_number: 2 }, revision],
    page: 0,
    pageCount: 1,
    total: 2,
  });
  render(<SurveyRevisionPage />);
  await shown();
  expect(api.revision).toHaveBeenCalledWith('10', '20');
  expect(screen.getByRole('link', { name: 'アンケート一覧へ' })).toHaveAttribute(
    'href',
    '/store/surveys'
  );
  const versions = screen.getByRole('heading', { level: 2, name: '設問版' }).closest('section')!;
  expect(
    (await within(versions).findAllByRole('link', { name: '設問・回答' })).map(link =>
      link.getAttribute('href')
    )
  ).toEqual(['/store/surveys/10/revisions/21']);
  expect(within(versions).getByText('表示中')).toBeVisible();
  expect(await screen.findByRole('link', { name: '回答・履歴' })).toHaveAttribute(
    'href',
    '/store/surveys/responses/30'
  );
  expect(screen.getByText(/回答者数や回答率ではありません/)).toBeVisible();
  expect(screen.queryByRole('button', { name: '回答を受付' })).not.toBeInTheDocument();
  expect(screen.queryByRole('button', { name: '受付を終了' })).not.toBeInTheDocument();
});

test('見出しは h1 の下に h2 だけを置く', async () => {
  fixtures();
  render(<SurveyRevisionPage />);
  await shown();
  await screen.findByRole('link', { name: '回答・履歴' });
  expect(screen.getAllByRole('heading', { level: 1 })).toHaveLength(1);
  expect(screen.getAllByRole('heading', { level: 2 }).map(heading => heading.textContent)).toEqual([
    '設問の内容',
    'この版の記録件数',
    '回答記録',
    '設問版',
    '操作履歴',
  ]);
});

test('版の操作履歴をページ番号で送り、次のページを取り直す', async () => {
  fixtures();
  const event = (id: string, reason: string) => ({
    id,
    type: 'OPENED' as const,
    created_at: '2026-01-01T00:00:00Z',
    actor: { id: '1', display_name: '担当' },
    after_version: 1,
    after_status: 'OPEN',
    reason,
  });
  api.history
    .mockResolvedValueOnce({ rows: [event('h2', '新しい操作')], page: 0, pageCount: 2, total: 2 })
    .mockResolvedValueOnce({ rows: [event('h1', '古い操作')], page: 1, pageCount: 2, total: 2 });
  render(<SurveyRevisionPage />);
  await shown();
  const section = screen.getByRole('heading', { name: '操作履歴' }).closest('section')!;
  expect(await within(section).findByText('新しい操作')).toBeVisible();
  fireEvent.click(within(section).getAllByRole('button', { name: '次へ' })[0]);
  expect(await within(section).findByText('古い操作')).toBeVisible();
  expect(api.history).toHaveBeenLastCalledWith({ sid: '10', rid: '20' }, 1);
});

test('回答の状態で絞り込み、回答一覧を取り直す', async () => {
  fixtures();
  render(<SurveyRevisionPage />);
  await shown();
  await screen.findByRole('link', { name: '回答・履歴' });
  fireEvent.click(screen.getByLabelText('回答の状態'));
  const option = await screen.findByRole('option', { name: '取り下げ済み' });
  fireEvent.pointerDown(option);
  fireEvent.click(option);
  fireEvent.click(screen.getByRole('button', { name: '検索' }));
  await waitFor(() =>
    expect(api.answers).toHaveBeenLastCalledWith('10', '20', 0, { status: 'WITHDRAWN' })
  );
});

test('受付の開始は理由だけを入力する短いダイアログで確定し、同じ版を取り直す', async () => {
  fixtures();
  permission('SURVEY_VIEW', 'SURVEY_MANAGE');
  api.revision.mockResolvedValue({ ...revision, status: 'DRAFT', version: 0 });
  api.survey.mockResolvedValue({
    ...survey,
    latest_status: 'DRAFT',
    open_revision_id: undefined,
    draft_revision_id: '20',
  });
  api.write.mockResolvedValue(written('OPENED', { revision }));
  render(<SurveyRevisionPage />);
  await shown();
  expect(screen.queryByRole('button', { name: '回答を受付' })).not.toBeInTheDocument();
  fireEvent.click(screen.getByRole('button', { name: '受付を開始' }));
  const dialog = await screen.findByRole('dialog', { name: '受付を開始' });
  fireEvent.click(within(dialog).getByRole('button', { name: '確定する' }));
  expect(await within(dialog).findByText('操作理由を1〜500文字で入力してください')).toBeVisible();
  expect(api.write).not.toHaveBeenCalled();
  api.revision.mockResolvedValue(revision);
  fireEvent.change(within(dialog).getByLabelText('操作理由'), { target: { value: '設問を確認' } });
  fireEvent.click(within(dialog).getByRole('button', { name: '確定する' }));
  await waitFor(() => expect(api.write).toHaveBeenCalledTimes(1));
  expect(api.write.mock.calls[0][0]).toEqual({
    kind: 'OPEN',
    sid: '10',
    rid: '20',
    input: { version: 0, reason: '設問を確認' },
  });
  expect(await screen.findByText('受付中')).toBeVisible();
  expect(screen.queryByRole('button', { name: '受付を開始' })).not.toBeInTheDocument();
  expect(api.revision).toHaveBeenCalledTimes(2);
  await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
  expect(mockPush).not.toHaveBeenCalled();
});

test('受付の終了も理由を付けて確定する', async () => {
  fixtures();
  permission('SURVEY_VIEW', 'SURVEY_MANAGE');
  api.write.mockResolvedValue(written('CLOSED', { revision: { ...revision, status: 'CLOSED' } }));
  render(<SurveyRevisionPage />);
  await shown();
  fireEvent.click(screen.getByRole('button', { name: '受付を終了' }));
  const dialog = await screen.findByRole('dialog', { name: '受付を終了' });
  fireEvent.change(within(dialog).getByLabelText('操作理由'), { target: { value: '期間満了' } });
  fireEvent.click(within(dialog).getByRole('button', { name: '確定する' }));
  await waitFor(() => expect(api.write).toHaveBeenCalledTimes(1));
  expect(api.write.mock.calls[0][0]).toEqual({
    kind: 'CLOSE',
    sid: '10',
    rid: '20',
    input: { version: 1, reason: '期間満了' },
  });
});

test('受付権限は設問管理を含まず、回答を記録したらその回答の詳細へ移る', async () => {
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
  api.write.mockResolvedValue(written('RECEIVED', { answer }));
  render(<SurveyRevisionPage />);
  await shown();
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
  await waitFor(() => expect(mockPush).toHaveBeenCalledWith('/store/surveys/responses/30'));
});

test('受付409で入力を保持し、終了した版へ新しい要求を自動送信しない', async () => {
  fixtures();
  permission('SURVEY_VIEW', 'SURVEY_RECORD');
  api.write.mockRejectedValue({ response: { status: 409 } });
  render(<SurveyRevisionPage />);
  await shown();
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

test('改版元404でも親アンケートが存在すればフォームを保持する', async () => {
  fixtures();
  permission('SURVEY_VIEW', 'SURVEY_MANAGE');
  api.write.mockRejectedValue({ response: { status: 404 } });
  render(<SurveyRevisionPage />);
  await shown();
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

test('改版競合では最新系列を確認し入力を保って明示的に新基準を選び、新しい版へ移る', async () => {
  fixtures();
  permission('SURVEY_VIEW', 'SURVEY_MANAGE');
  api.write.mockRejectedValueOnce({ response: { status: 409 } });
  render(<SurveyRevisionPage />);
  await shown();
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
  api.write.mockResolvedValueOnce(
    written('DRAFT_CREATED', { revision: { ...revision, id: '22', status: 'DRAFT' } })
  );
  fireEvent.click(screen.getByRole('button', { name: '下書きを保存' }));
  await waitFor(() => expect(api.write).toHaveBeenCalledTimes(2));
  expect(api.write.mock.calls[1][0]).toMatchObject({
    input: { based_on_revision_id: '21', title: '自分の改版案' },
  });
  await waitFor(() => expect(mockPush).toHaveBeenCalledWith('/store/surveys/10/revisions/22'));
});

test('改版元が不在でも系列確認の通信障害で入力を消さない', async () => {
  fixtures();
  permission('SURVEY_VIEW', 'SURVEY_MANAGE');
  api.write.mockRejectedValue({ response: { status: 404 } });
  render(<SurveyRevisionPage />);
  await shown();
  fireEvent.click(screen.getByRole('button', { name: '新しい版を作成' }));
  fireEvent.change(await screen.findByLabelText('題名'), { target: { value: '消さない改版案' } });
  fireEvent.click(screen.getByRole('button', { name: '下書きを保存' }));
  await screen.findByText(/対象が見つかりません。入力は保持/);
  api.survey.mockRejectedValue(new Error('network'));
  fireEvent.click(screen.getByRole('button', { name: '最新状態を確認' }));
  expect(await screen.findByText(/最新状態を確認できません。入力は保持しています/)).toBeVisible();
  expect(screen.getByLabelText('題名')).toHaveValue('消さない改版案');
});

test('版の404は再試行を出さず一覧へのリンクを出す', async () => {
  fixtures();
  api.revision.mockRejectedValue({ response: { status: 404 } });
  render(<SurveyRevisionPage />);
  expect(await screen.findByText('設問版が見つかりません')).toBeVisible();
  expect(screen.queryByRole('button', { name: /再試行/ })).not.toBeInTheDocument();
  expect(screen.getByRole('link', { name: '一覧へ戻る' })).toHaveAttribute(
    'href',
    '/store/surveys'
  );
});

test('契約で許可された設問キーをフォーム内部のプロパティと混同しない', async () => {
  fixtures();
  permission('SURVEY_VIEW', 'SURVEY_RECORD');
  api.revision.mockResolvedValue({
    ...revision,
    questions: [{ ...revision.questions[0], question_key: '__proto__' }],
  });
  // 送信内容だけを確かめる。業務拒否で操作を確定させ、後の用例へ結果不明を残さない。
  api.write.mockRejectedValue({ response: { status: 422 } });
  render(<SurveyRevisionPage />);
  await shown();
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
  render(<SurveyRevisionPage />);
  await shown();
  fireEvent.click(screen.getByRole('button', { name: '回答を受付' }));
  fireEvent.click(await screen.findByLabelText('順序付き選択（必須）'));
  const options = await screen.findAllByRole('option');
  expect(options.map(option => option.textContent)).toEqual(['先に表示', '後に表示']);
});
