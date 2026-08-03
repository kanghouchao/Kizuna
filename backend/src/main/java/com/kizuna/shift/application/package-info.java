/**
 * shift モジュールのアプリケーション層。
 *
 * <p>公開の意図は {@code ConfirmedShiftLookupService} に限る。予約申請の指名が「その日その店舗の確定シフト」に基づくことを order
 * モジュールが検証するための恒久的な読み口で、確定シフトの有無と指名候補以外を返さないことで公開面そのものを狭く保つ。
 */
@org.springframework.modulith.NamedInterface("application")
package com.kizuna.shift.application;
