/**
 * customer モジュールのドメイン層。 過渡的な公開は、order の完了時顧客ロックと完了・プレビューの有効関連読み取りのため。 受注の候補検索と権限付き優先連絡先投影、および表示用の
 * Customer join も維持し、顧客の生成・保存は customer が所有する。
 */
@org.springframework.modulith.NamedInterface("domain")
package com.kizuna.customer.domain;
