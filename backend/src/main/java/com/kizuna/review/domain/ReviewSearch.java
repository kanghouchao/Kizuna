package com.kizuna.review.domain;

import com.kizuna.review.domain.ReviewValues.PermissionStatus;
import com.kizuna.review.domain.ReviewValues.Status;
import com.kizuna.shared.exception.ServiceException;
import java.util.Locale;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

public record ReviewSearch(
    int page,
    int size,
    String query,
    String reviewId,
    Status status,
    PermissionStatus permissionStatus,
    Order order) {
  public enum Order {
    RECEIVED_DESC,
    RECEIVED_ASC,
    CREATED_DESC,
    CREATED_ASC
  }

  public ReviewSearch {
    if (page < 0 || size < 1 || size > 100 || order == null)
      throw new ServiceException("一覧のページ条件を確認してください");
    if (query != null) {
      query = query.strip();
      if (query.isEmpty()) query = null;
      else query = ReviewInput.clean(query, 60);
    }
    if (reviewId != null) reviewId = ReviewInput.id(reviewId);
  }

  public PageRequest pageable() {
    boolean asc = order == Order.RECEIVED_ASC || order == Order.CREATED_ASC;
    String field =
        order == Order.RECEIVED_ASC || order == Order.RECEIVED_DESC ? "receivedAt" : "createdAt";
    return PageRequest.of(
        page, size, Sort.by(asc ? Sort.Direction.ASC : Sort.Direction.DESC, field, "id"));
  }

  public Specification<ReviewRecord> specification() {
    Specification<ReviewRecord> spec = (root, q, cb) -> cb.conjunction();
    if (query != null) {
      String pattern =
          "%"
              + query
                  .toLowerCase(Locale.ROOT)
                  .replace("\\", "\\\\")
                  .replace("%", "\\%")
                  .replace("_", "\\_")
              + "%";
      spec = spec.and((root, q, cb) -> cb.like(cb.lower(root.get("displayName")), pattern, '\\'));
    }
    if (reviewId != null) spec = spec.and((root, q, cb) -> cb.equal(root.get("id"), reviewId));
    if (status != null) spec = spec.and((root, q, cb) -> cb.equal(root.get("status"), status));
    if (permissionStatus != null)
      spec = spec.and((root, q, cb) -> cb.equal(root.get("permissionStatus"), permissionStatus));
    return spec;
  }
}
