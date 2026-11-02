package com.kizuna.order.domain;

/** 顧客がどこで店を知ったか。媒体（MEDIA）だけが媒体を指し、ほかは媒体以外の経路として件数だけ数える。 */
public enum AcquisitionSource {
  MEDIA,
  REPEAT,
  REFERRAL,
  OTHER
}
