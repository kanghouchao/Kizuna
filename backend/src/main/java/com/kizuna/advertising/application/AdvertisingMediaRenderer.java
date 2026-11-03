package com.kizuna.advertising.application;

import com.kizuna.advertising.application.AdvertisingMediaService.Snapshot;
import com.kizuna.advertising.domain.AdvertisingMediaReport;
import com.kizuna.shared.export.DocumentBudget;
import com.kizuna.shared.export.TabularDocument;
import com.kizuna.shared.export.TabularDocument.Sheet;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class AdvertisingMediaRenderer {
  private static final List<Object> HEADER =
      List.of(
          "行種別",
          "生成日時",
          "計算根拠",
          "媒体の照合",
          "問い合わせ人数の根拠",
          "店舗ID",
          "対象月",
          "対象月版",
          "区分",
          "媒体",
          "費用行数",
          "登録額（円）",
          "問い合わせ人数の入力済み行数",
          "問い合わせ人数の未入力行数",
          "入力済み人数の合計（重複未除外）",
          "入力状況");
  private static final String MEDIA = "媒体別集計";

  public byte[] render(Snapshot source, String format, DocumentBudget budget) throws IOException {
    var metadata = new ArrayList<List<Object>>();
    var criteria = common(source, AdvertisingLabels.CRITERIA);
    criteria.addAll(
        List.of(
            "",
            "",
            source.report().entryCount(),
            source.report().recordedTotalAmount(),
            "",
            "",
            "",
            ""));
    metadata.add(criteria);
    for (var category : source.report().categoryTotals()) {
      var row = common(source, "区分別総計");
      row.addAll(
          List.of(
              AdvertisingLabels.category(category.category()),
              "",
              category.entryCount(),
              category.recordedAmount(),
              "",
              "",
              "",
              ""));
      metadata.add(row);
    }
    return TabularDocument.write(
        format,
        HEADER,
        List.of(
            new Sheet(AdvertisingLabels.CRITERIA, metadata.stream()),
            new Sheet(MEDIA, source.report().rows().stream().map(row -> media(source, row)))),
        budget);
  }

  private ArrayList<Object> common(Snapshot source, String type) {
    return new ArrayList<>(
        List.of(
            type,
            source.generatedAt().toString(),
            "媒体別の広告費登録の集計（第1版）",
            AdvertisingLabels.MEDIUM_ID_MATCHING,
            "費用行に手入力された人数の合計（重複未除外）",
            source.storeId(),
            source.month(),
            source.monthVersion()));
  }

  private List<Object> media(Snapshot source, AdvertisingMediaReport.Row value) {
    var row = common(source, MEDIA);
    row.addAll(
        List.of(
            AdvertisingLabels.category(value.category()),
            value.medium().name(),
            value.entryCount(),
            value.recordedAmount(),
            value.recordedInquiryEntryCount(),
            value.unrecordedInquiryEntryCount(),
            value.recordedInquiryCountSum() == null ? "" : value.recordedInquiryCountSum(),
            inquiryStatus(value.inquiryStatus())));
    return row;
  }

  private String inquiryStatus(AdvertisingMediaReport.InquiryStatus status) {
    return switch (status) {
      case UNRECORDED -> "全行未入力";
      case PARTIAL -> "一部未入力";
      case RECORDED -> "全行入力済み";
    };
  }
}
