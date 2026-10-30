package com.kizuna.advertising.infrastructure;

import static com.kizuna.shared.export.TabularCells.writeCsv;
import static com.kizuna.shared.export.TabularCells.writeXlsx;

import com.kizuna.advertising.application.AdvertisingMediaService.Snapshot;
import com.kizuna.advertising.domain.AdvertisingMediaReport;
import com.kizuna.shared.export.DocumentBudget;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
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
    var output = budget.output();
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
    if (format.equals("csv")) {
      output.write(new byte[] {(byte) 0xef, (byte) 0xbb, (byte) 0xbf});
      writeCsv(output, HEADER, budget);
      for (var row : metadata) writeCsv(output, row, budget);
      for (var row : source.report().rows()) writeCsv(output, media(source, row), budget);
    } else {
      try (var book = new SXSSFWorkbook(100)) {
        book.setCompressTempFiles(true);
        var totals = book.createSheet(AdvertisingLabels.CRITERIA);
        var media = book.createSheet(MEDIA);
        writeXlsx(totals, HEADER, budget);
        writeXlsx(media, HEADER, budget);
        totals.createFreezePane(0, 1);
        media.createFreezePane(0, 1);
        for (var row : metadata) writeXlsx(totals, row, budget);
        for (var row : source.report().rows()) writeXlsx(media, media(source, row), budget);
        budget.check();
        book.write(output);
      }
    }
    budget.check();
    return output.toByteArray();
  }

  private ArrayList<Object> common(Snapshot source, String type) {
    return new ArrayList<>(
        List.of(
            type,
            source.generatedAt().toString(),
            "媒体別の広告費登録の集計（第1版）",
            AdvertisingLabels.EXACT_NAME,
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
            value.mediaName(),
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
