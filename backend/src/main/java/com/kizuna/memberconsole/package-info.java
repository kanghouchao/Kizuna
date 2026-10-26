/**
 * 職員の会員コンソール。会員の検索・照会と代行退会を、member / point / customer / memberwithdrawal の公開面を束ねて提供する。 会員は point /
 * customer に依存できず、三者は互いを知らないため、照会の合成も独立のモジュールに置く（memberwithdrawal と同型）。
 */
package com.kizuna.memberconsole;
