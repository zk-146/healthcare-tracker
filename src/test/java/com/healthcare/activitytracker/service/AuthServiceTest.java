package com.healthcare.activitytracker.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.healthcare.activitytracker.exception.ResourceConflictException;
import com.healthcare.activitytracker.exception.UnauthorizedException;
import com.healthcare.activitytracker.model.dto.AuthResponse;
import com.healthcare.activitytracker.model.dto.LoginRequest;
import com.healthcare.activitytracker.model.dto.RegisterRequest;
import com.healthcare.activitytracker.model.entity.RefreshToken;
import com.healthcare.activitytracker.model.entity.User;
import com.healthcare.activitytracker.repository.RefreshTokenRepository;
import com.healthcare.activitytracker.repository.UserRepository;
import com.healthcare.activitytracker.util.JwtUtil;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

  @Mock private UserRepository userRepository;
  @Mock private RefreshTokenRepository refreshTokenRepository;
  @Mock private TokenBlacklistService tokenBlacklistService;

  private PasswordEncoder passwordEncoder;
  private JwtUtil jwtUtil;
  private AuthService authService;

  @BeforeEach
  void setUp() {
    passwordEncoder = new BCryptPasswordEncoder();
    jwtUtil =
        new JwtUtil(
            "test-secret-key-for-unit-tests-must-be-at-least-32-chars",
            "test-refresh-secret-key-for-tests-must-be-at-least-32-chars",
            900_000L,
            604_800_000L,
            "activity-tracker",
            "activity-tracker-users");
    authService =
        new AuthService(
            userRepository,
            refreshTokenRepository,
            passwordEncoder,
            jwtUtil,
            tokenBlacklistService);
  }

  @Test
  void register_successfullyCreatesUser() {
    RegisterRequest request = new RegisterRequest();
    request.setEmail("new@example.com");
    request.setPassword("StrongP@ss123");
    request.setFullName("Test User");

    when(userRepository.existsByEmail("new@example.com")).thenReturn(false);
    when(userRepository.save(any(User.class)))
        .thenAnswer(
            inv -> {
              User u = inv.getArgument(0);
              u =
                  User.builder()
                      .id(UUID.randomUUID())
                      .email(u.getEmail())
                      .passwordHash(u.getPasswordHash())
                      .fullName(u.getFullName())
                      .build();
              return u;
            });
    when(refreshTokenRepository.save(any(RefreshToken.class)))
        .thenAnswer(inv -> inv.getArgument(0));

    AuthResponse response = authService.register(request);

    assertThat(response.getToken()).isNotBlank();
    assertThat(response.getRefreshToken()).isNotBlank();
    assertThat(response.getEmail()).isEqualTo("new@example.com");
  }

  @Test
  void register_throwsConflict_whenEmailExists() {
    RegisterRequest request = new RegisterRequest();
    request.setEmail("existing@example.com");
    request.setPassword("StrongP@ss123");
    request.setFullName("Test User");

    when(userRepository.existsByEmail("existing@example.com")).thenReturn(true);

    assertThatThrownBy(() -> authService.register(request))
        .isInstanceOf(ResourceConflictException.class)
        .hasMessageContaining("Email already registered");
  }

  @Test
  void login_successfullyAuthenticates() {
    LoginRequest request = new LoginRequest();
    request.setEmail("user@example.com");
    request.setPassword("StrongP@ss123");

    String hash = passwordEncoder.encode("StrongP@ss123");
    User user =
        User.builder()
            .id(UUID.randomUUID())
            .email("user@example.com")
            .passwordHash(hash)
            .fullName("Test User")
            .build();

    when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
    when(refreshTokenRepository.save(any(RefreshToken.class)))
        .thenAnswer(inv -> inv.getArgument(0));

    AuthResponse response = authService.login(request);

    assertThat(response.getToken()).isNotBlank();
    assertThat(response.getEmail()).isEqualTo("user@example.com");
  }

  @Test
  void login_throwsUnauthorized_whenEmailNotFound() {
    LoginRequest request = new LoginRequest();
    request.setEmail("unknown@example.com");
    request.setPassword("StrongP@ss123");

    when(userRepository.findByEmail("unknown@example.com")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> authService.login(request)).isInstanceOf(UnauthorizedException.class);
  }

  @Test
  void login_throwsUnauthorized_whenPasswordWrong() {
    LoginRequest request = new LoginRequest();
    request.setEmail("user@example.com");
    request.setPassword("wrongpassword!");

    User user =
        User.builder()
            .id(UUID.randomUUID())
            .email("user@example.com")
            .passwordHash(passwordEncoder.encode("correctpassword!"))
            .fullName("Test User")
            .build();

    when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));

    assertThatThrownBy(() -> authService.login(request)).isInstanceOf(UnauthorizedException.class);
  }

  @Test
  void refresh_withValidToken_returnsNewTokens() {
    UUID userId = UUID.randomUUID();
    String refreshToken = jwtUtil.generateRefreshToken(userId, "user@example.com");
    String tokenHash = AuthService.sha256(refreshToken);

    User user =
        User.builder()
            .id(userId)
            .email("user@example.com")
            .passwordHash("hash")
            .fullName("Test User")
            .build();

    RefreshToken storedToken =
        RefreshToken.builder().tokenHash(tokenHash).user(user).revoked(false).build();

    when(refreshTokenRepository.findByTokenHash(tokenHash)).thenReturn(Optional.of(storedToken));
    when(userRepository.findById(userId)).thenReturn(Optional.of(user));
    when(refreshTokenRepository.save(any(RefreshToken.class)))
        .thenAnswer(inv -> inv.getArgument(0));

    AuthResponse response = authService.refresh(refreshToken);
    assertThat(response.getToken()).isNotBlank();
  }

  @Test
  void refresh_withInvalidToken_throwsUnauthorized() {
    assertThatThrownBy(() -> authService.refresh("invalid.token.here"))
        .isInstanceOf(UnauthorizedException.class);
  }

  @Test
  void refresh_withRevokedToken_throwsUnauthorized() {
    UUID userId = UUID.randomUUID();
    String refreshToken = jwtUtil.generateRefreshToken(userId, "user@example.com");
    String tokenHash = AuthService.sha256(refreshToken);

    User user =
        User.builder()
            .id(userId)
            .email("user@example.com")
            .passwordHash("hash")
            .fullName("Test User")
            .build();

    RefreshToken storedToken =
        RefreshToken.builder()
            .tokenHash(tokenHash)
            .user(user)
            .revoked(true) // Already revoked
            .build();

    when(refreshTokenRepository.findByTokenHash(tokenHash)).thenReturn(Optional.of(storedToken));

    assertThatThrownBy(() -> authService.refresh(refreshToken))
        .isInstanceOf(UnauthorizedException.class)
        .hasMessageContaining("revoked");
  }
}
