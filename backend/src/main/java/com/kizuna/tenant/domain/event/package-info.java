/** tenant モジュールが公開するドメインイベント。他モジュールはこの named interface 経由でのみ tenant に依存できる。 */
@org.springframework.modulith.NamedInterface("events")
package com.kizuna.tenant.domain.event;
