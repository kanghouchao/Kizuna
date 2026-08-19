import { apiClient } from '@/shared/api';
import {
  AbsenceResponse,
  AttendanceCorrectionRequest,
  AttendanceCreateRequest,
  AttendanceResponse,
} from '../model/types';

/**
 * 当日実績の読み書き。シフトと同じ後端モジュールに属するが集約が別（ADR 0014）なので、
 * 口も shiftApi とは分けて持つ。
 *
 * 物理削除の口は無い（法定保存）。誤建行は cancel で導出・照会から外す。
 */
export const attendanceApi = {
  /** 指定営業日の実績を取得する（取消済みは常に除外される）。 */
  list: async (params: {
    business_date: string;
    cast_id?: string;
  }): Promise<AttendanceResponse[]> => {
    const response = await apiClient.get('/store/attendances', { params });
    return response.data;
  },
  /** 実績を記録する。shift_id を省くと飛び込み出勤になる。 */
  record: async (data: AttendanceCreateRequest): Promise<AttendanceResponse> => {
    const response = await apiClient.post('/store/attendances', data);
    return response.data;
  },
  /** 実績を訂正する。訂正できる項目の全量を送る。 */
  correct: async (id: string, data: AttendanceCorrectionRequest): Promise<AttendanceResponse> => {
    const response = await apiClient.put(`/store/attendances/${id}`, data);
    return response.data;
  },
  /** 実績に取消標記を付ける。理由は必須で、経緯を辿れる根拠はここにしか残らない。 */
  cancel: async (id: string, reason: string): Promise<void> => {
    await apiClient.post(`/store/attendances/${id}/cancellation`, { reason });
  },
  /**
   * 指定営業日の欠勤を取得する。行ではなく導出なので、営業日の終了と当該キャストの最遅予定終了の
   * 経過を待たない間は空で返る（ADR 0014）— 空を「誰も欠勤していない」と読んではならない。
   */
  listAbsences: async (params: { business_date: string }): Promise<AbsenceResponse[]> => {
    const response = await apiClient.get('/store/absences', { params });
    return response.data;
  },
};
