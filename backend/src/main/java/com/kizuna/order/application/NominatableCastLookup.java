package com.kizuna.order.application;

import com.kizuna.cast.domain.Cast;
import com.kizuna.cast.domain.CastRepository;
import com.kizuna.shared.storescope.StoreScopeExempt;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Component;

/**
 * 指名先として成立するキャスト（対象店舗に在籍中のキャスト）を引く述語。
 *
 * <p>会員の申請・店舗の受注作成・汎用更新・申請編集・確定と、指名候補の一覧が同じ条件を共有する。候補に出さないだけでは キャスト ID
 * を直接送る要求を防げないため、読み口と書き込み側の判定はひとつでなければならない。
 *
 * <p>共有するのは引き当てだけで、見つからないときに何を投げるかは呼び出し側が決める — 会員向けは列挙を防ぐ {@code
 * NotFoundException}、店舗スタッフ向けは理由と対処の分かる {@code ServiceException} であり、ここで投げ分けまで畳むと 会員側の防列挙が黙って壊れる。
 *
 * <p>店舗の一致を述語に置くのは、キャストの読み取りに掛かる絞り込みへ暗黙に頼らないため。当日の確定シフトの有無は確定時だけが見る —
 * 先の日付の申請は編集時点でシフトが確定していないのが通常で、編集で要求すると指名を差し替える手段が事実上無くなる。
 *
 * <p>同じ層の他の bean と違い {@code @Service} ではなく {@code @Component} なのは、これがユースケースでも取引境界でもなく、
 * 呼び出し側の取引の中で回る引き当てだけを持つため。
 */
@Component
@RequiredArgsConstructor
class NominatableCastLookup {

  private static final String EXPLICIT_STORE_PREDICATE =
      "キャスト読み取りの暗黙の絞り込みに頼らず、店舗の一致を引き当ての述語に明示することが境界";

  /** 指名を成立させる在籍状態。 */
  private static final String ACTIVE_STATUS = "ACTIVE";

  /** 1 回に返す候補の件数。絞り込みは名前で行うため、続きを辿る手段は持たせず上限で切る。 */
  private static final Limit CANDIDATE_LIMIT = Limit.of(10);

  private final CastRepository castRepository;

  /** 指名先として成立するキャストを引く。成立しないときは空を返し、何を投げるかは呼び出し側が決める。 */
  @StoreScopeExempt(reason = EXPLICIT_STORE_PREDICATE)
  Optional<Cast> find(Long storeId, String castId) {
    return castRepository
        .findById(castId)
        .filter(cast -> storeId.equals(cast.getStoreId()))
        .filter(cast -> ACTIVE_STATUS.equals(cast.getStatus()));
  }

  /**
   * 指名候補を名前で絞り込んで返す。
   *
   * <p>検索語なし（null・空白のみ）は絞り込みなしと同じに扱う。コンボボックスは開いた時点で語なしに一度取りに行くため、 ここで空を返すと候補が何も出ないまま打ち始めることになる。
   */
  @StoreScopeExempt(reason = EXPLICIT_STORE_PREDICATE)
  List<Cast> searchCandidates(Long storeId, String keyword) {
    String name = keyword == null ? "" : keyword.trim();
    return castRepository
        .findByStoreIdAndStatusAndNameContainingIgnoreCaseOrderByDisplayOrderAscIdAsc(
            storeId, ACTIVE_STATUS, name, CANDIDATE_LIMIT);
  }
}
