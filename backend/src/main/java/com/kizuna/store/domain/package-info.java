/**
 * store モジュールのドメイン層。
 *
 * <p>named interface として公開しているのは過渡措置: cast/customer/order/shift モジュールが Store 集約と
 * リポジトリを直接参照しているため。D3（ID 参照化）が進んだ段階で公開面を events のみに狭める （docs/ddd-fsd-refactor-plan.md）。
 */
@org.springframework.modulith.NamedInterface("domain")
package com.kizuna.store.domain;
