/** 会員登録リクエスト（POST /platform/members）。member/api/dto/MemberRegistrationRequest.java に対応。 */
export interface MemberRegisterRequest {
  email: string;
  password: string;
  display_name: string;
}

/** 会員登録応答。member/api/dto/MemberRegistrationResponse.java に対応。 */
export interface MemberRegisterResponse {
  member_code?: string;
}

/** 会員ポータルホーム応答。member/api/dto/MemberHomeResponse.java に対応。 */
export interface MemberHomeResponse {
  member_code?: string;
  display_name?: string;
}
