/**
 * user モジュールのドメイン層。PlatformUser 集約（ロール×店舗集合の授権）・Role 集約・ Permission 目録とそれらのリポジトリ。
 *
 * <p>named interface として公開しているのは過渡措置: auth の認証・昇格処理をはじめ cast/customer/member/order/point/shift
 * がユーザー集約とリポジトリを、各モジュールの授権判定が PermissionCode・StoreScopeType・UserType を直接参照しているため。ID 参照化と読み側
 * projection の整備が進んだ段階で公開面を狭める。
 */
@org.springframework.modulith.NamedInterface("domain")
package com.kizuna.user.domain;
