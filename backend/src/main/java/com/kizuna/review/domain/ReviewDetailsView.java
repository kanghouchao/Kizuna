package com.kizuna.review.domain;

import com.kizuna.review.domain.ReviewValues.PermissionStatus;
import com.kizuna.review.domain.ReviewValues.Status;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

public interface ReviewDetailsView extends ReviewSummaryView {
  String getBody();

  String getOriginOrderId();

  OffsetDateTime getOriginCheckedAt();

  Long getOriginOrderVersion();

  Long getRecordedBy();

  String getRecorderName();

  String getSupersedesId();

  String getSupersededById();

  default List<String> publicationBlockers() {
    var blockers = new ArrayList<String>();
    if (getStatus() == Status.WITHDRAWN) blockers.add("WITHDRAWN");
    else if (getStatus() != Status.APPROVED) blockers.add("NOT_APPROVED");
    if (getPermissionStatus() == PermissionStatus.NOT_GRANTED) blockers.add("NO_PERMISSION");
    else if (getPermissionStatus() == PermissionStatus.REVOKED) blockers.add("PERMISSION_REVOKED");
    return List.copyOf(blockers);
  }
}
