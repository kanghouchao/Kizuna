import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { customerApi, MergePreview, MergeProfile } from '@/entities/customer';
import { CustomerMergeConfirmDialog } from '../ui/CustomerMergeConfirmDialog';

jest.mock('@/entities/customer', () => ({
  customerApi: { mergePreview: jest.fn(), merge: jest.fn() },
}));
jest.mock('@/shared/notify', () => ({ notify: { success: jest.fn(), error: jest.fn() } }));
const api = customerApi as jest.Mocked<typeof customerApi>;
const profile: MergeProfile = {
  name: '存続',
  address: null,
  building_name: null,
  landmark: null,
  classification: null,
  has_pet: null,
  usage_areas: null,
  ng_type: null,
  ng_content: '犬に注意',
};
export const preview: MergePreview = {
  surviving: { id: 'a', profile, contacts: [], member_links: [] },
  merged: { id: 'b', profile: { ...profile, name: '被統合' }, contacts: [], member_links: [] },
  profile,
  preferred_contacts: { phone: null, email: null, line: null },
  preference_conflicts: [],
  member_linked: false,
  unfinished_order_count: 2,
  moved_order_count: 1,
  moved_contact_count: 0,
  moved_link_count: 0,
  preview_token: 'proof',
};
beforeEach(() => {
  jest.clearAllMocks();
  api.mergePreview.mockResolvedValue(preview);
});
function open() {
  render(
    <CustomerMergeConfirmDialog
      open
      survivingId="a"
      mergedId="b"
      onMerged={jest.fn()}
      onClose={jest.fn()}
    />
  );
}
it('資料と注意事項の確認前は統合を実行できない', async () => {
  open();
  expect(await screen.findByRole('button', { name: '統合する' })).toBeDisabled();
  expect(
    screen.getByRole('checkbox', {
      name: '双方の注意事項・確定資料・優先指定・会員への影響を確認しました',
    })
  ).toHaveAttribute('aria-disabled', 'true');
});
it('理由・プレビュー・注意事項を確認した資料で実行する', async () => {
  api.merge.mockResolvedValue({
    merge_id: 'm',
    surviving_customer_id: 'a',
    moved_order_count: 1,
    moved_contact_count: 0,
    moved_link_count: 0,
  });
  open();
  fireEvent.change(await screen.findByLabelText('統合理由'), {
    target: { value: '同一人物を確認' },
  });
  fireEvent.click(screen.getByRole('button', { name: '被統合側の氏名を採用' }));
  fireEvent.click(screen.getByRole('button', { name: '確定資料でプレビュー' }));
  await waitFor(() =>
    expect(
      screen.getByRole('checkbox', {
        name: '双方の注意事項・確定資料・優先指定・会員への影響を確認しました',
      })
    ).not.toHaveAttribute('aria-disabled', 'true')
  );
  fireEvent.click(
    screen.getByRole('checkbox', {
      name: '双方の注意事項・確定資料・優先指定・会員への影響を確認しました',
    })
  );
  fireEvent.click(screen.getByRole('button', { name: '統合する' }));
  fireEvent.click(await screen.findByRole('button', { name: '統合を確定' }));
  await waitFor(() =>
    expect(api.merge).toHaveBeenCalledWith(
      'a',
      expect.objectContaining({
        profile: { ...profile, name: '被統合' },
        warnings_acknowledged: true,
        operation_reason: '同一人物を確認',
        preview_token: 'proof',
      })
    )
  );
});
it('競合後も入力を保持し、再確認するまで実行を止める', async () => {
  api.merge.mockRejectedValue({
    response: { status: 409, data: { error: '関連情報が変更されました' } },
  });
  open();
  fireEvent.change(await screen.findByLabelText('統合理由'), { target: { value: '重複確認' } });
  fireEvent.click(screen.getByRole('button', { name: '確定資料でプレビュー' }));
  await waitFor(() =>
    expect(
      screen.getByRole('checkbox', {
        name: '双方の注意事項・確定資料・優先指定・会員への影響を確認しました',
      })
    ).not.toHaveAttribute('aria-disabled', 'true')
  );
  fireEvent.click(
    screen.getByRole('checkbox', {
      name: '双方の注意事項・確定資料・優先指定・会員への影響を確認しました',
    })
  );
  fireEvent.click(screen.getByRole('button', { name: '統合する' }));
  fireEvent.click(await screen.findByRole('button', { name: '統合を確定' }));
  expect(await screen.findByText('関連情報が変更されました')).toBeInTheDocument();
  expect(screen.getByLabelText('統合理由')).toHaveValue('重複確認');
  expect(screen.getByRole('button', { name: '統合する' })).toBeDisabled();
});

it('プレビュー後に理由を消した場合は入力欄で訂正を求める', async () => {
  open();
  fireEvent.change(await screen.findByLabelText('統合理由'), { target: { value: '確認済み' } });
  fireEvent.click(screen.getByRole('button', { name: '確定資料でプレビュー' }));
  const checkbox = screen.getByRole('checkbox', {
    name: '双方の注意事項・確定資料・優先指定・会員への影響を確認しました',
  });
  await waitFor(() => expect(checkbox).not.toHaveAttribute('aria-disabled', 'true'));
  fireEvent.click(checkbox);
  fireEvent.change(screen.getByLabelText('統合理由'), { target: { value: '  ' } });
  fireEvent.click(screen.getByRole('button', { name: '統合する' }));
  expect(await screen.findByText('統合理由を入力してください')).toBeInTheDocument();
  expect(api.merge).not.toHaveBeenCalled();
  expect(checkbox).toHaveAttribute('aria-checked', 'true');
});

it('選択欄は内部値ではなく日本語のラベルを表示する', async () => {
  open();
  expect(await screen.findByRole('combobox', { name: 'ペットの有無' })).toHaveTextContent('未確認');
  expect(screen.getByRole('combobox', { name: 'メールの優先連絡先' })).toHaveTextContent(
    '指定なし'
  );
});

it('優先連絡先の競合は送信時に関連付けたエラーと最初の欄へのフォーカスで示す', async () => {
  api.mergePreview.mockResolvedValueOnce({
    ...preview,
    preference_conflicts: ['PHONE', 'EMAIL'],
    preview_token: undefined,
  });
  open();
  fireEvent.change(await screen.findByLabelText('統合理由'), { target: { value: '確認済み' } });
  const submit = screen.getByRole('button', { name: '確定資料でプレビュー' });
  expect(submit).toBeEnabled();
  fireEvent.click(submit);
  const phone = screen.getByRole('combobox', { name: '電話の優先連絡先' });
  await waitFor(() => expect(phone).toHaveFocus());
  expect(phone).toHaveAttribute('aria-invalid', 'true');
  expect(phone).toHaveAccessibleDescription('優先連絡先を一件、または指定なしを選択してください');
  expect(screen.getByRole('combobox', { name: 'メールの優先連絡先' })).toHaveAttribute(
    'aria-invalid',
    'true'
  );
  expect(api.mergePreview).toHaveBeenCalledTimes(1);
  for (const name of ['電話の優先連絡先', 'メールの優先連絡先']) {
    fireEvent.click(screen.getByRole('combobox', { name }));
    const option = await screen.findByRole('option', { name: '指定なし' });
    fireEvent.pointerDown(option);
    fireEvent.click(option);
    await waitFor(() =>
      expect(screen.queryByRole('option', { name: '指定なし' })).not.toBeInTheDocument()
    );
  }
  fireEvent.click(submit);
  await waitFor(() => expect(api.mergePreview).toHaveBeenCalledTimes(2));
  expect(api.mergePreview).toHaveBeenLastCalledWith(
    'a',
    expect.objectContaining({ preferred_contacts: { phone: null, email: null, line: null } })
  );
});
