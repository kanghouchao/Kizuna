# 認証は Spring Security 標準スタックを採用する

Status: Accepted

## Context

旧実装は手書き JWT フィルタ + jjwt でトークンを発行・検証しており、標準スタックに寄せられる保守負担が独自コードに残っていた。

## Decision

認証判定は `AuthenticationManager`（`DaoAuthenticationProvider` + 自作 `UserDetailsService`）、Bearer 検証は
oauth2-resource-server（`NimbusJwtDecoder`）、発行は Spring の `JwtEncoder`（`NimbusJwtEncoder`）に委ね、手書きフィルタと
jjwt を撤去する。対称 HMAC 鍵（HS256）を resource-server で扱う — 外部 IdP・非対称鍵は単一グループの単体運用では不要なため導入しない。

## Consequences

フレームワーク保守の恩恵を得る一方、標準スタックの失敗経路は 403 でなく 401 を返すため、切替直後は旧署名の現存トークンが失効し
再ログインが必要になる（未上線のため許容）。
