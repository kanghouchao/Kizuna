import { castInvitationStatusLabel } from '../model/invitationStatusLabel';

describe('castInvitationStatusLabel', () => {
  it('NOT_INVITED は「未招待」を返すこと', () => {
    expect(castInvitationStatusLabel('NOT_INVITED')).toEqual({
      text: '未招待',
      color: 'bg-gray-100 text-gray-800',
    });
  });

  it('INVITED は「招待中」を返すこと', () => {
    expect(castInvitationStatusLabel('INVITED')).toEqual({
      text: '招待中',
      color: 'bg-blue-100 text-blue-800',
    });
  });

  it('EXPIRED は「期限切れ」を返すこと', () => {
    expect(castInvitationStatusLabel('EXPIRED')).toEqual({
      text: '期限切れ',
      color: 'bg-red-100 text-red-800',
    });
  });

  it('LINKED は「連携済み」を返すこと', () => {
    expect(castInvitationStatusLabel('LINKED')).toEqual({
      text: '連携済み',
      color: 'bg-green-100 text-green-800',
    });
  });
});
