import { castInvitationStatusLabel } from '../model/invitationStatusLabel';

describe('castInvitationStatusLabel', () => {
  it('NOT_INVITED は「未招待」を返すこと', () => {
    expect(castInvitationStatusLabel('NOT_INVITED')).toEqual({
      text: '未招待',
      color: 'bg-muted text-foreground',
    });
  });

  it('INVITED は「招待中」を返すこと', () => {
    expect(castInvitationStatusLabel('INVITED')).toEqual({
      text: '招待中',
      color: 'bg-primary/10 text-primary-strong',
    });
  });

  it('EXPIRED は「期限切れ」を返すこと', () => {
    expect(castInvitationStatusLabel('EXPIRED')).toEqual({
      text: '期限切れ',
      color: 'bg-destructive/10 text-destructive-strong',
    });
  });

  it('LINKED は「連携済み」を返すこと', () => {
    expect(castInvitationStatusLabel('LINKED')).toEqual({
      text: '連携済み',
      color: 'bg-success/10 text-success-strong',
    });
  });
});
