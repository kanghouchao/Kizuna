package com.kizuna.storage.application;

import com.kizuna.user.application.BusinessAudit;
import java.util.Map;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 公開ファイル登録の監査。外部ストレージへの保存は DB トランザクションに乗らないので、保存の成功後に独立した短いトランザクションで追記する。
 * 保存済みのファイルは消さず、追記に失敗しても例外を返さずログに残す。
 */
@Slf4j
@Service
public class StoredFileAudit {

  private final BusinessAudit businessAudit;
  private final TransactionTemplate independent;

  public StoredFileAudit(BusinessAudit businessAudit, PlatformTransactionManager transactions) {
    this.businessAudit = businessAudit;
    this.independent = new TransactionTemplate(transactions);
    this.independent.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
  }

  public void recorded(
      Long storeId, String relativePath, String bucket, long size, String contentType) {
    try {
      independent.executeWithoutResult(
          status ->
              businessAudit.recordCurrent(
                  storeId,
                  "FILE_UPLOADED",
                  "STORED_FILE",
                  relativePath,
                  Map.of("exists", "false"),
                  Map.of(
                      "bucket", bucket,
                      "size", String.valueOf(size),
                      "content_type", Objects.toString(contentType, ""))));
    } catch (RuntimeException e) {
      log.warn("保存済みファイルの監査を追記できませんでした: path={}", relativePath, e);
    }
  }
}
