// BASE_URL の既定値の単一ソース（compose の環境変数 BASE_URL で上書き可能）。
// playwright.config の use.baseURL と、模版切替シナリオが作る新規 context の baseURL が
// 同じ値を共有し、既定値のドリフトを防ぐ。gateway の network alias 経由で tenant ドメインへ到達する。
export const BASE_URL = process.env.BASE_URL ?? 'http://store1.kizuna.test';
