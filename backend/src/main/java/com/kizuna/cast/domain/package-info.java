/**
 * cast モジュールのドメイン層。
 *
 * <p>named interface として公開する: order モジュールは指名の引き当てと指名候補の一覧のため {@code CastRepository} を、受注一覧・詳細の
 * projection のため {@code Cast} を直接参照する。shift モジュールも公開出勤表のキャスト表示情報取得のため {@code CastRepository} を参照する。
 *
 * <p>受注側に指名候補の読み口を設けても公開面は狭まらない — 一覧・詳細の projection が JPQL で {@code Cast} を join し続けるため、公開を落とすと
 * projection が組めなくなる。
 */
@org.springframework.modulith.NamedInterface("domain")
package com.kizuna.cast.domain;
