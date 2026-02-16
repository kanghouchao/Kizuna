package com.kizuna.utils;

import java.security.SecureRandom;
import lombok.experimental.UtilityClass;

@UtilityClass
public class RandomTokenUtils {
  private static final String CHARACTERS =
      "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
  private static final int TOKEN_LENGTH = 32;
  private static final SecureRandom random = new SecureRandom();

  public static String generate() {
    StringBuilder token = new StringBuilder(TOKEN_LENGTH);
    for (int i = 0; i < TOKEN_LENGTH; i++) {
      token.append(CHARACTERS.charAt(random.nextInt(CHARACTERS.length())));
    }
    return token.toString();
  }
}
