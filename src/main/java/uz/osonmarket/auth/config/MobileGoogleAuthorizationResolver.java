package uz.osonmarket.auth.config;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.core.OAuth2AuthorizationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import uz.osonmarket.auth.service.GoogleLoginExchange;

final class MobileGoogleAuthorizationResolver implements OAuth2AuthorizationRequestResolver {
    private final DefaultOAuth2AuthorizationRequestResolver delegate;
    private final GoogleLoginExchange exchange;

    MobileGoogleAuthorizationResolver(ClientRegistrationRepository clients, GoogleLoginExchange exchange) {
        this.delegate = new DefaultOAuth2AuthorizationRequestResolver(clients, "/oauth2/authorization");
        this.exchange = exchange;
    }

    @Override
    public OAuth2AuthorizationRequest resolve(HttpServletRequest request) {
        return bind(request, delegate.resolve(request));
    }

    @Override
    public OAuth2AuthorizationRequest resolve(HttpServletRequest request, String clientId) {
        return bind(request, delegate.resolve(request, clientId));
    }

    private OAuth2AuthorizationRequest bind(HttpServletRequest request, OAuth2AuthorizationRequest authorization) {
        if (authorization != null) {
            if (!"true".equals(request.getParameter("termsAccepted"))
                    || !"true".equals(request.getParameter("privacyAcknowledged"))) {
                throw new OAuth2AuthorizationException(new OAuth2Error("consent_required"));
            }
            exchange.bindMobileRequest(request, authorization.getState(), request.getParameter("mobileChallenge"));
        }
        return authorization;
    }
}
