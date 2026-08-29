package com.kizuna.member.application;

/**
 * 昇格判定の材料を、会員行を押さえた<b>後に</b>読むための口。
 *
 * <p>材料そのものは order と point が持ち member からは引けない（依存が環になる）ため、実装は帰属を記録する側に置く。値ではなくこの型で受けるのは、
 * <b>読む時点</b>を member 側が決めなければならないため — ロックの前に読んだ値で判じると、並行する 2 つの来店が同じ古い指標を観測し、
 * どちらも昇格させないまま閾値を跨いだ来店が取り残される。
 */
public interface MemberRankMetrics {

  /** 会員へ有効に帰属している完了受注の回数（跨店舗合計）。 */
  long completedVisitCount(long memberId);

  /** 受注付与の累計純額（取消仕訳の控除後）。 */
  long netGrantedPoints(long memberId);
}
