# language: ja
機能: 未ログインで開いた画面へ、ログイン後に戻る

  シナリオ: 未ログインで開いた従業員の画面へ、ログイン後に戻る
    もし 未ログインで "/admin/staff" を開く
    ならば ログイン画面へ移り、next に "/admin/staff" が載っている
    もし メール "tanaka.hanako@kizuna.test" とパスワード "pass1234" でログインする
    ならば "/admin/staff" へ戻る

  シナリオ: 未ログインで開いた店舗の画面へ、ログイン後に戻る
    もし 未ログインで店舗1の "/customers" を開く
    ならば ログイン画面へ移り、next に店舗1の "/customers" が載っている
    もし メール "tanaka.hanako@kizuna.test" とパスワード "pass1234" でログインする
    ならば 店舗1の "/customers" へ戻る
    かつ 画面の見出しが "顧客管理" である

  シナリオ: 未ログインで開いた会員の画面へ、断片ごとログイン後に戻り、戻り先の cookie は発行されない
    前提 新しい会員を API で登録する
    もし 未ログインで "/member/points#e2e-fragment" を開く
    ならば ログイン画面へ移り、next に "/member/points" が載っている
    もし その会員でログインする
    ならば "/member/points#e2e-fragment" へ戻る
    かつ ブラウザに会員の戻り先 cookie が無い

  シナリオ: 未ログインで開いたキャストの画面へ、ログイン後に戻る
    前提 店舗管理者としてキャストを作成し招待を受諾してキャスト用アカウントを用意する
    もし 未ログインで "/cast/schedule" を開く
    ならば ログイン画面へ移り、next に "/cast/schedule" が載っている
    もし 作成したキャストのメールとパスワードでログインする
    ならば "/cast/schedule" へ戻る

  シナリオ: next に外部の URL・// 始まり・別の本人種別のパスを渡しても既定の着地へ行く
    もし next に "https://evil.test/admin/staff" を載せたログイン画面を開き、メール "yamada.jiro@kizuna.test" とパスワード "pass1234" でログインする
    ならば 店舗のダッシュボードへ遷移する
    もし ブラウザの cookie をすべて消す
    かつ next に "//evil.test/admin/staff" を載せたログイン画面を開き、メール "yamada.jiro@kizuna.test" とパスワード "pass1234" でログインする
    ならば 店舗のダッシュボードへ遷移する
    もし ブラウザの cookie をすべて消す
    かつ next に "/member/points" を載せたログイン画面を開き、メール "yamada.jiro@kizuna.test" とパスワード "pass1234" でログインする
    ならば 店舗のダッシュボードへ遷移する
