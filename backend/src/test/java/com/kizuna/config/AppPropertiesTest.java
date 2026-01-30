package com.kizuna.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AppPropertiesTest {

  @Test
  void propertiesWork() {
    AppProperties props = new AppProperties();
    props.setScheme(AppProperties.Scheme.HTTPS);

    AppProperties.Jwt jwt = new AppProperties.Jwt();
    jwt.setSecret("secret");
    jwt.setExpiration(1000L);
    props.setJwt(jwt);

    assertThat(props.getJwtSecret()).isEqualTo("secret");
    assertThat(props.getJwtExpiration()).isEqualTo(1000L);
    assertThat(props.getScheme()).isEqualTo("https");
  }
}
