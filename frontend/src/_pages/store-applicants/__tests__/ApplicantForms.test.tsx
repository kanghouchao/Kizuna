import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { ApplicantIntakeForm } from '../ui/ApplicantIntakeForm';
import { ApplicantInterviewForm } from '../ui/ApplicantInterviewForm';
import { mediumApi } from '@/entities/medium';
// MediumSelect は API を slice 内の相対 import で読むため、barrel ではなく実体を差し替える。
jest.mock('@/entities/medium/api/medium', () => ({ mediumApi: { options: jest.fn() } }));
async function pick(combobox: string, option: string) {
  await waitFor(() => expect(screen.getByRole('combobox', { name: combobox })).toBeEnabled());
  fireEvent.click(screen.getByRole('combobox', { name: combobox }));
  const item = await screen.findByRole('option', { name: option });
  fireEvent.pointerDown(item);
  fireEvent.click(item);
}
test('応募元が媒体なら媒体を一覧から選ばせ、ID で送る', async () => {
  (mediumApi.options as jest.Mock).mockResolvedValue([{ id: 'm1', name: '求人誌' }]);
  const save = jest.fn().mockResolvedValue(undefined);
  render(<ApplicantIntakeForm onSave={save} />);
  fireEvent.change(screen.getByLabelText('氏名'), { target: { value: '応募者' } });
  await pick('応募元区分', '媒体');
  fireEvent.click(screen.getByRole('button', { name: '受付情報を保存' }));
  expect(await screen.findByText('応募元媒体を選択してください')).toBeVisible();
  expect(save).not.toHaveBeenCalled();

  await pick('応募元媒体', '求人誌');
  fireEvent.click(screen.getByRole('button', { name: '受付情報を保存' }));
  await waitFor(() => expect(save).toHaveBeenCalled());
  expect(save.mock.calls[0][0]).toMatchObject({ source_type: 'MEDIA', medium_id: 'm1' });
  expect(save.mock.calls[0][0]).not.toHaveProperty('medium');
});
test('編集では停止中の媒体を値として保つ', async () => {
  (mediumApi.options as jest.Mock).mockResolvedValue([{ id: 'm2', name: '有効' }]);
  const save = jest.fn().mockResolvedValue(undefined);
  const detail = {
    name: '応募者',
    channel: 'WEB' as const,
    source_type: 'MEDIA' as const,
    medium: { id: 'm1', name: '止めた媒体' },
    referrer: null,
    assignee: null,
    phone: null,
    email: null,
    address: null,
    experience: null,
    desired_conditions: null,
  };
  render(<ApplicantIntakeForm initial={detail} onSave={save} />);
  await waitFor(() =>
    expect(screen.getByRole('combobox', { name: '応募元媒体' })).toHaveTextContent(
      '止めた媒体（停止中）'
    )
  );
  fireEvent.click(screen.getByRole('button', { name: '受付情報を保存' }));
  await waitFor(() => expect(save).toHaveBeenCalled());
  expect(save.mock.calls[0][0]).toMatchObject({ medium_id: 'm1' });
});
test('氏名が空なら登録せず関連付けたエラーを表示する', async () => {
  const save = jest.fn();
  render(<ApplicantIntakeForm onSave={save} />);
  fireEvent.click(screen.getByRole('button', { name: '受付情報を保存' }));
  expect(await screen.findByText('氏名を入力してください')).toBeVisible();
  expect(save).not.toHaveBeenCalled();
  expect(screen.getByLabelText('氏名')).toHaveAttribute('aria-invalid', 'true');
});
test('詳細の状態や面接記録を受付更新に混入させない', async () => {
  const save = jest.fn().mockResolvedValue(undefined);
  const detail = {
    name: '応募者',
    channel: 'WEB' as const,
    source_type: 'DIRECT' as const,
    medium: null,
    referrer: '',
    assignee: '',
    phone: '',
    email: '',
    address: '',
    experience: '',
    desired_conditions: '',
    status: 'HIRED',
    interview: { notes: '機微情報' },
  };
  render(<ApplicantIntakeForm initial={detail} onSave={save} />);
  fireEvent.click(screen.getByRole('button', { name: '受付情報を保存' }));
  await waitFor(() => expect(save).toHaveBeenCalled());
  expect(save.mock.calls[0][0]).not.toHaveProperty('status');
  expect(save.mock.calls[0][0]).not.toHaveProperty('interview');
});
test('面接日時と担当者を要求しチェック項目を追加できる', async () => {
  const save = jest.fn();
  render(<ApplicantInterviewForm onSave={save} />);
  fireEvent.click(screen.getByRole('button', { name: '面接記録を保存' }));
  expect(await screen.findByText('面接日時を入力してください')).toBeVisible();
  expect(await screen.findByText('面接担当者を入力してください')).toBeVisible();
  fireEvent.click(screen.getByRole('button', { name: '確認項目を追加' }));
  expect(screen.getByLabelText('確認項目1の名前')).toBeVisible();
  expect(save).not.toHaveBeenCalled();
});
