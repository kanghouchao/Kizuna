import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { SurveyCorrectPage } from '../ui/SurveyCorrectPage';
import { SurveyReceivePage } from '../ui/SurveyReceivePage';
import {
  answer,
  api,
  fixtures,
  permission,
  resetSurveyTest,
  revision,
  written,
} from '../testing/survey-test-support';

jest.mock('@/widgets/admin-shell', () => ({
  DeniedPage: () => <p role="alert">権限の無い画面</p>,
}));

jest.mock('@/entities/user', () => ({
  ...jest.requireActual('@/entities/user'),
  useCurrentStoreId: () => '1',
  useStoreContext: () => ({
    stores: [{ id: 1, name: 'テスト店舗' }],
    currentStoreId: '1',
    currentStore: { id: 1, name: 'テスト店舗' },
    loadFailed: false,
    reload: jest.fn(),
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
const mockReplace = jest.fn();
let mockParams: Record<string, string> = {};
jest.mock('next/navigation', () => ({
  useRouter: () => ({ push: jest.fn(), replace: mockReplace, back: jest.fn() }),
  useParams: () => mockParams,
}));

beforeEach(() => {
  resetSurveyTest();
  fixtures();
  permission('SURVEY_VIEW', 'SURVEY_RECORD');
});

describe('回答の受付画面', () => {
  beforeEach(() => {
    mockParams = { sid: '10', rid: '20' };
  });

  async function shown() {
    await screen.findByRole('heading', { level: 1, name: '回答を受付' });
    await waitFor(() => expect(screen.queryByText('読み込み中...')).not.toBeInTheDocument());
  }

  test('受付権限が無ければ入力を出さない', async () => {
    permission('SURVEY_VIEW', 'SURVEY_MANAGE');
    render(<SurveyReceivePage />);
    expect(await screen.findByRole('alert')).toHaveTextContent('権限の無い画面');
    expect(screen.queryByLabelText('受領日時')).not.toBeInTheDocument();
    expect(api.revision).not.toHaveBeenCalled();
  });

  test('URL の版へ回答を記録し、記録した回答の詳細へ移る', async () => {
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
    render(<SurveyReceivePage />);
    await shown();
    expect(api.revision).toHaveBeenCalledWith('10', '20');
    expect(screen.getByRole('link', { name: '設問版へ戻る' })).toHaveAttribute(
      'href',
      '/store/1/surveys/10/revisions/20'
    );
    fireEvent.change(screen.getByLabelText('担当者が入力した質問（必須）'), {
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
    await waitFor(() => expect(mockReplace).toHaveBeenCalledWith('/store/1/surveys/responses/30'));
  });

  test('受付中でない版では入力を出さない', async () => {
    api.revision.mockResolvedValue({ ...revision, status: 'CLOSED' });
    render(<SurveyReceivePage />);
    await shown();
    expect(await screen.findByText(/この設問版は受付中ではありません/)).toBeVisible();
    expect(screen.queryByRole('button', { name: '回答を記録' })).not.toBeInTheDocument();
  });

  test('受付409で入力を保持し、終了した版へ新しい要求を自動送信しない', async () => {
    api.write.mockRejectedValue({ response: { status: 409 } });
    render(<SurveyReceivePage />);
    await shown();
    fireEvent.change(screen.getByLabelText('担当者が入力した質問（必須）'), {
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

  test('最新状態の確認が通信障害でも入力を消さない', async () => {
    api.write.mockRejectedValue({ response: { status: 409 } });
    render(<SurveyReceivePage />);
    await shown();
    fireEvent.change(screen.getByLabelText('担当者が入力した質問（必須）'), {
      target: { value: '消さない回答' },
    });
    fireEvent.change(screen.getByLabelText('受領日時'), { target: { value: '2026-01-01T09:00' } });
    fireEvent.click(screen.getByRole('button', { name: '回答を記録' }));
    await screen.findByText(/状態が更新されています/);
    api.revision.mockRejectedValue(new Error('network'));
    fireEvent.click(screen.getByRole('button', { name: '最新状態を確認' }));
    expect(await screen.findByText(/最新状態を確認できません。入力は保持しています/)).toBeVisible();
    expect(screen.getByLabelText('担当者が入力した質問（必須）')).toHaveValue('消さない回答');
  });

  test('結果不明の受付は同じ要求で確認するまで新しい送信を止める', async () => {
    api.write.mockRejectedValueOnce(new Error('network'));
    render(<SurveyReceivePage />);
    await shown();
    fireEvent.change(screen.getByLabelText('担当者が入力した質問（必須）'), {
      target: { value: '結果不明の回答' },
    });
    fireEvent.change(screen.getByLabelText('受領日時'), { target: { value: '2026-01-01T09:00' } });
    fireEvent.click(screen.getByRole('button', { name: '回答を記録' }));
    await screen.findByText(/結果が確認できません/);
    expect(screen.getByRole('button', { name: '回答を記録' })).toBeDisabled();
    api.write.mockResolvedValueOnce(written('RECEIVED', { answer }));
    fireEvent.click(screen.getByRole('button', { name: '同じ要求で結果を確認' }));
    await waitFor(() => expect(api.write).toHaveBeenCalledTimes(2));
    expect(api.write.mock.calls[1][0]).toEqual(api.write.mock.calls[0][0]);
    await waitFor(() => expect(mockReplace).toHaveBeenCalledWith('/store/1/surveys/responses/30'));
  });

  test('契約で許可された設問キーをフォーム内部のプロパティと混同しない', async () => {
    api.revision.mockResolvedValue({
      ...revision,
      questions: [{ ...revision.questions[0], question_key: '__proto__' }],
    });
    // 送信内容だけを確かめる。業務拒否で操作を確定させ、後の用例へ結果不明を残さない。
    api.write.mockRejectedValue({ response: { status: 422 } });
    render(<SurveyReceivePage />);
    await shown();
    fireEvent.change(screen.getByLabelText('担当者が入力した質問（必須）'), {
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
    render(<SurveyReceivePage />);
    await shown();
    fireEvent.click(screen.getByLabelText('順序付き選択（必須）'));
    const options = await screen.findAllByRole('option');
    expect(options.map(option => option.textContent)).toEqual(['先に表示', '後に表示']);
  });

  test('版の404は再試行を出さず一覧へのリンクを出す', async () => {
    api.revision.mockRejectedValue({ response: { status: 404 } });
    render(<SurveyReceivePage />);
    expect(await screen.findByText('設問版が見つかりません')).toBeVisible();
    expect(screen.queryByRole('button', { name: /再試行/ })).not.toBeInTheDocument();
    expect(screen.getByRole('link', { name: '一覧へ戻る' })).toHaveAttribute(
      'href',
      '/store/1/surveys'
    );
  });
});

describe('回答の訂正画面', () => {
  beforeEach(() => {
    mockParams = { aid: '30' };
  });

  async function shown() {
    await screen.findByRole('heading', { level: 1, name: '回答を訂正' });
    await waitFor(() => expect(screen.queryByText('読み込み中...')).not.toBeInTheDocument());
  }

  test.each([false, true])(
    '同じ版の訂正は日時変更=%sに従って受領日時を保持し、訂正後の回答へ移る',
    async changeReceivedAt => {
      const originalReceivedAt = '2026-01-01T12:34:56.123456Z';
      api.answer.mockResolvedValue({ ...answer, received_at: originalReceivedAt });
      api.write.mockResolvedValue(
        written('CORRECTION_RECEIVED', { answer: { ...answer, id: '31', supersedes_id: '30' } })
      );
      render(<SurveyCorrectPage />);
      await shown();
      expect(screen.getByRole('link', { name: '回答へ戻る' })).toHaveAttribute(
        'href',
        '/store/1/surveys/responses/30'
      );
      expect(screen.getByLabelText('担当者が入力した質問（必須）')).toHaveValue('保護された原回答');
      fireEvent.change(screen.getByLabelText('担当者が入力した質問（必須）'), {
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
      await waitFor(() =>
        expect(mockReplace).toHaveBeenCalledWith('/store/1/surveys/responses/31')
      );
    }
  );

  test('訂正済みの回答では入力を出さない', async () => {
    api.answer.mockResolvedValue({ ...answer, superseded_by_id: '31' });
    render(<SurveyCorrectPage />);
    await shown();
    expect(await screen.findByText(/この回答は訂正済みです/)).toBeVisible();
    expect(screen.queryByLabelText('操作理由')).not.toBeInTheDocument();
  });

  test('訂正済みで開き直しても、結果不明の訂正を同じ要求で確認できる', async () => {
    api.write.mockRejectedValueOnce(new Error('network'));
    const { unmount } = render(<SurveyCorrectPage />);
    await shown();
    fireEvent.change(screen.getByLabelText('操作理由'), { target: { value: '転記の修正' } });
    fireEvent.click(screen.getByRole('button', { name: '確認へ' }));
    fireEvent.click(
      within(await screen.findByRole('alertdialog')).getByRole('button', { name: '確定する' })
    );
    await screen.findByText(/結果が確認できません/);
    unmount();
    api.answer.mockResolvedValue({ ...answer, superseded_by_id: '31' });
    render(<SurveyCorrectPage />);
    await shown();
    expect(screen.getByText(/この回答は訂正済みです/)).toBeVisible();
    api.write.mockResolvedValueOnce(
      written('CORRECTION_RECEIVED', { answer: { ...answer, id: '31', supersedes_id: '30' } })
    );
    fireEvent.click(screen.getByRole('button', { name: '同じ要求で結果を確認' }));
    await waitFor(() => expect(mockReplace).toHaveBeenCalledWith('/store/1/surveys/responses/31'));
  });

  test('権限拒否で機密の回答と編集中の内容を破棄する', async () => {
    api.write.mockRejectedValue({ response: { status: 403 } });
    render(<SurveyCorrectPage />);
    await shown();
    fireEvent.change(screen.getByLabelText('操作理由'), { target: { value: '転記の修正' } });
    fireEvent.click(screen.getByRole('button', { name: '確認へ' }));
    fireEvent.click(
      within(await screen.findByRole('alertdialog')).getByRole('button', { name: '確定する' })
    );
    await waitFor(() =>
      expect(screen.getByRole('alert')).toHaveTextContent('現在の閲覧・操作権限')
    );
    expect(screen.queryByDisplayValue('保護された原回答')).not.toBeInTheDocument();
    expect(screen.queryByLabelText('操作理由')).not.toBeInTheDocument();
  });

  test('回答の404は再試行を出さず一覧へのリンクを出す', async () => {
    api.answer.mockRejectedValue({ response: { status: 404 } });
    render(<SurveyCorrectPage />);
    expect(await screen.findByText('回答が見つかりません')).toBeVisible();
    expect(screen.getByRole('link', { name: '一覧へ戻る' })).toHaveAttribute(
      'href',
      '/store/1/surveys'
    );
  });
});
