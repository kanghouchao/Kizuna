package com.kizuna.order.domain;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.kizuna.shared.exception.ServiceException;
import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import java.util.Locale;
import java.util.Map;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record ContactSnapshot(String name, String phoneNumber, String email, String lineId) {
  public static ContactSnapshot empty() {
    return new ContactSnapshot(null, null, null, null);
  }

  public static ContactSnapshot normalize(
      String name, String phoneNumber, String email, String lineId) {
    String phone = blankToNull(phoneNumber);
    if (phone != null) {
      var util = PhoneNumberUtil.getInstance();
      try {
        var parsed = util.parse(phone, "JP");
        if (!util.isValidNumberForRegion(parsed, "JP") || parsed.hasExtension())
          throw new NumberParseException(NumberParseException.ErrorType.NOT_A_NUMBER, "invalid");
        phone = util.format(parsed, PhoneNumberUtil.PhoneNumberFormat.E164);
      } catch (NumberParseException e) {
        throw new ServiceException(
            "日本の有効な電話番号を入力してください", Map.of("contact_snapshot.phone_number", "日本の電話番号を入力してください"));
      }
    }
    String mail = blankToNull(email);
    if (mail != null) {
      try {
        var address = new InternetAddress(mail, true);
        address.validate();
        if (!mail.equals(address.getAddress())
            || address.getPersonal() != null
            || address.isGroup()) throw new AddressException("invalid");
      } catch (AddressException e) {
        throw new ServiceException(
            "メールアドレスを確認してください", Map.of("contact_snapshot.email", "有効なメールアドレスを入力してください"));
      }
      int at = mail.lastIndexOf('@');
      mail = mail.substring(0, at + 1) + mail.substring(at + 1).toLowerCase(Locale.ROOT);
    }
    return new ContactSnapshot(blankToNull(name), phone, mail, blankToNull(lineId));
  }

  private static String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value.strip();
  }
}
