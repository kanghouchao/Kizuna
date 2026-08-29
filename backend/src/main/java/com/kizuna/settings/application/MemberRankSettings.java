package com.kizuna.settings.application;

/**
 * 会員ランクの昇格閾値の型付きスナップショット。キー名（member_rank_silver_visit_count 等）の知識は settings モジュールだけが持ち、
 * 消費側（member）はこの型のみに依存する。
 */
public record MemberRankSettings(Threshold silver, Threshold gold) {

  /**
   * 昇格の閾値。二つの条件は OR で、どちらか一方の達成で足りる。
   *
   * <p>0 以下はその条件が成立しえないことを表す。未設定を「0 以上で達成」と読むと、設定の不備で最初の付与から全員が最上位へ上がる。
   *
   * @param visitCount 会員へ帰属した完了受注の回数（跨店舗合計）
   * @param grantedPoints 受注付与の累計純額（取消仕訳の控除後）
   */
  public record Threshold(int visitCount, int grantedPoints) {

    public boolean isReachedBy(long visits, long netGrantedPoints) {
      return (visitCount > 0 && visits >= visitCount)
          || (grantedPoints > 0 && netGrantedPoints >= grantedPoints);
    }
  }
}
