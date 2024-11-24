package server.poptato.auth.application.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import server.poptato.auth.api.request.LoginRequestDto;
import server.poptato.auth.api.request.ReissueTokenRequestDto;
import server.poptato.auth.application.response.LoginResponseDto;
import server.poptato.auth.converter.AuthDtoConverter;
import server.poptato.external.oauth.SocialService;
import server.poptato.external.oauth.SocialServiceProvider;
import server.poptato.external.oauth.SocialUserInfo;
import server.poptato.global.dto.TokenPair;
import server.poptato.todo.constant.TutorialMessage;
import server.poptato.todo.domain.entity.Todo;
import server.poptato.todo.domain.repository.TodoRepository;
import server.poptato.user.domain.entity.Mobile;
import server.poptato.user.domain.entity.User;
import server.poptato.user.domain.repository.MobileRepository;
import server.poptato.user.domain.repository.UserRepository;
import server.poptato.user.validator.UserValidator;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AuthService {
    private final JwtService jwtService;
    private final SocialServiceProvider socialServiceProvider;
    private final UserRepository userRepository;
    private final UserValidator userValidator;
    private final TodoRepository todoRepository;
    private final MobileRepository mobileRepository;

    public LoginResponseDto login(final LoginRequestDto loginRequestDto) {
        SocialService socialService = socialServiceProvider.getSocialService(loginRequestDto.socialType());
        SocialUserInfo userInfo = socialService.getUserData(loginRequestDto.accessToken());
        Optional<User> findUser = userRepository.findBySocialId(userInfo.socialId());
        if (findUser.isEmpty()) {
            User newUser = saveNewDatas(loginRequestDto, userInfo);
            return createLoginResponse(newUser.getId(), true);
        }
        updateImage(findUser.get(),userInfo);
        return createLoginResponse(findUser.get().getId(), false);
    }

    private User saveNewDatas(LoginRequestDto requestDto, SocialUserInfo userInfo) {
        User user = User.create(requestDto, userInfo);
        User newUser = userRepository.save(user);
        Mobile mobile = Mobile.create(requestDto, newUser.getId());
        mobileRepository.save(mobile);
        Todo turorialTodo = Todo.createBacklog(newUser.getId(), TutorialMessage.GUIDE, 1);
        todoRepository.save(turorialTodo);
        return newUser;
    }

    private void updateImage(User existingUser, SocialUserInfo userInfo) {
        if (existingUser.getImageUrl() == null || existingUser.getImageUrl().isEmpty()) {
            existingUser.updateImageUrl(userInfo.imageUrl());
            userRepository.save(existingUser);
        }
    }

    private LoginResponseDto createLoginResponse(Long userId, boolean isNewUser) {
        TokenPair tokenPair = jwtService.generateTokenPair(String.valueOf(userId));
        return AuthDtoConverter.toLoginDto(tokenPair, userId, isNewUser);
    }

    public void logout(final Long userId) {
        userValidator.checkIsExistUser(userId);
        jwtService.deleteRefreshToken(String.valueOf(userId));
    }

    public TokenPair refresh(final ReissueTokenRequestDto reissueTokenRequestDto) {
        checkIsValidToken(reissueTokenRequestDto.getRefreshToken());

        final String userId = jwtService.getUserIdInToken(reissueTokenRequestDto.getRefreshToken());
        userValidator.checkIsExistUser(Long.parseLong(userId));

        final TokenPair tokenPair = jwtService.generateTokenPair(userId);
        jwtService.saveRefreshToken(userId, tokenPair.refreshToken());

        return tokenPair;
    }

    private void checkIsValidToken(String refreshToken) {
        try {
            jwtService.verifyToken(refreshToken);
            jwtService.compareRefreshToken(jwtService.getUserIdInToken(refreshToken), refreshToken);
        } catch (Exception e) {
            throw e;
        }
    }
}
