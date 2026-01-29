import com.kizuna.utils.JwtUtil;
import jakarta.annotation.security.PermitAll;
import jakarta.annotation.security.RolesAllowed;
import jakarta.validation.Valid;
import java.security.Principal;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller for Central Authentication.
 *
 * @author KangHouchao
 */
@RestController
@RequestMapping("/central")
@RequiredArgsConstructor
public class CentralAuthController {

  private final CentralAuthService authService;
  private final CentralUserRepository userRepository;
  private final RedisTemplate<String, Object> redisTemplate;
  private final JwtUtil jwtUtil;

  @PostMapping("/login")
  @PermitAll
  public ResponseEntity<Token> login(@Valid @RequestBody LoginRequest req) {
    return ResponseEntity.ok(authService.login(req.getUsername(), req.getPassword()));
  }

  @GetMapping("/me")
  @RolesAllowed("ADMIN")
  public ResponseEntity<AdminDto> me(Principal principal) {
    if (principal == null || principal.getName() == null) {
      return ResponseEntity.status(401).build();
    }
    CentralUser user = userRepository.findByUsername(principal.getName()).orElse(null);
    if (user == null) return ResponseEntity.status(404).build();
    return ResponseEntity.ok(new AdminDto(user.getId(), user.getUsername(), user.getUsername()));
  }

  @PostMapping("/logout")
  @RolesAllowed("ADMIN")
  public ResponseEntity<?> logout(
      @RequestHeader(name = "Authorization", required = false) String authHeader) {
    if (authHeader != null && authHeader.startsWith("Bearer ")) {
      String token = authHeader.substring(7);
      try {
        var claims = jwtUtil.getClaims(token);
        long exp = claims.getExpiration().getTime();
        long now = System.currentTimeMillis();
        long ttl = Math.max(0, exp - now);
        if (ttl > 0) {
          redisTemplate
              .opsForValue()
              .set("blacklist:tokens:" + token, "1", ttl, TimeUnit.MILLISECONDS);
        }
      } catch (Exception e) {
        // Token invalid or expired, ignore
      }
    }
    SecurityContextHolder.clearContext();
    return ResponseEntity.noContent().build();
  }
}
