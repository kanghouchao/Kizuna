package com.kizuna.order.domain;

import com.kizuna.shared.exception.ServiceException;

/** 受注の知った経路と媒体の組。媒体は経路が MEDIA のときだけ持ち、両方空は未入力。 */
public record OrderAcquisition(AcquisitionSource source, String mediumId) {
  public static final OrderAcquisition UNSET = new OrderAcquisition(null, null);

  public OrderAcquisition {
    if (source == AcquisitionSource.MEDIA && mediumId == null)
      throw new ServiceException("知った経路が媒体のときは媒体を選択してください");
    if (source != AcquisitionSource.MEDIA && mediumId != null)
      throw new ServiceException("媒体は知った経路が媒体のときだけ指定できます");
  }
}
