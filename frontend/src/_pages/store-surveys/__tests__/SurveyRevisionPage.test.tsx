import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { SurveyRevisionPage } from '../ui/SurveyRevisionPage';
import {
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
  expect(screen.queryByRole('link', { name: '回答を受付' })).not.toBeInTheDocument();
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
  expect(screen.queryByRole('link', { name: '回答を受付' })).not.toBeInTheDocument();
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
  // 状態は版一覧のセルにも出るので、読み直した見出しの直下のバッジで確かめる
  await waitFor(() => {
    const heading = screen.getByRole('heading', { level: 1, name: '店舗の問票' });
    expect(within(heading.parentElement!).getByText('受付中')).toBeVisible();
  });
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

test('受付権限は設問管理を含まず、回答の受付は受付画面へのリンクで移る', async () => {
  fixtures();
  permission('SURVEY_VIEW', 'SURVEY_RECORD');
  render(<SurveyRevisionPage />);
  await shown();
  expect(screen.queryByRole('button', { name: '受付を終了' })).not.toBeInTheDocument();
  expect(screen.getByRole('link', { name: '回答を受付' })).toHaveAttribute(
    'href',
    '/store/surveys/10/revisions/20/responses/new'
  );
});

test('下書きの版は差替画面へ、最新版は改訂画面へリンクする', async () => {
  fixtures();
  permission('SURVEY_VIEW', 'SURVEY_MANAGE');
  api.revision.mockResolvedValue({ ...revision, status: 'DRAFT', version: 0 });
  api.survey.mockResolvedValue({ ...survey, open_revision_id: undefined });
  render(<SurveyRevisionPage />);
  await shown();
  expect(screen.getByRole('link', { name: '下書きを編集' })).toHaveAttribute(
    'href',
    '/store/surveys/10/revisions/20/edit'
  );
  expect(screen.getByRole('link', { name: '新しい版を作成' })).toHaveAttribute(
    'href',
    '/store/surveys/10/revisions/new?from=20'
  );
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
