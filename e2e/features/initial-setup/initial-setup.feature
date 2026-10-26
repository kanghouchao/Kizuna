# language: ja
# スイート全体が使う 2 店舗と 3 アカウントを製品の導線で作る。後のシナリオは前のシナリオの登録結果に
# 依存するため直列で流し、途中で落ちたら残りは実行しない。
@mode:serial
機能: 初期設定

  シナリオ: 従業員が居ない状態で最初の管理者を登録すると、自動でログインして各管理画面へ到達できる
    前提 初期設定の要否 API が required: true を返す
    かつ 初期設定画面を開く
    もし メールアドレス "admin@kizuna.test"・表示名 "HQ管理者"・パスワード "pass1234" で最初の管理者を登録する
    ならば 自動でログインしてダッシュボードへ遷移する
    もし サイドバーの "ロール管理" から "/admin/roles" を開く
    ならば 一覧の "統括" の行に "既定" が表示される
    もし サイドバーの "従業員管理" から "/admin/staff" を開く
    ならば 一覧の "admin@kizuna.test" の行に "統括" が表示される
    もし サイドバーの "店舗一覧" から "/admin/stores" を開く
    ならば 店舗一覧に "店舗がありません" と表示される

  シナリオ: 最初の管理者が店舗を登録し、従業員管理 API で店長と店舗スタッフのアカウントを作成する
    前提 "admin@kizuna.test" とパスワード "pass1234" で API にログインする
    もし 次の店舗を登録する
      | 店舗名   | ドメイン           | メールアドレス          |
      | Sample   | store1.kizuna.test | admin@store1.kizuna.com |
      | Sample 2 | store2.kizuna.test | admin@store2.kizuna.com |
    ならば 店舗 "store1.kizuna.test" の id が店舗文脈の固定 id と一致する
    もし 次の従業員を作成する
      | 氏名     | メールアドレス            | パスワード | ロール       | 店舗                                   |
      | 田中花子 | tanaka.hanako@kizuna.test | pass1234   | 店長         | store1.kizuna.test, store2.kizuna.test |
      | 山田次郎 | yamada.jiro@kizuna.test   | pass1234   | スタッフ     | store1.kizuna.test                     |
    ならば 作成した各従業員がそのパスワードでログインでき、授権店舗が指定どおりになる

  シナリオ: 従業員が居る状態では、初期設定の要否・画面・登録のいずれも閉じている
    前提 初期設定の要否 API が required: false を返す
    もし 初期設定画面の URL へアクセスする
    ならば ログイン画面へリダイレクトされる
    かつ 初期設定の登録 API にメールアドレス "late-admin@kizuna.test"・パスワード "pass1234" を送ると 404 が返る
