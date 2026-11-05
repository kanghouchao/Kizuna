import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { serviceIdentityApi } from '@/entities/user';
import type { ServiceIdentityResponse } from '@/entities/user';
import { useKeyedResource } from '@/shared/lib';
import { notify } from '@/shared/notify';
import { ServiceIdentityCreateModal } from '../ServiceIdentityCreateModal';
import { ServiceIdentityEditModal } from '../ServiceIdentityEditModal';

jest.mock('@/entities/user', () => ({
  serviceIdentityApi: { grantableRoles: jest.fn(), create: jest.fn(), update: jest.fn() },
}));
jest.mock('@/shared/notify', () => ({
  notify: { success: jest.fn(), error: jest.fn(), warning: jest.fn() },
}));

const cases = [
  {
    name: 'サービスID作成',
    Create: ServiceIdentityCreateModal,
    api: serviceIdentityApi,
    roles: serviceIdentityApi.grantableRoles,
  },
  {
    name: 'サービスID編集',
    Edit: ServiceIdentityEditModal,
    api: serviceIdentityApi,
    roles: serviceIdentityApi.grantableRoles,
  },
];
const props = {
  stores: [{ id: 9, name: '店舗A' }],
  storesLoading: false,
  storesFailed: false,
  onReloadStores: jest.fn(),
  onClose: jest.fn(),
  onCreated: jest.fn(),
  onUpdated: jest.fn(),
};

function Harness({ entry }: { entry: (typeof cases)[number] }) {
  return entry.Create ? <entry.Create {...props} /> : <EditHarness />;
}

function EditHarness() {
  const service = useKeyedResource<ServiceIdentityResponse>(['service', 42], async () => ({
    id: 42,
    display_name: '対象名',
    enabled: true,
    roles: [{ id: 3, name: '担当者' }],
    store_scope_type: 'SPECIFIC_STORES' as const,
    store_ids: [],
    version: 7,
  }));
  return <ServiceIdentityEditModal {...props} resource={service} />;
}

async function prepare(entry: (typeof cases)[number]) {
  render(<Harness entry={entry} />);
  await screen.findByRole('checkbox', { name: '担当者' });
  if (entry.Create) {
    fireEvent.change(screen.getByLabelText('用途名'), { target: { value: '対象名' } });
    fireEvent.click(screen.getByRole('checkbox', { name: '担当者' }));
  }
}
beforeEach(() => {
  jest.clearAllMocks();
  for (const entry of cases) {
    jest
      .mocked(entry.roles)
      .mockResolvedValue([{ id: 3, name: '担当者', system: false, permission_count: 0 }]);
    jest.mocked(entry.api.create).mockResolvedValue({} as never);
    jest.mocked(entry.api.update).mockResolvedValue({} as never);
  }
});
it.each(cases)('$name は空の個別店舗を入力横で拒否する', async entry => {
  await prepare(entry);
  fireEvent.click(screen.getByRole('radio', { name: '個別店舗' }));
  fireEvent.click(screen.getByRole('button', { name: entry.Create ? '追加する' : '保存する' }));
  const message = await screen.findByText('対象店舗を 1 つ以上選択してください');
  const group = screen.getByRole('group', { name: '対象店舗' });
  expect(group).toHaveAttribute('aria-invalid', 'true');
  expect(group.getAttribute('aria-describedby')).toContain(message.id);
  await waitFor(() => expect(screen.getByRole('radio', { name: '個別店舗' })).toHaveFocus());
  expect(entry.api.create).not.toHaveBeenCalled();
  expect(entry.api.update).not.toHaveBeenCalled();
  expect(notify.error).not.toHaveBeenCalled();
});

it.each(cases)('$name は固有の API と項目で保存する', async entry => {
  await prepare(entry);
  expect(entry.roles).toHaveBeenCalled();
  if (entry.Create) expect(screen.getByRole('radio', { name: '個別店舗' })).toBeChecked();
  expect(screen.queryByLabelText('メールアドレス')).not.toBeInTheDocument();
  expect(screen.queryByLabelText('初期パスワード')).not.toBeInTheDocument();
  if (entry.Edit) {
    expect(screen.queryByRole('textbox')).not.toBeInTheDocument();
    expect(screen.getByText(/対象名は/)).toBeInTheDocument();
  }
  // 停止・再開は一覧の行操作が担い、編集モーダルには状態の入力を置かない
  expect(screen.queryByLabelText('停止')).not.toBeInTheDocument();
  fireEvent.click(screen.getByRole('radio', { name: '個別店舗' }));
  fireEvent.click(screen.getByRole('checkbox', { name: '店舗A' }));
  fireEvent.click(screen.getByRole('button', { name: entry.Create ? '追加する' : '保存する' }));
  const grants = { role_ids: [3], store_scope_type: 'SPECIFIC_STORES', store_ids: [9] };
  if (entry.Create) {
    await waitFor(() =>
      expect(entry.api.create).toHaveBeenCalledWith({
        ...grants,
        display_name: '対象名',
      })
    );
  } else {
    await waitFor(() =>
      expect(entry.api.update).toHaveBeenCalledWith(42, {
        ...grants,
        version: 7,
      })
    );
  }
});
