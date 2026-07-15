import { castInvitationAcceptanceApi } from '@/entities/cast';

jest.mock('@/shared/api/client', () => ({
  __esModule: true,
  default: {
    get: jest.fn(async (url: string) => ({ data: { ok: true, url } })),
    post: jest.fn(async (url: string) => ({ data: { ok: true, url } })),
  },
}));

describe('castInvitationAcceptanceApi', () => {
  it('view は /platform/cast-invitations/:token を GET する', async () => {
    expect(await castInvitationAcceptanceApi.view('tok1')).toEqual({
      ok: true,
      url: '/platform/cast-invitations/tok1',
    });
  });

  it('acceptAsNewUser は /platform/cast-invitations/:token/acceptance を POST する', async () => {
    const payload = { email: 'a@example.com', password: 'pass1234', display_name: '花子' };
    expect(await castInvitationAcceptanceApi.acceptAsNewUser('tok1', payload)).toEqual({
      ok: true,
      url: '/platform/cast-invitations/tok1/acceptance',
    });
  });

  it('acceptAsExistingUser は /platform/cast-invitations/:token/acceptance/existing を POST する', async () => {
    expect(await castInvitationAcceptanceApi.acceptAsExistingUser('tok1')).toEqual({
      ok: true,
      url: '/platform/cast-invitations/tok1/acceptance/existing',
    });
  });
});
