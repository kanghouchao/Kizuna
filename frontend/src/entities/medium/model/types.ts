export type MediumStatus = 'ACTIVE' | 'STOPPED';

export interface MediumSummary {
  id: string;
  name: string;
  status: MediumStatus;
  version: number;
}

export interface MediumOption {
  id: string;
  name: string;
}

/** 媒体名の上限。サーバの検証と同じ値で、送信前に入力横へ示すために持つ。 */
export const MEDIUM_NAME_MAX = 100;
