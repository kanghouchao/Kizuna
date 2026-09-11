/**
 * customer モジュールのドメイン層。 過渡的な公開は、order の完了時顧客ロックと完了・プレビューの有効関連読み取りのため。 表示用の Customer join
 * も維持し、顧客の生成・保存は customer が所有する。
 */
@org.springframework.modulith.NamedInterface("domain")
package com.kizuna.customer.domain;
