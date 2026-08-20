// バックエンド API の JSON キーに一致（Jackson グローバル SNAKE_CASE）。
// 応答の任意性は Java 側の可空性が正本。wrapper 型のフィールドは
// default-property-inclusion: non_null によりキーごと応答から消えるため optional にする。

// 受注ステータス。すべての受注は CONFIRMED で出生する（ADR 0017）。未処理の申請は別記録（OrderApplication）。
export type OrderStatus = 'CONFIRMED' | 'COMPLETED' | 'CANCELLED';

// 受付経路。MEMBER_WEB=会員ポータルの申請確定由来/GUEST_WEB=公開店面のゲスト申請確定由来/PHONE=電話受付。
export type ReceptionRoute = 'MEMBER_WEB' | 'GUEST_WEB' | 'PHONE';

/** 予約申請の確定だけが名乗る受付経路。店舗・HQ の作成契約はこの群を拒否する。 */
export type WebApplicationReceptionRoute = 'MEMBER_WEB' | 'GUEST_WEB';

/** 申請由来の受注に出す由来の表示。電話受付は申請ではないので持たない。 */
export const WEB_APPLICATION_ROUTE_LABELS: Record<WebApplicationReceptionRoute, string> = {
  MEMBER_WEB: '会員申請',
  GUEST_WEB: 'ゲスト申請',
};

/** 申請由来の経路か。表示を持つ値を数え上げる形なので、経路が増えても名札の無い値は入り込まない。 */
export function isWebApplicationRoute(
  route: ReceptionRoute
): route is WebApplicationReceptionRoute {
  return route in WEB_APPLICATION_ROUTE_LABELS;
}

/** 受注ステータスの日本語表示。 */
export const ORDER_STATUS_LABELS: Record<OrderStatus, string> = {
  CONFIRMED: '確定',
  COMPLETED: '完了',
  CANCELLED: 'キャンセル',
};

// 予約申請のステータス。PENDING 以外はすべて終端で、申請行は以後動かない（失効は状態ではなく expired の導出）。
export type OrderApplicationStatus = 'PENDING' | 'CONFIRMED' | 'DECLINED' | 'WITHDRAWN';

/** 予約申請ステータスの日本語表示。店舗の受付箱と会員ポータルが同じ呼び名を使う。 */
export const ORDER_APPLICATION_STATUS_LABELS: Record<OrderApplicationStatus, string> = {
  PENDING: '申請中',
  CONFIRMED: '確定',
  DECLINED: '謝絶',
  WITHDRAWN: '取り下げ',
};

export interface Order {
  id?: string;
  receptionist_id?: number;
  receptionist_name?: string;
  business_date?: string;
  arrival_scheduled_start_time?: string;
  arrival_scheduled_end_time?: string;
  customer_id?: string;
  customer_name?: string;
  cast_id?: string;
  cast_name?: string;
  pax?: number;
  course_minutes?: number;
  extension_minutes?: number;
  option_codes?: string[];
  discount_name?: string;
  manual_discount?: number;
  carrier?: string;
  media_name?: string;
  /** 会計金額。完了処理でのみ確定する。 */
  total_fee?: number;
  used_points?: number;
  /** 会計金額から自動計算された付与ポイント。完了処理でのみ確定する。 */
  auto_grant_points?: number;
  remarks?: string;
  cast_driver_message?: string;
  status?: OrderStatus;
  reception_route?: ReceptionRoute;
  /** 申請した会員の会員コード。店舗が起こした受注では応答から消える。 */
  requester_member_code?: string;
  /**
   * 申請時に会員が店舗へ名乗った名前の写し（正本は申請行と、確定時の自動整備が起こした台帳行）。
   * 会員行が消えた申請の確定では顧客が着かず、これが受注の唯一の名乗りになる。
   */
  requester_declared_name?: string;
  location_address?: string;
  location_building?: string;
  /**
   * 受付で録入された連絡先。台帳の顧客に着かなかった受注にだけ残る（顧客が着いた受注では
   * 台帳の行が連絡先を持つため、応答から消える）。
   */
  contact_name?: string;
  contact_phone_number?: string;
  /**
   * 取消の記録（理由・実行者の表示名・時刻）。取消していない受注では応答から消える。
   *
   * 実行者は操作者の削除で欠落しうる（FK が SET NULL）。会員向けの応答は項目の白名単なので、
   * この理由が会員へ渡ることはない。
   */
  cancelled_reason?: string;
  cancelled_by_name?: string;
  cancelled_at?: string;
}

/**
 * 作業キューの 1 行（GET /store/orders/work-queue）。対応の要否を判断し、その場で処理するのに
 * 要る項目だけを持つ。行を書き戻す操作（更新）の応答も同じ形で返るため、受け取ったものを
 * そのまま 1 行の差し替えに使える。
 */
export interface OrderWorkQueueRow {
  id?: string;
  receptionist_id?: number;
  receptionist_name?: string;
  business_date?: string;
  arrival_scheduled_start_time?: string;
  cast_id?: string;
  cast_name?: string;
  pax?: number;
  course_minutes?: number;
  remarks?: string;
  status?: OrderStatus;
  reception_route?: ReceptionRoute;
  /** 申請した会員の会員コード。店舗が起こした受注では応答から消える。 */
  requester_member_code?: string;
  customer_name?: string;
  /** 受付で録入された連絡先。台帳の顧客に着かなかった受注にだけ残る。 */
  contact_name?: string;
  contact_phone_number?: string;
  /** 申請時に会員が店舗へ名乗った名前。会員行が消えた申請由来の受注では唯一の名乗りになる。 */
  requester_declared_name?: string;
}

/**
 * アーカイブの 1 行（GET /store/orders/archive）。終端状態の受注を振り返るための会計・ポイントと
 * 取消の記録を持つ。指名・受付担当・備考は対応中にしか使わないので載らない。
 */
export interface OrderArchiveRow {
  id?: string;
  status?: OrderStatus;
  business_date?: string;
  total_fee?: number;
  auto_grant_points?: number;
  used_points?: number;
  cancelled_at?: string;
  /** 取消の実行者。操作者の削除で欠落しうる（FK が SET NULL）。 */
  cancelled_by_name?: string;
  cancelled_reason?: string;
  customer_name?: string;
  contact_name?: string;
  contact_phone_number?: string;
  requester_declared_name?: string;
}

/**
 * 顧客詳細の注文履歴 1 行（GET /store/orders?customer_id=）。顧客は画面の文脈が持っているので載らない。
 */
export interface OrderSummaryRow {
  id?: string;
  business_date?: string;
  cast_name?: string;
  course_minutes?: number;
  extension_minutes?: number;
  used_points?: number;
  status?: OrderStatus;
}

/**
 * 完了処理の応答（POST /store/orders/{id}/completion）。伝票トークンの生値だけを持つ。
 *
 * 会員へ帰属した完了ではキーごと消える。サーバはダイジェストしか保存しないので、この応答を
 * 逃すと生値は二度と取得できない。
 */
export interface OrderCompletionResult {
  receipt_token?: string;
}

/**
 * 受注一覧の並び替えの鍵（GET /store/orders/work-queue・/archive の sort_key）。
 *
 * 鍵は受注自身の列に限る — join 先の顧客名・キャスト名を鍵にすると、並びが他の集約の書き換えで動く。
 * どの鍵も未設定を最大として並べる（昇順で末尾、降順で先頭）。
 */
export type OrderSortKey = 'BUSINESS_DATE' | 'ARRIVAL_TIME' | 'PAX' | 'COURSE_MINUTES';

export const ORDER_SORT_KEY_LABELS: Record<OrderSortKey, string> = {
  BUSINESS_DATE: '営業日',
  ARRIVAL_TIME: '時刻',
  PAX: '人数',
  COURSE_MINUTES: 'コース',
};

/**
 * 群読み口の抽出条件。作業キュー（カーソル）とアーカイブ（オフセット）が同じ条件を共有する。
 *
 * 状態の軸は検索条件ではなく statuses が担う — 画面でも群そのものが状態の軸なので、検索欄に
 * 状態を置くと群と条件が食い違う。
 */
export interface OrderQueryParams {
  /** 対象の状態。サーバ側が必須（欠けると 400）。 */
  statuses: OrderStatus[];
  /** お客様名の部分一致。空欄は送らない。 */
  customer_name?: string;
  business_date?: string;
  sort_key?: OrderSortKey;
  desc?: boolean;
}

/**
 * 検索と並び替えの条件（状態の軸を除いた部分）。作業キューとアーカイブが同じものを共有する —
 * 群ごとに違う条件だと、同じ画面が 2 つの母集合を主張することになる。
 */
export type OrderListCriteria = Omit<OrderQueryParams, 'statuses'>;

/**
 * 受注の部分更新（PUT /store/orders/{id}）。null ではなく「送らない」が変更しないの意。
 * 文字列の項目は空文字を送れば空にできる（日付・時刻・数値にその形は無い）。
 *
 * 状態は収めない — 完了・取消・確定・謝絶はいずれも専用の操作が独占する（ADR 0013）。終端状態
 * （完了・取消）の受注はサーバが撥ねる。
 *
 * 指名・受付担当は契約上は任意だが、**既に設定済みの受注では省略すると 400 になる**（省略が
 * 「変更しない」なのか「外す」なのかを契約側で区別できないため、外れた結果を黙って作らない）。
 * 店舗が起こした受注は出生時に両方が埋まっているので、編集画面は毎回この 2 つを運ぶ必要がある。
 */
export interface OrderUpdateRequest {
  receptionist_id?: number;
  cast_id?: string;
  business_date?: string;
  arrival_scheduled_start_time?: string;
  arrival_scheduled_end_time?: string;
  pax?: number;
  course_minutes?: number;
  extension_minutes?: number;
  option_codes?: string[];
  discount_name?: string;
  manual_discount?: number;
  location_address?: string;
  location_building?: string;
  carrier?: string;
  media_name?: string;
  remarks?: string;
  cast_driver_message?: string;
  /**
   * 受付で録入された連絡先の訂正。顧客が着いていない受注でだけ送れる（着いた受注へ送ると 400）。
   * 送っても台帳照合は再走しない。
   */
  contact_name?: string;
  contact_phone_number?: string;
}

/** 確定済みの受注の取消（POST /store/orders/{id}/cancellation）。理由は必須（500 文字以内）。 */
export interface OrderCancellationRequest {
  reason: string;
}

export interface OrderReceptionist {
  id?: number;
  display_name?: string;
}

/**
 * 指名候補 1 件（当店に在籍中のキャスト）。
 *
 * キャスト管理の CastResponse とは別物で、ドロップダウンに要る最小限だけを持つ。読み口が受注側にあるのは、
 * 指名が受注の操作で、候補の範囲も要る権限も受注側が決めるため。
 */
export interface OrderCastCandidate {
  id?: string;
  name?: string;
}

export interface OrderCreateRequest {
  /**
   * 受付担当。**省略すると実行者本人が受付担当として記録される**（サーバが確定操作と同じ適格述語で
   * 解決し、適格でなければ 400）。画面の既定が「自分」なのはこのため — JWT にも /platform/me にも
   * 利用者 id が無いので、画面の側で「自分」を選択値として組み立てることはできない。
   */
  receptionist_id?: number;
  business_date: string;
  arrival_scheduled_start_time?: string;
  arrival_scheduled_end_time?: string;
  customer_id?: string;
  customer_name?: string;
  cast_id: string;
  pax?: number;
  course_minutes?: number;
  extension_minutes?: number;
  option_codes?: string[];
  discount_name?: string;
  manual_discount?: number;
  /** 受付経路。Web 申請の群は予約申請の確定だけが名乗るため、この契約では拒否される（400）。 */
  reception_route?: Exclude<ReceptionRoute, WebApplicationReceptionRoute>;
  carrier?: string;
  media_name?: string;
  remarks?: string;
  cast_driver_message?: string;
  phone_number?: string;
  phone_number2?: string;
  address?: string;
  building_name?: string;
  classification?: string;
  landmark?: string;
  has_pet?: boolean;
  ng_type?: string;
  ng_content?: string;
}

/**
 * 店舗の予約受付箱の 1 行（GET /store/order-applications）。申請原文（希望内容）と処理に要る項目だけを持つ。
 *
 * 確定済みの行は order_id で生成された受注と対照できる。
 */
export interface OrderApplicationRow {
  id?: string;
  business_date?: string;
  arrival_scheduled_start_time?: string;
  pax?: number;
  cast_id?: string;
  cast_name?: string;
  remarks?: string;
  status?: OrderApplicationStatus;
  /** 申請した会員の会員コード。会員行が消えた申請でもスナップショットとして残る。 */
  requester_member_code?: string;
  /** 申請時に会員が店舗へ名乗った名前。確定まで台帳行は無いので、これが申請の唯一の名乗りになる。 */
  requester_declared_name?: string;
  /** ゲスト申請の連絡先。折返し先であり、確定時の新規顧客フォームの予填値になる（会員申請では応答から消える）。 */
  contact_name?: string;
  contact_phone_number?: string;
  /** 確定時に生成した受注の id。確定していない申請では応答から消える。 */
  order_id?: string;
  declined_reason?: string;
  /** 希望日を過ぎても処理されていない申請（失効）。確定・謝絶はサーバ側でも拒否される。Java 側が primitive のため必ず現れる。 */
  expired: boolean;
}

/**
 * 予約申請の確定内容（POST /store/order-applications/{id}/confirmation）。確定は申請内容を予填した
 * 受注の作成操作で、送った内容がそのまま受注になる（申請原文は動かない）。
 *
 * ここに無い項目（割引・媒体・派遣先など）は、確定後の受注を汎用更新で整える。
 */
export interface OrderApplicationConfirmationRequest {
  /** 受付担当。省略すると、実行者本人が受付候補の条件を満たす場合にだけ補われる。 */
  receptionist_id?: number;
  business_date: string;
  arrival_scheduled_start_time?: string;
  arrival_scheduled_end_time?: string;
  /** 指名するキャスト。省略は指名なし（会員は指名なしで申請できるため、確定でも強制しない）。 */
  cast_id?: string;
  pax?: number;
  course_minutes?: number;
  remarks?: string;
  /**
   * ゲスト申請の受注を着ける既存の台帳行。new_customer との併用は 400。
   * どちらも省略すると顧客未設定のまま成立し、申請の連絡先が受注側へ写る。
   * 会員申請では受け付けられない（顧客は会員の紐づけが決める）。
   */
  customer_id?: string;
  /** ゲスト申請の受注のために新しく起こす台帳行。 */
  new_customer?: {
    name: string;
    phone_number?: string;
  };
}

/**
 * 公開店面からのゲスト予約申請（POST /store/order-applications/public・匿名）。
 * 店舗は訪問された域名から解決されるため、この本体では名乗らない。
 */
export interface GuestOrderApplicationCreateRequest {
  business_date: string;
  arrival_scheduled_start_time?: string;
  pax?: number;
  remarks?: string;
  /** 折返し先。確定は店舗が折返し連絡で内容を詰める操作なので必須。 */
  contact_name: string;
  contact_phone_number: string;
}

/** ゲスト予約申請の受理応答。匿名の申請者に申請を読む口は無いため、受付番号だけが返る。 */
export interface GuestOrderApplicationResponse {
  id: string;
}

/** 予約申請の謝絶（POST /store/order-applications/{id}/refusal）。理由は必須（500 文字以内）。 */
export interface OrderApplicationDeclineRequest {
  reason: string;
}

/**
 * 受注完了（会計）の内容（POST /store/orders/{id}/completion）。
 *
 * ポイント利用は任意だが、送るなら 1 以上（Java 側が @Min(1)）。利用しない完了では項目ごと省略する — 0 は撥ねられる。
 */
export interface OrderCompletionRequest {
  total_fee: number;
  use_points?: number;
}

/**
 * 完了処理の事前計算（GET /store/orders/{id}/completion-preview）。
 *
 * 残高だけが可空。他の 3 つは Java 側が primitive のため、応答から消えることはない。
 */
export interface OrderCompletionPreview {
  member_linked: boolean;
  /** 未紐づけではキーごと応答から消える。 */
  point_balance?: number;
  usage_unit: number;
  grant_points: number;
}

// 受注の帰属の成立機構。COMPLETION=完了時の関連解決 / RECEIPT_TOKEN=伝票トークンの事後申領。
export type OrderAttributionSource = 'COMPLETION' | 'RECEIPT_TOKEN';

/**
 * 受注 1 件の帰属の現況（GET /store/orders/{id}/attribution）。
 *
 * 返るのは直近の記録 1 件で、会員側の情報は会員コードだけ（表示名・メールは店舗へ渡らない）。
 * attributed 以外は Java 側が可空のため、記録の無い受注ではキーごと消える。
 */
export interface OrderAttribution {
  /** 直近の帰属記録の ID。無効化はこの値で対象を名指す。記録が 1 件も無ければ応答から消える。 */
  id?: number;
  /** Java 側が primitive の boolean のため、キーは必ず応答に含まれる。 */
  attributed: boolean;
  member_code?: string;
  source?: OrderAttributionSource;
  attributed_at?: string;
  /** 直近の記録が無効化済みならその理由。 */
  invalidated_reason?: string;
  invalidated_at?: string;
  /**
   * まだ差し引かれていない誤付与を持つ帰属記録の ID。無ければ応答から消える。
   *
   * 現況（上の id）とは別の記録を指しうる — 訂正を後回しにしたあと正しい本人が申領を済ませると、直近の記録は
   * その人の有効な帰属になり、訂正が要るのは別人の古い記録になる。差し引きの宛先はこちらで名指す。
   */
  pending_correction_attribution_id?: number;
  pending_correction_member_code?: string;
}

// 帰属記録の無効化（POST /store/orders/{id}/attribution/invalidation）。理由は必須（500 文字以内）。
// 対象は受注から導かず、画面が読み口で得た帰属記録の ID を名指す（開いたまま別の操作者が訂正を
// 一巡させると、有効な記録が別人の新しい帰属に入れ替わっているため）。
export interface OrderAttributionInvalidationRequest {
  attribution_id: number;
  reason: string;
}

/**
 * 誤帰属の訂正の進み具合（GET/POST /store/orders/{id}/attribution/correction）。
 *
 * 会員の残高は載らない。この口が届く会員は自店舗の顧客と紐づいているとは限らないため、
 * 店舗へ渡すのは訂正の進み具合だけに閉じてある。
 */
export interface OrderAttributionCorrection {
  /** その受注がその会員へ与えた付与の合計。訂正の累計はこれを超えられない。 */
  granted_points: number;
  /** 同じ帰属記録に対して既に差し引いた合計。 */
  corrected_points: number;
}

// 誤帰属で付いたポイントの差し引き（POST /store/orders/{id}/attribution/correction）。
// 宛先は名指した帰属記録が持つ会員で、顧客に現在紐づく会員ではない。points は引く量を正で渡す
// （この口は与える方向を持たない — 授権が「その付与が誤りだった」ことにだけ由来するため）。
export interface OrderAttributionCorrectionRequest {
  attribution_id: number;
  points: number;
  reason: string;
  idempotency_key: string;
}

// 再発行された伝票トークン（POST /store/orders/{id}/receipt-token）。生値はこの応答にしか現れない。
export interface OrderReceiptTokenIssue {
  receipt_token: string;
}

// 会員本人の予約申請1件（GET /platform/me/order-applications）。店舗の顧客台帳の項目は含まない。
export interface MemberOrderApplication {
  id?: string;
  store_id?: number;
  store_name?: string;
  business_date?: string;
  arrival_scheduled_start_time?: string;
  pax?: number;
  cast_name?: string;
  status?: OrderApplicationStatus;
  /** 希望日を過ぎても店舗が処理していない申請（失効）。Java 側が primitive のため必ず現れる。 */
  expired: boolean;
}

// 会員本人の来店1件（GET /platform/me/visits）。確定した来店の記録で、申請の追跡（MemberOrderApplication）とは別。
// 会計金額・利用ポイント・顧客台帳の項目は含まない。
export interface MemberVisit {
  /** 来店日（受注の業務日）。 */
  visited_on?: string;
  store_name?: string;
  pax?: number;
  /** 担当キャスト名。指名も割り当ても無い来店では応答から消える。 */
  cast_name?: string;
  /** Java 側が primitive の long のため、キーは必ず応答に含まれる。付与の無い来店は 0。 */
  granted_points: number;
}

// 伝票トークンの申領の結果（POST /platform/me/receipts）。
// 来店の内容は来店履歴（MemberVisit）が返すもので、この応答には乗らない。
export interface MemberReceiptClaim {
  /** Java 側が primitive の int のため、キーは必ず応答に含まれる。0 円完了の伝票では 0。 */
  granted_points: number;
}

// 会員本人の予約申請（POST /platform/me/order-applications）。受付担当・顧客はサーバ側（確定時）が決める。
export interface MemberOrderApplicationCreateRequest {
  store_id: number;
  business_date: string;
  pax: number;
  /** 店舗へ名乗る名前。確定時に店舗の顧客台帳行の氏名になるため必須。 */
  declared_name: string;
  arrival_scheduled_start_time?: string;
  cast_id?: string;
  remarks?: string;
}
