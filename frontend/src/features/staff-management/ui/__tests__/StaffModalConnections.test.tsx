import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { platformStaffApi, serviceIdentityApi } from '@/entities/user';
import type { PlatformStaffResponse, ServiceIdentityResponse } from '@/entities/user';
import { useKeyedResource } from '@/shared/lib';
import { notify } from '@/shared/notify';
import { StaffCreateModal } from '../StaffCreateModal';
import { StaffEditModal } from '../StaffEditModal';
import { ServiceIdentityCreateModal } from '../ServiceIdentityCreateModal';
import { ServiceIdentityEditModal } from '../ServiceIdentityEditModal';

jest.mock('@/entities/user', () => ({
  platformStaffApi: { grantableRoles: jest.fn(), create: jest.fn(), update: jest.fn() },
  serviceIdentityApi: { grantableRoles: jest.fn(), create: jest.fn(), update: jest.fn() },
}));
jest.mock('@/shared/notify', () => ({
  notify: { success: jest.fn(), error: jest.fn(), warning: jest.fn() },
}));

const cases = [
  {
    name: '従業員作成',
    Create: StaffCreateModal,
    api: platformStaffApi,
    roles: platformStaffApi.grantableRoles,
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
    name: '従業員編集',
    Edit: StaffEditModal,
    api: platformStaffApi,
    roles: platformStaffApi.grantableRoles,
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
  return entry.Create ? <entry.Create {...props} /> : <EditHarness entry={entry} />;
}

function EditHarness({ entry }: { entry: (typeof cases)[number] }) {
  const subject = {
    id: 42,
    display_name: '対象名',
    enabled: true,
    roles: [{ id: 3, name: '担当者' }],
    store_scope_type: 'SPECIFIC_STORES' as const,
    store_ids: [],
    version: 7,
  };
  const staff = useKeyedResource<PlatformStaffResponse>(['staff', 42], async () => ({
    ...subject,
    email: 'staff@example.com',
    editable: true,
  }));
  const service = useKeyedResource<ServiceIdentityResponse>(['service', 42], async () => subject);
  return entry.Edit === StaffEditModal ? (
    <StaffEditModal {...props} resource={staff} />
  ) : (
    <ServiceIdentityEditModal {...props} resource={service} />
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
  // 停止・再開は一覧の行操作が担い、編集モーダルには状態の入力を置かない
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
      })
    );
  }
});
