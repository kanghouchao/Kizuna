/** 共有カーネル: 店舗コンテキスト、共通エンティティ基盤、横断的設定・例外。 全モジュールから参照されるため OPEN モジュールとして公開する。 */
@org.springframework.modulith.ApplicationModule(
    type = org.springframework.modulith.ApplicationModule.Type.OPEN)
package com.kizuna.shared;
