package com.kizuna.order.application;

import com.kizuna.customer.contact.GuestContactImport;
import com.kizuna.order.domain.OrderQuote;
import java.util.List;

/**
 * 確認で利用者が同意した内容。確認値はこれだけを署名する。
 *
 * <p>時刻を持たない — 採用時刻は試算と確定で別の瞬間に打たれるため、持てば照合が常に食い違う。残高・同意の現況のような表示だけの値も
 * 持たない（変わっても同意した内容は変わらない）。領域の型を埋め込まず値で組むのは、領域側に時刻の項目が増えても署名へ漏れないようにするため。
 */
public record OrderConsent(
    Course course,
    List<FeeLine> feeLines,
    int totalFee,
    int pointBasisAmount,
    int totalDurationMinutes,
    int totalRemuneration,
    Points points,
    List<SpecialService> specialServices,
    List<GuestContactImport> contactImports) {

  public record Course(
      String serviceId,
      String revisionId,
      long revisionNumber,
      String name,
      int durationMinutes,
      int price,
      int remuneration,
      String adoptionBasis) {}

  public record FeeLine(
      String lineId,
      String kind,
      String name,
      int amount,
      Integer durationMinutes,
      int remuneration,
      String serviceId,
      String revisionId,
      Long revisionNumber,
      String adoptionBasis) {}

  public record Points(
      boolean memberLinked, String memberCode, int usageUnit, int usePoints, int grantPoints) {}

  public record SpecialService(
      String serviceId,
      String revisionId,
      long revisionNumber,
      long termsVersion,
      String name,
      String chargeType,
      int price,
      int remuneration,
      String adoptionBasis,
      String enrollmentId,
      String consentEventId,
      Long consentVersion,
      boolean requiresAttention) {}

  static OrderConsent of(
      OrderQuote quote,
      OrderPoints points,
      List<SpecialServiceStatus> specialServices,
      List<GuestContactImport> contactImports) {
    var c = quote.course();
    return new OrderConsent(
        new Course(
            c.serviceId(),
            c.revisionId(),
            c.revisionNumber(),
            c.name(),
            c.durationMinutes(),
            c.price(),
            c.remuneration(),
            c.adoptionBasis()),
        quote.feeLines().stream()
            .map(
                line -> {
                  var adoption = line.adoption();
                  return new FeeLine(
                      line.lineId(),
                      line.kind().name(),
                      line.name(),
                      line.amount(),
                      line.durationMinutes(),
                      line.remuneration(),
                      adoption == null ? null : adoption.serviceId(),
                      adoption == null ? null : adoption.revisionId(),
                      adoption == null ? null : adoption.revisionNumber(),
                      adoption == null ? null : adoption.adoptionBasis());
                })
            .toList(),
        quote.totalFee(),
        quote.grantBasisAmount(),
        quote.totalDurationMinutes(),
        quote.totalRemuneration(),
        points == null
            ? null
            : new Points(
                points.memberLinked(),
                points.memberCode(),
                points.usageUnit(),
                points.usePoints(),
                points.grantPoints()),
        specialServices.stream()
            .map(
                status -> {
                  var s = status.snapshot();
                  return new SpecialService(
                      s.serviceId(),
                      s.revisionId(),
                      s.revisionNumber(),
                      s.termsVersion(),
                      s.name(),
                      s.chargeType(),
                      s.price(),
                      s.remuneration(),
                      s.adoptionBasis(),
                      s.enrollmentId(),
                      s.consentEventId(),
                      s.consentVersion(),
                      status.requiresAttention());
                })
            .toList(),
        contactImports);
  }
}
