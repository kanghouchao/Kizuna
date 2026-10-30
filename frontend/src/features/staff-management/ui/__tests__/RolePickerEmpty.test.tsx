import { render, screen } from '@testing-library/react';
import { staffApi, serviceIdentityApi } from '@/entities/user';
import type { ServiceIdentityResponse } from '@/entities/user';
import { readTokenClaims, useKeyedResource } from '@/shared/lib';
import { StaffCreateModal } from '../StaffCreateModal';
import { ServiceIdentityCreateModal } from '../ServiceIdentityCreateModal';
import { ServiceIdentityEditModal } from '../ServiceIdentityEditModal';

jest.mock('@/entities/user', () => ({
  staffApi: { grantableRoles: jest.fn(), create: jest.fn(), update: jest.fn() },
  serviceIdentityApi: { grantableRoles: jest.fn(), create: jest.fn(), update: jest.fn() },
}));
jest.mock('@/shared/notify', () => ({
  notify: { success: jest.fn(), error: jest.fn(), warning: jest.fn() },
}));
// hasPermission は実物のまま（PERM_ 接頭辞の対応も検証対象に含める）
jest.mock('@/shared/lib', () => ({
  ...jest.requireActual('@/shared/lib'),
  readTokenClaims: jest.fn(),
}));

const HINT = /先にロール管理で自作ロールを作成してください/;
const props = {
  stores: [{ id: 9, name: '店舗A' }],
  storesLoading: false,
  storesFailed: false,
  onReloadStores: jest.fn(),
  onClose: jest.fn(),
  onCreated: jest.fn(),
  onUpdated: jest.fn(),
};

function grant(...permissions: string[]) {
  jest.mocked(readTokenClaims).mockReturnValue({
    authorities: permissions.map(permission => `PERM_${permission}`),
    userType: 'STAFF',
  });
}

function ServiceEditHarness() {
  const resource = useKeyedResource<ServiceIdentityResponse>(['service', 42], async () => ({
    id: 42,
    display_name: '対象名',
    enabled: true,
    roles: [],
    store_scope_type: 'SPECIFIC_STORES',
    store_ids: [9],
    version: 7,
  }));
  return <ServiceIdentityEditModal {...props} resource={resource} />;
}

beforeEach(() => {
  jest.clearAllMocks();
  jest.mocked(serviceIdentityApi.grantableRoles).mockResolvedValue([]);
  jest.mocked(staffApi.grantableRoles).mockResolvedValue([]);
  grant();
});

it('サービスID追加は候補が空ならロール管理での作成を案内し、権限が無ければリンクを出さない', async () => {
  render(<ServiceIdentityCreateModal {...props} />);
  expect(await screen.findByText(HINT)).toBeInTheDocument();
  expect(screen.queryByRole('link', { name: 'ロール管理を開く' })).not.toBeInTheDocument();
});

it('ROLE_MANAGE を持つ利用者にはロール管理へのリンクを出す', async () => {
  grant('ROLE_MANAGE');
  render(<ServiceIdentityCreateModal {...props} />);
  expect(await screen.findByRole('link', { name: 'ロール管理を開く' })).toHaveAttribute(
    'href',
    '/admin/roles'
  );
});

it('サービスID編集でも候補が空なら案内する', async () => {
  render(<ServiceEditHarness />);
  expect(await screen.findByText(HINT)).toBeInTheDocument();
});

it('候補があれば案内を出さない', async () => {
  jest
    .mocked(serviceIdentityApi.grantableRoles)
    .mockResolvedValue([{ id: 3, name: '担当者', system: false, permission_count: 0 }]);
  render(<ServiceIdentityCreateModal {...props} />);
  await screen.findByLabelText('担当者');
  expect(screen.queryByText(HINT)).not.toBeInTheDocument();
});

it('候補の取得に失敗したら失敗を表示し、案内を出さない', async () => {
  jest.mocked(serviceIdentityApi.grantableRoles).mockRejectedValue(new Error('boom'));
  render(<ServiceIdentityCreateModal {...props} />);
  expect(await screen.findByText('ロール一覧の取得に失敗しました')).toBeInTheDocument();
  expect(screen.queryByText(HINT)).not.toBeInTheDocument();
});

it('従業員追加には自作ロールの案内を出さない', async () => {
  render(<StaffCreateModal {...props} />);
  await screen.findByLabelText('氏名');
  await screen.findByRole('group', { name: 'ロール' });
  expect(screen.queryByText('読み込み中...')).not.toBeInTheDocument();
  expect(screen.queryByText(HINT)).not.toBeInTheDocument();
});
