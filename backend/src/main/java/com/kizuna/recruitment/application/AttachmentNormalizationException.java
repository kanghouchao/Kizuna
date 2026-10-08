package com.kizuna.recruitment.application;

import com.kizuna.shared.exception.ConflictException;

final class AttachmentNormalizationException extends ConflictException {
  AttachmentNormalizationException(String message) {
    super(message);
  }
}
