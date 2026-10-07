export const applicantStatusLabels = {
  RECEIVED: '応募受付',
  SCREENING: '選考中',
  INTERVIEWED: '面接済',
  HIRED: '採用',
  REJECTED: '不採用',
  WITHDRAWN: '辞退',
} as const;
export type ApplicantStatus = keyof typeof applicantStatusLabels;
export const receptionChannelLabels = {
  PHONE: '電話',
  WEB: 'Web',
  MEDIA: '媒体',
  OTHER: 'その他',
} as const;
export const sourceTypeLabels = {
  DIRECT: '直接応募',
  MEDIA: '媒体',
  REFERRAL: '紹介',
  SCOUT: 'スカウト',
} as const;
export interface ApplicantIntake {
  name: string;
  channel: keyof typeof receptionChannelLabels;
  source_type: keyof typeof sourceTypeLabels;
  source_media: string | null;
  referrer: string | null;
  assignee: string | null;
  phone: string | null;
  email: string | null;
  address: string | null;
  experience: string | null;
  desired_conditions: string | null;
}
export interface ApplicantSummary {
  id: string;
  name: string;
  channel: ApplicantIntake['channel'];
  source_type: ApplicantIntake['source_type'];
  source_media?: string | null;
  assignee?: string | null;
  status: ApplicantStatus;
  created_at: string;
  version: number;
}
export interface ApplicantInterview {
  interview_at: string;
  interviewer: string;
  notes: string | null;
  checklist: Record<string, boolean>;
}
export interface ApplicantDetail extends ApplicantIntake {
  id: string;
  status: ApplicantStatus;
  created_at: string;
  updated_at: string;
  modified_by: number;
  version: number;
  interview?: ApplicantInterview | null;
}
export interface ApplicantHistory {
  id: string;
  previous_status?: ApplicantStatus;
  new_status: ApplicantStatus;
  actor_id: number;
  created_at: string;
  reason: string;
}
export interface RecruitmentPolicy {
  final_decision_configured: boolean;
  retention_periods: Record<string, number> | null;
}
export function allowedApplicantTransitions(status: ApplicantStatus): ApplicantStatus[] {
  switch (status) {
    case 'RECEIVED':
      return ['SCREENING', 'WITHDRAWN'];
    case 'SCREENING':
      return ['INTERVIEWED', 'WITHDRAWN'];
    case 'INTERVIEWED':
      return ['SCREENING', 'WITHDRAWN'];
    default:
      return [];
  }
}

export const applicantStatusClasses: Record<ApplicantStatus, string> = {
  RECEIVED: 'border-transparent bg-muted text-foreground',
  SCREENING: 'border-transparent bg-primary/10 text-primary-strong',
  INTERVIEWED: 'border-transparent bg-warning/10 text-warning-strong',
  HIRED: 'border-transparent bg-success/10 text-success-strong',
  REJECTED: 'border-transparent bg-destructive/10 text-destructive-strong',
  WITHDRAWN: 'border-transparent bg-muted text-foreground',
};
