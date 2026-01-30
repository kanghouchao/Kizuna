package com.kizuna.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ServiceExceptionTest {

  @Test
  void exceptionWorks() {
    ServiceException ex = new ServiceException("msg");
    assertThat(ex.getMessage()).isEqualTo("msg");
  }
}
