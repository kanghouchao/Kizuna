/** 入力中の小計だけに使う選択価格。送信時は除去し、確定額はサーバの試算を使う。 */
export interface FeePreviewValues {
  fee_preview?: {
    course?: number;
    special_services?: number;
  };
}
