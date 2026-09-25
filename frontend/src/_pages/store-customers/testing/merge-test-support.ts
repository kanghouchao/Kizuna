import { fireEvent, screen, waitFor, within } from '@testing-library/react';
import { MergePreview, MergePreviewRequest, MergeProfile } from '@/entities/customer';

export function mergePreviewFixture(
  id: string,
  request: MergePreviewRequest
): Promise<MergePreview> {
  const profile: MergeProfile = {
    name: id === 'c1' ? '山田太郎' : 'ヤマダタロウ',
    address: null,
    building_name: null,
    landmark: null,
    classification: null,
    has_pet: null,
    usage_areas: null,
    ng_type: null,
    ng_content: null,
  };
  return Promise.resolve({
    surviving: { id, profile, contacts: [], member_links: [] },
    merged: {
      id: request.merged_customer_id,
      profile: { ...profile, name: id === 'c1' ? 'ヤマダタロウ' : '山田太郎' },
      contacts: [],
      member_links: [],
    },
    profile: request.profile ?? profile,
    preferred_contacts: request.preferred_contacts ?? { phone: null, email: null, line: null },
    preference_conflicts: [],
    member_linked: false,
    unfinished_order_count: 0,
    moved_order_count: 9,
    moved_contact_count: 0,
    moved_link_count: 0,
    preview_token: 'proof',
  });
}
export async function confirmReviewedMerge() {
  const dialog = await screen.findByRole('dialog');
  fireEvent.change(await within(dialog).findByLabelText('統合理由'), {
    target: { value: '重複を確認' },
  });
  fireEvent.click(within(dialog).getByRole('button', { name: '確定資料でプレビュー' }));
  const checkbox = within(dialog).getByRole('checkbox', {
    name: '双方の注意事項・確定資料・優先指定・会員への影響を確認しました',
  });
  await waitFor(() => expect(checkbox).not.toHaveAttribute('aria-disabled', 'true'));
  fireEvent.click(checkbox);
  fireEvent.click(within(dialog).getByRole('button', { name: '統合する' }));
  fireEvent.click(await screen.findByRole('button', { name: '統合を確定' }));
}
