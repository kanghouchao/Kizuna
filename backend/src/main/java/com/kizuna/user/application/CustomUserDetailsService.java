package com.kizuna.user.application;

import com.kizuna.shared.tenancy.TenantContext;
import com.kizuna.user.domain.Authorities;
import com.kizuna.user.domain.CentralPermission;
import com.kizuna.user.domain.CentralRole;
import com.kizuna.user.domain.CentralUserRepository;
import com.kizuna.user.domain.StoreUserRepository;
import com.kizuna.user.domain.TenantPermission;
import com.kizuna.user.domain.TenantRole;
import io.jsonwebtoken.lang.Collections;
import java.util.Collection;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.AllArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@AllArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

  private final CentralUserRepository centralUserRepository;
  private final StoreUserRepository storeUserRepository;
  private final TenantContext tenantContext;

  @Override
  @Transactional(readOnly = true)
  public UserDetails loadUserByUsername(final String username) throws UsernameNotFoundException {
    if (tenantContext.isTenant()) {
      return storeUserRepository
          .findByEmail(username)
          .map(
              u -> {
                List<GrantedAuthority> authorities =
                    buildAuthorities(
                        u.getRoles(),
                        TenantRole::getName,
                        TenantRole::getPermissions,
                        TenantPermission::getName);
                return buildUser(u.getEmail(), u.getPassword(), u.getEnabled(), authorities);
              })
          .orElseThrow(() -> new UsernameNotFoundException("ユーザーが見つかりません（テナント）: " + username));
    } else {
      return centralUserRepository
          .findByUsername(username)
          .map(
              u -> {
                List<GrantedAuthority> authorities =
                    buildAuthorities(
                        u.getRoles(),
                        CentralRole::getName,
                        CentralRole::getPermissions,
                        CentralPermission::getName);
                return buildUser(u.getUsername(), u.getPassword(), u.getEnabled(), authorities);
              })
          .orElseThrow(() -> new UsernameNotFoundException("ユーザーが見つかりません（セントラル）: " + username));
    }
  }

  private <R, P> List<GrantedAuthority> buildAuthorities(
      Collection<R> roles,
      Function<R, String> roleNameExtractor,
      Function<R, Collection<P>> permExtractor,
      Function<P, String> permNameExtractor) {

    if (Collections.isEmpty(roles)) {
      return List.of();
    }

    return roles.stream()
        .flatMap(
            r -> {
              String roleName = roleNameExtractor.apply(r);
              Stream<String> roleAuth =
                  Stream.of("ROLE_" + (roleName == null ? "" : roleName.toUpperCase()));

              Collection<P> perms = permExtractor.apply(r);
              Stream<String> permOriginal =
                  perms == null ? Stream.empty() : perms.stream().map(permNameExtractor);
              Stream<String> permWithPrefix =
                  perms == null
                      ? Stream.empty()
                      : perms.stream()
                          .map(
                              p ->
                                  Authorities.permission(
                                      permNameExtractor.apply(p) == null
                                          ? ""
                                          : permNameExtractor.apply(p).toUpperCase()));

              return Stream.concat(roleAuth, Stream.concat(permOriginal, permWithPrefix));
            })
        .distinct()
        .filter(StringUtils::isNotBlank)
        .map(SimpleGrantedAuthority::new)
        .collect(Collectors.toList());
  }

  private UserDetails buildUser(
      String username, String password, boolean enabled, List<GrantedAuthority> authorities) {
    return User.withUsername(username)
        .password(password)
        .disabled(!enabled)
        .authorities(authorities)
        .build();
  }
}
