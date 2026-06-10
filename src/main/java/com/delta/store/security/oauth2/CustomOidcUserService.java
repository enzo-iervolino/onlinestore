package com.delta.store.security.oauth2;

import com.delta.store.user.model.User;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

@Service
public class CustomOidcUserService extends OidcUserService {

    private final CustomOAuth2UserService customOAuth2UserService;

    public CustomOidcUserService(CustomOAuth2UserService customOAuth2UserService) {
        this.customOAuth2UserService = customOAuth2UserService;
    }

    @Override
    @Transactional
    public OidcUser loadUser(OidcUserRequest userRequest) throws OAuth2AuthenticationException {
        OidcUser oidcUser = super.loadUser(userRequest);

        String registrationId = userRequest.getClientRegistration().getRegistrationId();
        Map<String, Object> attributes = new HashMap<>(oidcUser.getAttributes());

        User user = customOAuth2UserService.saveOrUpdateOAuth2User(registrationId, attributes);

        /*
         * Google usa OIDC quando è presente lo scope "openid".
         * Impostiamo "email" come nameAttributeKey per mantenere compatibili
         * GlobalModelAttributes, CurrentUserControllerAdvice, profilo, checkout
         * e tutti i punti esistenti che leggono authentication.getName().
         */
        if (oidcUser.getUserInfo() != null) {
            return new DefaultOidcUser(
                    customOAuth2UserService.buildAuthorities(user),
                    oidcUser.getIdToken(),
                    oidcUser.getUserInfo(),
                    "email"
            );
        }

        return new DefaultOidcUser(
                customOAuth2UserService.buildAuthorities(user),
                oidcUser.getIdToken(),
                "email"
        );
    }
}
