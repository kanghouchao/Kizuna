/**
 * shift モジュールのアプリケーション層。
 *
 * <p>パッケージ全体は公開しない。公開するのは {@code ConfirmedShiftLookupService} 型のみで、そこに {@code @NamedInterface}
 * を直接付けている — パッケージに付けると同居する店舗向け・キャスト向けサービスまで公開面に入り、意図しない跨モジュール依存を Modulith の検証が通してしまう。
 */
package com.kizuna.shift.application;
