package com.kizuna.service.api.dto;

import com.kizuna.service.domain.ServiceItem;
import com.kizuna.service.domain.ServiceRevision;
import com.kizuna.service.domain.ServiceTerms;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface ServiceMapper {
  @Mapping(target = "version", source = "revisionNumber")
  @Mapping(target = "kind", source = "terms.kind")
  @Mapping(target = "name", source = "terms.name")
  @Mapping(target = "durationMinutes", source = "terms.durationMinutes")
  @Mapping(target = "chargeType", source = "terms.chargeType")
  @Mapping(target = "price", source = "terms.price")
  @Mapping(target = "remuneration", source = "terms.remuneration")
  ServiceResponse response(ServiceItem item);

  default ServiceSummary summary(ServiceItem item) {
    return snapshot(item.getId(), item.getTerms(), item.getRevisionNumber(), item.isDeleted());
  }

  default ServiceRevisionResponse revision(ServiceRevision revision) {
    long number = revision.getRevisionNumber();
    return new ServiceRevisionResponse(
        revision.getId(),
        number,
        revision.getOperation(),
        revision.getActorId().toString(),
        revision.getOccurredAt(),
        revision.getBeforeTerms() == null
            ? null
            : snapshot(revision.getServiceId(), revision.getBeforeTerms(), number - 1, false),
        snapshot(
            revision.getServiceId(),
            revision.getAfterTerms(),
            number,
            revision.getOperation() == ServiceRevision.Operation.DELETED));
  }

  private static ServiceSummary snapshot(
      String id, ServiceTerms terms, long version, boolean deleted) {
    return new ServiceSummary(
        id,
        terms.getKind(),
        terms.getName(),
        terms.getDurationMinutes(),
        terms.getChargeType(),
        terms.getPrice(),
        terms.getRemuneration(),
        version,
        deleted);
  }
}
