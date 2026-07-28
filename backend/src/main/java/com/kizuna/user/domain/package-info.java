/**
 * user モジュールのドメイン層。PlatformUser 集約（ロール×店舗集合の授権）・Role 集約・ Permission 目録とそれらのリポジトリ。
 *
 * <p>named interface として公開しているのは過渡措置: auth モジュールの認証サービスがユーザー集約と リポジトリを直接参照しているため。ID 参照化と読み側
 * projection の整備が進んだ段階で 公開面を狭める（docs/ddd-fsd-refactor-plan.md）。
 */
@org.springframework.modulith.NamedInterface("domain")
package com.kizuna.user.domain;
