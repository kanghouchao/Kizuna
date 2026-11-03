package com.kizuna.advertising.application;

import com.kizuna.advertising.application.AdvertisingOrderCostService.Snapshot;
import com.kizuna.advertising.domain.AdvertisingOrderCostReport;
import com.kizuna.shared.export.DocumentBudget;
import com.kizuna.shared.export.TabularDocument;
import com.kizuna.shared.export.TabularDocument.Sheet;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.springframework.stereotype.Component;

@Component
public class AdvertisingOrderCostRenderer {
  private static final List<Object> HEADER =
      List.of(
          "行種別",
          "生成日時",
          "計算根拠",
          "媒体の照合",
          "受注の根拠",
          "端数処理",
          "店舗ID",
          "対象月",
          "対象月版",
          "媒体",
          "費用行数",
          "登録営業広告費（円）",
          "有効完了受注件数",
          "うち0円の受注件数",
          "媒体未入力の有効完了受注件数",
          "一件あたり記録費用（円）",
          "算出状況",
          "解釈");
  private static final String MEDIA = "受注あたり記録広告費";
  // 列数を変えずに経路別の件数を残すため、媒体以外の経路は集計条件と同じシートの別種別の行に置く。
  private static final String OTHER_ROUTES = "媒体以外の経路";

  public byte[] render(Snapshot source, String format, DocumentBudget budget) throws IOException {
    var criteria = common(source, AdvertisingLabels.CRITERIA);
    var report = source.report();
    criteria.addAll(
        List.of(
            "",
            report.costEntryCount(),
            value(report.recordedSalesAmount()),
            report.validCompletedOrderCount(),
            report.zeroAmountOrderCount(),
            report.unnamedMediaOrderCount(),
            "",
            "",
            "同月同媒体の記録比較であり、実際の獲得費用・広告による因果・利益・ROIを示しません。0円の有効完了受注を含み、小数第3位を四捨五入しています。"));
    var otherRoutes = common(source, OTHER_ROUTES);
    otherRoutes.addAll(
        List.of(
            "",
            "",
            "",
            report.otherRouteOrderCount(),
            "",
            "",
            "",
            "",
            "リピート・紹介・その他の経路で知った有効完了受注の件数で、有効完了受注件数の内数です。媒体への割当てと単価算出から除外しています。"));
    return TabularDocument.write(
        format,
        HEADER,
        List.of(
            new Sheet(AdvertisingLabels.CRITERIA, Stream.<List<Object>>of(criteria, otherRoutes)),
            new Sheet(MEDIA, report.rows().stream().map(row -> media(source, row)))),
        budget);
  }

  private ArrayList<Object> common(Snapshot source, String type) {
    return new ArrayList<>(
        List.of(
            type,
            source.generatedAt().toString(),
            "登録営業広告費 ÷ 有効完了受注件数（第1版）",
            AdvertisingLabels.MEDIUM_ID_MATCHING,
            "原営業日が対象月の有効完了受注（0円を含む）",
            "小数第3位を四捨五入（円）",
            source.storeId(),
            source.month(),
            source.monthVersion()));
  }

  private List<Object> media(Snapshot source, AdvertisingOrderCostReport.Row value) {
    var row = common(source, MEDIA);
    row.addAll(
        List.of(
            value.medium().name(),
            value.costEntryCount(),
            value(value.recordedSalesAmount()),
            value.validCompletedOrderCount(),
            value.zeroAmountOrderCount(),
            "",
            value(value.costPerOrder()),
            status(value.calculationStatus()),
            ""));
    return row;
  }

  private Object value(Object nullable) {
    return nullable == null ? "" : nullable;
  }

  private String status(AdvertisingOrderCostReport.Status status) {
    return switch (status) {
      case CALCULATED -> "記録値から算出";
      case NO_COST_RECORDS -> "営業広告費が未登録";
      case NO_VALID_ORDERS -> "有効完了受注が0件";
    };
  }
}
