package server.poptato.auth.application;

import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.api.model.PortBinding;
import com.github.dockerjava.api.model.Ports;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import server.poptato.auth.api.request.ReissueTokenRequestDto;
import server.poptato.auth.application.service.AuthService;
import server.poptato.auth.application.service.JwtService;
import server.poptato.auth.exception.AuthException;
import server.poptato.external.oauth.SocialService;
import server.poptato.external.oauth.SocialServiceProvider;
import server.poptato.global.dto.TokenPair;
import server.poptato.todo.constant.TutorialMessage;
import server.poptato.todo.domain.entity.Todo;
import server.poptato.todo.domain.repository.TodoRepository;
import server.poptato.todo.domain.value.TodayStatus;
import server.poptato.todo.domain.value.Type;
import server.poptato.user.domain.entity.User;
import server.poptato.user.domain.repository.UserRepository;
import server.poptato.user.exception.UserException;
import server.poptato.user.validator.UserValidator;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static server.poptato.auth.exception.errorcode.AuthExceptionErrorCode.INVALID_TOKEN;

@Testcontainers
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
    private TodoRepository todoRepository;
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

    @Container
    private static final GenericContainer<?> redisContainer =
            new GenericContainer<>("redis:latest")
                    .withExposedPorts(6379)
                    .waitingFor(Wait.forListeningPort())
                    .withCreateContainerCmdModifier(cmd ->
                            cmd.withHostConfig(new HostConfig().withPortBindings(
                                    new PortBinding(Ports.Binding.bindPort(63799), new ExposedPort(6379))
                            ))
                    );

    @DynamicPropertySource
    static void configureRedisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.redis.host", redisContainer::getHost);
        registry.add("spring.redis.port", () -> redisContainer.getMappedPort(6379));
    }

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

    @DisplayName("회원가입 시, 튜토리얼 할 일이 성공적으로 추가된다.")
    @Test
    void createTutorialData_Success() {
        // given
        String kakaoId = "kakaoId";
        String email = "email";
        String name = "name";

        User user = User.builder()
                .kakaoId(kakaoId)
                .email(email)
                .name(name)
                .build();

        //when
        User newUser = userRepository.save(user);
        authService.createTutorialData(newUser.getId());

        //then
        List<Todo> todays = todoRepository.findByTypeAndUserId(Type.TODAY, newUser.getId());
        List<Todo> backlogs = todoRepository.findByTypeAndUserId(Type.BACKLOG, newUser.getId());

        assertThat(todays.size()).isEqualTo(1);
        assertThat(backlogs).hasSize(4)
                .extracting("content","backlogOrder")
                .containsExactlyInAnyOrder(
                        tuple(TutorialMessage.BACKLOG_NEW_TODO, 4),
                        tuple(TutorialMessage.BACKLOG_BOOKMARK_DDAY, 3),
                        tuple(TutorialMessage.BACKLOG_DRAG_AND_DROP,2),
                        tuple(TutorialMessage.BACKLOG_LEFT_SWIPE,1)
                );
    }
}
