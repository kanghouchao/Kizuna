# language: ja
機能: 模版切替
  @template-switch
  シナリオ: template_key を変更すると公開サイトの見た目が切り替わる
    前提 店舗 "store1" の template_key が "default" である
    もし template_key を "classic" に変更する
    ならば 新しいブラウザコンテキストで公開サイトが classic 模版で描画される
    # 事後: after-hook が template_key を "default" へ無条件復元
