package com.kizuna.advertising.application;

import com.kizuna.advertising.api.dto.AdvertisingResponses.CostResponse;
import com.kizuna.advertising.application.AdvertisingService.ExportSnapshot;
import com.kizuna.shared.export.DocumentBudget;
import com.kizuna.shared.export.TabularDocument;
import com.kizuna.shared.export.TabularDocument.Sheet;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.springframework.stereotype.Component;

@Component
public class AdvertisingRenderer {
  private static final List<Object> HEADER =
      List.of(
          "行種別",
          "生成日時",
          "計算根拠",
          "店舗ID",
          "対象月",
          "広告費登録件数",
          "営業広告登録額（円）",
          "採用広告登録額（円）",
          "広告登録額合計（円）",
          "広告費ID",
          "広告費版",
          "区分",
          "媒体",
          "広告会社",
          "プラン",
          "問い合わせ人数",
          "金額（円）");
  private static final String COSTS = "広告費明細";

  public byte[] render(ExportSnapshot report, String format, DocumentBudget budget)
      throws IOException {
    return TabularDocument.write(
        format,
        HEADER,
        List.of(
            new Sheet(AdvertisingLabels.CRITERIA, Stream.of(row(report, null))),
            new Sheet(COSTS, report.costs().stream().map(cost -> row(report, cost)))),
        budget);
  }

  private List<Object> row(ExportSnapshot report, CostResponse cost) {
    var s = report.summary();
    var row =
        new ArrayList<Object>(
            List.of(
                cost == null ? AdvertisingLabels.CRITERIA : COSTS,
                s.generatedAt().toString(),
                "出力時点の有効な広告費登録（第1版）",
                s.storeId().toString(),
                s.month()));
    row.addAll(
        cost == null
            ? List.of(
                s.entryCount(), s.salesAmount(), s.recruitmentAmount(), s.recordedTotalAmount())
            : List.of("", "", "", ""));
    if (cost != null)
      row.addAll(
          List.of(
              cost.id(),
              Long.toString(cost.version()),
              AdvertisingLabels.category(cost.category()),
              cost.medium().name(),
              blank(cost.agencyName()),
              blank(cost.planName()),
              blank(cost.inquiryCount()),
              cost.amount()));
    while (row.size() < HEADER.size()) row.add("");
    return row;
  }

  private Object blank(Object value) {
    return value == null ? "" : value;
  }
}
