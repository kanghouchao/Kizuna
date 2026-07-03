/**
 * user モジュールのドメイン層。CentralUser / StoreUser の 2 集約とロール・権限、およびそのリポジトリ。
 *
 * <p>named interface として公開しているのは過渡措置: auth モジュールの認証サービスがユーザー集約と リポジトリを直接参照しているため。D3（ID 参照化）と読み側
 * projection の整備が進んだ段階で 公開面を狭める（docs/ddd-fsd-refactor-plan.md）。
 */
@org.springframework.modulith.NamedInterface("domain")
package com.kizuna.user.domain;
