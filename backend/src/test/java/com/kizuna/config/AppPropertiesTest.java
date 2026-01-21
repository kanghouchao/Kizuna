package com.kizuna.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AppPropertiesTest {

  @Test
  void defaultSchemeIsHttp() {
    AppProperties properties = new AppProperties();

    assertThat(properties.getScheme()).isEqualTo("http");
  }

  @Test
  void returnsSchemeNameWhenConfigured() {
    AppProperties properties = new AppProperties();
    properties.setScheme(AppProperties.Scheme.HTTPS);

    assertThat(properties.getScheme()).isEqualTo("https");
  }

  @Test
  void nullSchemeReturnsNull() {
    AppProperties properties = new AppProperties();
    properties.setScheme(null);

    assertThat(properties.getScheme()).isNull();
  }

  @Test
  void exposesJwtInfo() {
    AppProperties properties = new AppProperties();
    AppProperties.Jwt jwt = new AppProperties.Jwt();
    jwt.setSecret("top-secret");
    jwt.setExpiration(1234L);
    properties.setJwt(jwt);

    assertThat(properties.getJwtSecret()).isEqualTo("top-secret");
    assertThat(properties.getJwtExpiration()).isEqualTo(1234L);
  }
}
