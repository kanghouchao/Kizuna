package com.kizuna.review.domain;

import com.kizuna.receipt.once.Fingerprint;
import com.kizuna.review.domain.ReviewValues.Basis;
import com.kizuna.review.domain.ReviewValues.Operation;
import com.kizuna.shared.exception.ServiceException;
import com.kizuna.shared.publication.Publication;
import com.kizuna.user.domain.PermissionCode;
import java.time.OffsetDateTime;
import java.util.List;

public sealed interface ReviewCommand {
  String key();

  String fingerprint();

  List<PermissionCode> permissions();

  record Receive(ReviewInput input, String key) implements ReviewCommand {
    public Receive {
      if (input == null) throw ReviewInput.invalid();
      key = ReviewInput.key(key);
    }

    public String fingerprint() {
      return received(input);
    }

    public List<PermissionCode> permissions() {
      return intake(input);
    }
  }

  record Decide(String reviewId, Long version, Operation type, String reason, String key)
      implements ReviewCommand {
    public Decide {
      reviewId = ReviewInput.id(reviewId);
      version = ReviewInput.version(version);
      if (type != Operation.APPROVED && type != Operation.REJECTED)
        throw new ServiceException("審査結果を指定してください");
      reason = ReviewInput.clean(reason, 500);
      key = ReviewInput.key(key);
    }

    public String fingerprint() {
      return Fingerprint.of(type, reviewId, version, reason);
    }

    public List<PermissionCode> permissions() {
      return List.of(PermissionCode.REVIEW_MODERATE);
    }
  }

  record Withdraw(String reviewId, Long version, String reason, String key)
      implements ReviewCommand {
    public Withdraw {
      reviewId = ReviewInput.id(reviewId);
      version = ReviewInput.version(version);
      reason = ReviewInput.clean(reason, 500);
      key = ReviewInput.key(key);
    }

    public String fingerprint() {
      return Fingerprint.of(Operation.WITHDRAWN, reviewId, version, reason);
    }

    public List<PermissionCode> permissions() {
      return List.of(PermissionCode.REVIEW_MANAGE);
    }
  }

  record Grant(
      String reviewId,
      Long version,
      Basis basis,
      OffsetDateTime grantedAt,
      String evidence,
      String key)
      implements ReviewCommand {
    public Grant {
      reviewId = ReviewInput.id(reviewId);
      version = ReviewInput.version(version);
      if (basis == null) throw new ServiceException("許可の取得根拠を指定してください");
      grantedAt = ReviewInput.time(grantedAt);
      evidence = ReviewInput.clean(evidence, 500);
      key = ReviewInput.key(key);
    }

    public String fingerprint() {
      return Fingerprint.of(
          Operation.PERMISSION_GRANTED, reviewId, version, basis, grantedAt, evidence);
    }

    public List<PermissionCode> permissions() {
      return List.of(PermissionCode.REVIEW_MANAGE);
    }
  }

  record Revoke(String reviewId, Long version, OffsetDateTime receivedAt, String reason, String key)
      implements ReviewCommand {
    public Revoke {
      reviewId = ReviewInput.id(reviewId);
      version = ReviewInput.version(version);
      receivedAt = ReviewInput.time(receivedAt);
      reason = ReviewInput.clean(reason, 500);
      key = ReviewInput.key(key);
    }

    public String fingerprint() {
      return Fingerprint.of(Operation.PERMISSION_REVOKED, reviewId, version, receivedAt, reason);
    }

    public List<PermissionCode> permissions() {
      return List.of(PermissionCode.REVIEW_MANAGE);
    }
  }

  record Publish(String reviewId, Long version, Publication publication, String key)
      implements ReviewCommand {
    public Publish {
      reviewId = ReviewInput.id(reviewId);
      version = ReviewInput.version(version);
      if (publication == null) throw new ServiceException("公開設定を指定してください");
      key = ReviewInput.key(key);
    }

    public String fingerprint() {
      return Fingerprint.of(
          Operation.PUBLICATION_CHANGED,
          reviewId,
          version,
          publication.published(),
          publication.publishFrom(),
          publication.publishUntil(),
          publication.displayOrder());
    }

    public List<PermissionCode> permissions() {
      return List.of(PermissionCode.REVIEW_MODERATE);
    }
  }

  record Correct(String reviewId, Long version, String reason, ReviewInput input, String key)
      implements ReviewCommand {
    public Correct {
      reviewId = ReviewInput.id(reviewId);
      version = ReviewInput.version(version);
      reason = ReviewInput.clean(reason, 500);
      if (input == null) throw ReviewInput.invalid();
      key = ReviewInput.key(key);
    }

    public String fingerprint() {
      return Fingerprint.of(
          Operation.CORRECTION_RECEIVED, reviewId, version, reason, received(input));
    }

    public List<PermissionCode> permissions() {
      return intake(input);
    }
  }

  private static String received(ReviewInput i) {
    return Fingerprint.of(
        "RECEIVED", i.body(), i.displayName(), i.receivedVia(), i.receivedAt(), i.originOrderId());
  }

  /** 関連受注を結ぶ受付は、受注を操作できる人にだけ許す。再送でも同じ権限を求める。 */
  private static List<PermissionCode> intake(ReviewInput input) {
    return input.originOrderId() == null
        ? List.of(PermissionCode.REVIEW_MANAGE)
        : List.of(PermissionCode.REVIEW_MANAGE, PermissionCode.ORDER_MANAGE);
  }
}
