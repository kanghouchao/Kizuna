import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import {
  platformRoleApi,
  platformStaffApi,
  storeStaffApi,
  serviceIdentityApi,
} from '@/entities/user';
import type { StoreStaffResponse } from '@/entities/user';
import { useKeyedResource } from '@/shared/lib';
import { notify } from '@/shared/notify';
import { StaffCreateModal } from '../StaffCreateModal';
import { StaffEditModal } from '../StaffEditModal';
import { StoreStaffCreateModal } from '../StoreStaffCreateModal';
import { StoreStaffEditModal } from '../StoreStaffEditModal';
import { ServiceIdentityCreateModal } from '../ServiceIdentityCreateModal';
import { ServiceIdentityEditModal } from '../ServiceIdentityEditModal';

jest.mock('@/entities/user', () => ({
  platformRoleApi: { list: jest.fn() },
  platformStaffApi: { create: jest.fn(), update: jest.fn() },
  storeStaffApi: { grantableRoles: jest.fn(), create: jest.fn(), update: jest.fn() },
  serviceIdentityApi: { grantableRoles: jest.fn(), create: jest.fn(), update: jest.fn() },
}));
jest.mock('@/shared/notify', () => ({
  notify: { success: jest.fn(), error: jest.fn(), warning: jest.fn() },
}));

const cases = [
  {
    name: '管理者作成',
    Create: StaffCreateModal,
    api: platformStaffApi,
    roles: platformRoleApi.list,
    service: false,
    scope: 'ALL_STORES',
  },
  {
    name: '店舗スタッフ作成',
    Create: StoreStaffCreateModal,
    api: storeStaffApi,
    roles: storeStaffApi.grantableRoles,
    service: false,
    scope: 'SPECIFIC_STORES',
  },
  {
    name: 'サービスID作成',
    Create: ServiceIdentityCreateModal,
    api: serviceIdentityApi,
    roles: serviceIdentityApi.grantableRoles,
    service: true,
    scope: 'SPECIFIC_STORES',
  },
  {
    name: '管理者編集',
    Edit: StaffEditModal,
    api: platformStaffApi,
    roles: platformRoleApi.list,
    service: false,
  },
  {
    name: '店舗スタッフ編集',
    Edit: StoreStaffEditModal,
    api: storeStaffApi,
    roles: storeStaffApi.grantableRoles,
    service: false,
  },
  {
    name: 'サービスID編集',
    Edit: ServiceIdentityEditModal,
    api: serviceIdentityApi,
    roles: serviceIdentityApi.grantableRoles,
    service: true,
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
  const resource = useKeyedResource<StoreStaffResponse>(['subject', 42], async () => ({
    id: 42,
    email: 'staff@example.com',
    display_name: '対象名',
    enabled: true,
    editable: true,
    roles: [{ id: 3, name: '担当者' }],
    store_scope_type: 'SPECIFIC_STORES' as const,
    store_ids: [],
    version: 7,
  }));
  return entry.Create ? (
    <entry.Create {...props} />
  ) : entry.Edit ? (
    entry.Edit === StoreStaffEditModal ? (
      <StoreStaffEditModal {...props} resource={resource} />
    ) : (
      <ReadOnlyEdit entry={entry} />
    )
  ) : null;
}

function ReadOnlyEdit({ entry }: { entry: (typeof cases)[number] }) {
  const resource = useKeyedResource<import('@/entities/user').ServiceIdentityResponse>(
    ['subject', 42],
    async () => ({
      id: 42,
      display_name: '対象名',
      enabled: true,
      roles: [{ id: 3, name: '担当者' }],
      store_scope_type: 'SPECIFIC_STORES',
      store_ids: [],
      version: 7,
    })
  );
  return entry.Edit === StaffEditModal ? (
    <StaffEditModal {...props} resource={resource} />
  ) : (
    <ServiceIdentityEditModal {...props} resource={resource} />
  );
}

async function prepare(entry: (typeof cases)[number]) {
  render(<Harness entry={entry} />);
  await screen.findByLabelText('担当者');
  if (entry.Create) {
    fireEvent.change(screen.getByLabelText(entry.service ? '用途名' : '氏名'), {
      target: { value: '対象名' },
    });
    if (!entry.service) {
      fireEvent.change(screen.getByLabelText('メールアドレス'), {
        target: { value: 'new@example.com' },
      });
      fireEvent.change(screen.getByLabelText('初期パスワード'), { target: { value: 'secret' } });
    }
    fireEvent.click(screen.getByLabelText('担当者'));
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
  fireEvent.click(screen.getByLabelText('個別店舗'));
  fireEvent.click(screen.getByRole('button', { name: entry.Create ? '追加する' : '保存する' }));
  const message = await screen.findByText('対象店舗を 1 つ以上選択してください');
  const group = screen.getByRole('group', { name: entry.service ? '対象店舗' : '担当店舗' });
  expect(group).toHaveAttribute('aria-invalid', 'true');
  expect(group.getAttribute('aria-describedby')).toContain(message.id);
  await waitFor(() => expect(screen.getByLabelText('個別店舗')).toHaveFocus());
  expect(entry.api.create).not.toHaveBeenCalled();
  expect(entry.api.update).not.toHaveBeenCalled();
  expect(notify.error).not.toHaveBeenCalled();
});

it.each(cases)('$name は固有の API と項目で保存する', async entry => {
  await prepare(entry);
  expect(entry.roles).toHaveBeenCalled();
  if (entry.Create)
    expect(
      screen.getByLabelText(entry.scope === 'ALL_STORES' ? '全店舗' : '個別店舗')
    ).toBeChecked();
  if (entry.service || entry.Edit) {
    expect(screen.queryByLabelText('メールアドレス')).not.toBeInTheDocument();
    expect(screen.queryByLabelText('初期パスワード')).not.toBeInTheDocument();
  }
  if (entry.Edit) {
    expect(screen.queryByRole('textbox')).not.toBeInTheDocument();
    expect(
      screen.getByText(new RegExp(`対象名${entry.service ? '' : 'さん'}は`))
    ).toBeInTheDocument();
  }
  if (entry.Edit !== StoreStaffEditModal)
    expect(screen.queryByLabelText('停止')).not.toBeInTheDocument();
  fireEvent.click(screen.getByLabelText('個別店舗'));
  fireEvent.click(screen.getByLabelText('店舗A'));
  fireEvent.click(screen.getByRole('button', { name: entry.Create ? '追加する' : '保存する' }));
  const grants = { role_ids: [3], store_scope_type: 'SPECIFIC_STORES', store_ids: [9] };
  if (entry.Create) {
    await waitFor(() =>
      expect(entry.api.create).toHaveBeenCalledWith({
        ...grants,
        display_name: '対象名',
        ...(!entry.service ? { email: 'new@example.com', password: 'secret' } : {}),
      })
    );
  } else {
    await waitFor(() =>
      expect(entry.api.update).toHaveBeenCalledWith(42, {
        ...grants,
        version: 7,
        ...(entry.Edit === StoreStaffEditModal ? { enabled: true } : {}),
      })
    );
  }
});
it('店舗スタッフは停止への変更も同じ更新要求で送る', async () => {
  const entry = cases.find(entry => entry.Edit === StoreStaffEditModal)!;
  await prepare(entry);
  expect(screen.getByLabelText('有効')).toBeChecked();
  fireEvent.click(screen.getByLabelText('停止'));
  fireEvent.click(screen.getByLabelText('全店舗'));
  fireEvent.click(screen.getByRole('button', { name: '保存する' }));
  await waitFor(() =>
    expect(storeStaffApi.update).toHaveBeenCalledWith(42, {
      role_ids: [3],
      store_scope_type: 'ALL_STORES',
      store_ids: [],
      version: 7,
      enabled: false,
    })
  );
});
