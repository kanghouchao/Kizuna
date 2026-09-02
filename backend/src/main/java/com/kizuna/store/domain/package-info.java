/**
 * store モジュールのドメイン層。
 *
 * <p>named interface 公開のうち Store 集約とリポジトリは過渡措置: cast モジュールの招待処理が直接参照しているためで、ID
 * 参照化が進んだ段階でポートのみに狭める。跨モジュールポート（{@code CompletedOrderCheck} / {@code AttendanceRecordCheck}）は store
 * 側が定義し order / shift 側が実装するため恒久的に公開する。
 */
@org.springframework.modulith.NamedInterface("domain")
package com.kizuna.store.domain;
