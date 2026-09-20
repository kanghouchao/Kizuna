package com.kizuna.shared.validation;

import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.kizuna.shared.exception.ServiceException;
import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import java.util.Locale;
import java.util.Map;

public final class ContactValues {
  private ContactValues() {}

  public static String phone(String value, String field) {
    String phone = blankToNull(value);
    if (phone != null) {
      var util = PhoneNumberUtil.getInstance();
      try {
        var parsed = util.parse(phone, "JP");
        if (!util.isValidNumberForRegion(parsed, "JP") || parsed.hasExtension())
          throw new NumberParseException(NumberParseException.ErrorType.NOT_A_NUMBER, "invalid");
        phone = util.format(parsed, PhoneNumberUtil.PhoneNumberFormat.E164);
      } catch (NumberParseException e) {
        throw new ServiceException("日本の有効な電話番号を入力してください", Map.of(field, "日本の電話番号を入力してください"));
      }
    }
    return phone;
  }

  public static String email(String value, String field) {
    String mail = blankToNull(value);
    if (mail != null) {
      try {
        var address = new InternetAddress(mail, true);
        address.validate();
        if (!mail.equals(address.getAddress())
            || address.getPersonal() != null
            || address.isGroup()) throw new AddressException("invalid");
      } catch (AddressException e) {
        throw new ServiceException("メールアドレスを確認してください", Map.of(field, "有効なメールアドレスを入力してください"));
      }
      int at = mail.lastIndexOf('@');
      mail = mail.substring(0, at + 1) + mail.substring(at + 1).toLowerCase(Locale.ROOT);
    }
    return mail;
  }

  public static String search(String value) {
    String term = value.strip();
    try {
      if (term.contains("@")) return email(term, "search");
      if (term.matches("[+0-9()\\s-]+")) return phone(term, "search");
    } catch (ServiceException ignored) {
      // 完全な宛先ではない検索語は部分一致に用いる。
    }
    return term;
  }

  public static String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value.strip();
  }
}
