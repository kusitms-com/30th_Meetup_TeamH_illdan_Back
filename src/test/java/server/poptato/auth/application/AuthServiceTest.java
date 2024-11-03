package server.poptato.auth.application;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import server.poptato.auth.api.request.ReissueTokenRequestDto;
import server.poptato.auth.application.service.AuthService;
import server.poptato.auth.application.service.JwtService;
import server.poptato.auth.exception.AuthException;
import server.poptato.external.oauth.SocialService;
import server.poptato.external.oauth.SocialServiceProvider;
import server.poptato.global.dto.TokenPair;
import server.poptato.user.domain.repository.UserRepository;
import server.poptato.user.exception.UserException;
import server.poptato.user.validator.UserValidator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static server.poptato.auth.exception.errorcode.AuthExceptionErrorCode.INVALID_TOKEN;

@SpringBootTest
public class AuthServiceTest {
    @Autowired
    private AuthService authService;
    @Autowired
    private SocialService socialService;
    @Autowired
    private SocialServiceProvider socialServiceProvider;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private JwtService jwtService;
    @Autowired
    private UserValidator userValidator;
    private ReissueTokenRequestDto validTokenRequestDto;
    private String accessToken;
    private String refreshToken;
    private String userIdTypeString = "1";

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @BeforeEach
    public void setup() {
        TokenPair tokenPair = jwtService.generateTokenPair(userIdTypeString);
        accessToken = tokenPair.accessToken();
        refreshToken = tokenPair.refreshToken();
        validTokenRequestDto = new ReissueTokenRequestDto(accessToken, refreshToken);
    }

    @AfterEach
    void deleteRefreshToken() {
        jwtService.deleteRefreshToken(userIdTypeString);
    }

    @DisplayName("로그아웃 시, 성공한다.")
    @Test
    public void logout_Success() {
        // given
        long userId = Long.parseLong(userIdTypeString);

        // when
        authService.logout(userId);

        // then
        String storedRefreshToken = redisTemplate.opsForValue().get(String.valueOf(userId));
        assertThat(storedRefreshToken).isNull();
    }

    @DisplayName("로그아웃 시, 유저가 존재하지 않으면 예외가 발생한다.")
    @Test
    public void logout_UserNotExistException() {
        // given
        Long userId = 5L;

        // then
        assertThrows(UserException.class, () -> authService.logout(userId));
    }

    @DisplayName("토큰 재발급 시, 성공한다.")
    @Test
    void refresh_Success() {
        //when
        TokenPair refreshTokenPair = authService.refresh(validTokenRequestDto);

        //then
        assertNotNull(refreshTokenPair);
        assertNotNull(refreshTokenPair.accessToken());
        assertNotNull(refreshTokenPair.refreshToken());
    }

    @DisplayName("토큰 재발급 시, 토큰이 유효하지 않으면 예외가 발생한다.")
    @Test
    void refresh_InvalidTokenException() {
        // given
        String invalidRefreshToken = "bbb";
        ReissueTokenRequestDto invalidRequestDto = ReissueTokenRequestDto.builder()
                .accessToken(accessToken)
                .refreshToken(invalidRefreshToken)
                .build();

        //when & then
        assertThrows(AuthException.class, () -> authService.refresh(invalidRequestDto))
                .getMessage()
                .equals(INVALID_TOKEN);
    }
}
