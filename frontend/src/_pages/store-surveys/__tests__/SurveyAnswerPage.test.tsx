import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { SurveyAnswerPage } from '../ui/SurveyAnswerPage';
import {
  answer,
  api,
  fixtures,
  permission,
  resetSurveyTest,
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
  useParams: () => ({ aid: '30' }),
}));

beforeEach(resetSurveyTest);

async function shown() {
  await screen.findByRole('heading', { level: 1, name: '回答 #30' });
}

test('URL の回答を直接表示し、閲覧のみでは記録の操作を持たない', async () => {
  fixtures();
  api.answer.mockResolvedValue({ ...answer, supersedes_id: '29' });
  render(<SurveyAnswerPage />);
  await shown();
  expect(api.answer).toHaveBeenCalledWith('30');
  expect(screen.getByText('保護された原回答')).toBeVisible();
  expect(screen.getByRole('link', { name: '設問版へ戻る' })).toHaveAttribute(
    'href',
    '/store/surveys/10/revisions/20'
  );
  expect(screen.getByRole('link', { name: '訂正元を表示' })).toHaveAttribute(
    'href',
    '/store/surveys/responses/29'
  );
  expect(screen.queryByRole('button', { name: '回答を訂正' })).not.toBeInTheDocument();
  expect(screen.queryByRole('button', { name: '回答を取り下げ' })).not.toBeInTheDocument();
});

test('取り下げは理由を入力する短いダイアログで確定し、同じ回答を取り直す', async () => {
  fixtures();
  permission('SURVEY_VIEW', 'SURVEY_RECORD');
  api.write.mockResolvedValue(written('WITHDRAWN', { answer: { ...answer, status: 'WITHDRAWN' } }));
  render(<SurveyAnswerPage />);
  await shown();
  fireEvent.click(screen.getByRole('button', { name: '回答を取り下げ' }));
  const dialog = await screen.findByRole('dialog', { name: '回答を取り下げ' });
  api.answer.mockResolvedValue({ ...answer, status: 'WITHDRAWN', version: 1 });
  fireEvent.change(within(dialog).getByLabelText('操作理由'), { target: { value: '撤回の理由' } });
  fireEvent.click(within(dialog).getByRole('button', { name: '確定する' }));
  await waitFor(() => expect(api.write).toHaveBeenCalledTimes(1));
  expect(api.write.mock.calls[0][0]).toEqual({
    kind: 'WITHDRAW',
    aid: '30',
    input: { version: 0, reason: '撤回の理由' },
  });
  expect(await screen.findByText(/取り下げ済み ／ 設問版/)).toBeVisible();
  expect(screen.queryByRole('button', { name: '回答を取り下げ' })).not.toBeInTheDocument();
  expect(mockPush).not.toHaveBeenCalled();
});

test('権限拒否で機密の詳細と編集中の内容を破棄する', async () => {
  fixtures();
  permission('SURVEY_VIEW', 'SURVEY_RECORD');
  api.write.mockRejectedValue({ response: { status: 403 } });
  render(<SurveyAnswerPage />);
  await shown();
  fireEvent.click(screen.getByRole('button', { name: '回答を取り下げ' }));
  fireEvent.change(await screen.findByLabelText('操作理由'), { target: { value: '撤回の理由' } });
  fireEvent.click(screen.getByRole('button', { name: '確定する' }));
  await waitFor(() => expect(screen.getByRole('alert')).toHaveTextContent('現在の閲覧・操作権限'));
  expect(screen.queryByText('保護された原回答')).not.toBeInTheDocument();
  expect(screen.queryByLabelText('操作理由')).not.toBeInTheDocument();
});

test('回答の404は再試行を出さず一覧へのリンクを出す', async () => {
  fixtures();
  api.answer.mockRejectedValue({ response: { status: 404 } });
  render(<SurveyAnswerPage />);
  expect(await screen.findByText('回答が見つかりません')).toBeVisible();
  expect(screen.queryByRole('button', { name: /再試行/ })).not.toBeInTheDocument();
  expect(screen.getByRole('link', { name: '一覧へ戻る' })).toHaveAttribute(
    'href',
    '/store/surveys'
  );
});

test.each([false, true])(
  '同じ版の訂正は日時変更=%sに従って受領日時を保持し、訂正後の回答へ移る',
  async changeReceivedAt => {
    fixtures();
    const originalReceivedAt = '2026-01-01T12:34:56.123456Z';
    api.answer.mockResolvedValue({ ...answer, received_at: originalReceivedAt });
    permission('SURVEY_VIEW', 'SURVEY_RECORD');
    api.write.mockResolvedValue(
      written('CORRECTION_RECEIVED', { answer: { ...answer, id: '31', supersedes_id: '30' } })
    );
    render(<SurveyAnswerPage />);
    await shown();
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
    await waitFor(() => expect(mockPush).toHaveBeenCalledWith('/store/surveys/responses/31'));
  }
);
