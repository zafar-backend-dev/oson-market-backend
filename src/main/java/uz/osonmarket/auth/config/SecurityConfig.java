package uz.osonmarket.auth.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.*;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.i18n.AcceptHeaderLocaleResolver;
import uz.osonmarket.auth.api.AuthException;
import uz.osonmarket.auth.api.AuthMessages;
import uz.osonmarket.auth.persistence.UserRepository;
import uz.osonmarket.auth.service.AccessTokens;
import uz.osonmarket.auth.service.GoogleAuthService;
import uz.osonmarket.auth.service.GoogleLoginExchange;

@Configuration
public class SecurityConfig {
    private final ObjectMapper json;
    private final AuthMessages messages;

    public SecurityConfig(ObjectMapper json, AuthMessages messages) {
        this.json = json;
        this.messages = messages;
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    SecretKey jwtSecret(@Value("${jwt.secret}") String configured) {
        byte[] key = HexFormat.of().parseHex(configured);
        if (key.length < 32) {
            throw new IllegalArgumentException("jwt.secret must contain at least 32 bytes in hexadecimal");
        }
        return new SecretKeySpec(key, "HmacSHA256");
    }

    @Bean
    JwtEncoder jwtEncoder(SecretKey key) {
        return new NimbusJwtEncoder(new com.nimbusds.jose.jwk.source.ImmutableSecret<>(key));
    }

    @Bean
    JwtDecoder jwtDecoder(SecretKey key, UserRepository users) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                new JwtTimestampValidator(Duration.ZERO), new JwtIssuerValidator(AccessTokens.ISSUER), token -> {
                    try {
                        UUID userId = UUID.fromString(token.getSubject());
                        Number version = token.getClaim("ver");
                        boolean valid = users.findById(userId)
                                .filter(user -> "ACTIVE".equals(user.getStatus()) && user.getEmailVerifiedAt() != null
                                        && version != null && Objects.equals(user.getCredentialVersion(), version.intValue()))
                                .isPresent();
                        if (valid) {
                            return OAuth2TokenValidatorResult.success();
                        }
                    } catch (IllegalArgumentException | ClassCastException ignored) {
                        // The token claims do not identify a valid account.
                    }
                    return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token"));
                }));
        return decoder;
    }

    @Bean
    LocaleResolver localeResolver() {
        return new AcceptHeaderLocaleResolver() {
            @Override
            public Locale resolveLocale(HttpServletRequest request) {
                // The client sends a single language selected in the application.
                String language = request.getHeader(HttpHeaders.ACCEPT_LANGUAGE);
                return language != null && language.strip().equalsIgnoreCase("ru")
                        ? Locale.forLanguageTag("ru")
                        : Locale.forLanguageTag("uz");
            }
        };
    }


    @Bean
    CorsConfigurationSource corsConfigurationSource(@Value("${app.cors-origin}") String origin,
            @Value("${app.local-frontend-origin}") String localOrigin,
            @Value("${app.mobile-frontend-origins}") List<String> mobileOrigins) {
        var publicCors = new CorsConfiguration();
        publicCors.setAllowedOriginPatterns(List.of("http://*", "https://*"));
        publicCors.setAllowedOrigins(mobileOrigins);
        publicCors.setAllowedMethods(List.of("*"));
        publicCors.setAllowedHeaders(List.of("*"));
        publicCors.setExposedHeaders(List.of("Retry-After", "Content-Range", "Accept-Ranges"));

        var trustedOrigins = new java.util.LinkedHashSet<>(mobileOrigins);
        trustedOrigins.add(origin);
        trustedOrigins.add(localOrigin);
        var sessionCors = new CorsConfiguration(publicCors);
        sessionCors.setAllowedOriginPatterns(List.of());
        sessionCors.setAllowedOrigins(List.copyOf(trustedOrigins));
        sessionCors.setAllowCredentials(true);

        var exchangeCors = new CorsConfiguration(sessionCors);
        exchangeCors.setAllowedOrigins(List.of(origin));

        var source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/auth/google/exchange", exchangeCors);
        source.registerCorsConfiguration("/api/**", sessionCors);
        return request -> {
            var matched = source.getCorsConfiguration(request);
            if (matched == null) return null;
            String supplied = request.getHeader(HttpHeaders.ORIGIN);
            return supplied != null && matched.getAllowedOrigins().contains(supplied) ? matched : publicCors;
        };
    }

    @Bean
    @Order(1)
    SecurityFilterChain api(HttpSecurity http, CorsConfigurationSource corsConfigurationSource, LocaleResolver locales, UserRepository users) throws Exception {
        return http.securityMatcher("/api/**")
                .cors(c -> c.configurationSource(corsConfigurationSource))
                .csrf(c -> c.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(a -> a
                        .requestMatchers(HttpMethod.POST, "/api/auth/sign-in", "/api/auth/sign-in/verify",
                                "/api/auth/sign-up", "/api/auth/sign-up/verify",
                                "/api/auth/forgot-password", "/api/auth/reset-password",
                                "/api/auth/google/exchange", "/api/auth/google/mobile/exchange", "/api/auth/refresh", "/api/auth/logout").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/categories", "/api/categories/*", "/api/categories/*/image").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/catalog/tree", "/api/catalog/products/*/images/*",
                                "/api/catalog/brands/*/logo").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/auth/me").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/catalog/products/*/social", "/api/catalog/products/*/comments",
                                "/api/catalog/products/*/comments/*/replies", "/api/catalog/products/*/comments/*/avatar").permitAll()
                        .requestMatchers("/api/catalog/products/*/like", "/api/catalog/products/*/comments",
                                "/api/catalog/products/*/comments/*", "/api/catalog/products/*/comments/*/replies",
                                "/api/catalog/products/*/comments/*/like").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/branches", "/api/branches/*/image").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/site/legal/*", "/api/site/logo", "/api/site/about", "/api/site/android-release", "/api/site/advertisements", "/api/site/advertisements/*/media").permitAll()
                        .requestMatchers("/api/admin/**").access((authentication, context) -> {
                            boolean admin = authentication.get() instanceof org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken token
                                    && users.findById(UUID.fromString(token.getName()))
                                    .filter(user -> "ADMIN".equals(user.getRole())).isPresent();
                            return new org.springframework.security.authorization.AuthorizationDecision(admin);
                        })
                        .requestMatchers("/api/orders", "/api/orders/**",
                                "/api/advertisement-notifications", "/api/advertisement-notifications/**").authenticated()
                        .requestMatchers("/api/profile", "/api/profile/**", "/api/users/delete-account/**", "/api/mobile/chat/**").authenticated()
                        .anyRequest().denyAll())
                .exceptionHandling(e -> e
                        .authenticationEntryPoint((req, res, ex) -> unauthorized(res, locales.resolveLocale(req)))
                        .accessDeniedHandler((req, res, ex) ->
                                error(res, 403, "auth.forbidden", locales.resolveLocale(req))))
                .oauth2ResourceServer(o -> o
                        .authenticationEntryPoint((req, res, ex) -> unauthorized(res, locales.resolveLocale(req)))
                        .jwt(j -> {}))
                .build();
    }

    @Bean
    @Order(2)
    SecurityFilterChain google(HttpSecurity http, GoogleAuthService service,
                               GoogleLoginExchange exchange, LocaleResolver locales,
            org.springframework.security.oauth2.client.registration.ClientRegistrationRepository clients) throws Exception {
        var resolver = new MobileGoogleAuthorizationResolver(clients, exchange);
        return http.authorizeHttpRequests(a -> a.requestMatchers("/oauth2/authorization/google",
                        "/login/oauth2/code/google", "/error").permitAll()
                        .requestMatchers(HttpMethod.GET, "/ws/orders", "/ws/mobile/chat", "/ws/admin/chat", "/swagger-ui.html", "/swagger-ui/**",
                                "/v3/api-docs", "/v3/api-docs/**", "/v3/api-docs.yaml").permitAll()
                        .anyRequest().denyAll())
                .oauth2Login(o -> o
                        .authorizationEndpoint(endpoint -> endpoint.authorizationRequestResolver(resolver))
                        .successHandler((req, res, authentication) -> {
                            var locale = locales.resolveLocale(req);
                            AccessTokens.Token token;
                            try {
                                token = service.signIn((OidcUser) authentication.getPrincipal(), locale);
                            } catch (AuthException ex) {
                                exchange.failure(req, res, ex.code());
                                return;
                            } catch (RuntimeException ex) {
                                org.slf4j.LoggerFactory.getLogger(SecurityConfig.class)
                                        .error("Google sign-in failed ({})", ex.getClass().getSimpleName());
                                exchange.failure(req, res, "auth.google.failed");
                                return;
                            }
                            exchange.success(req, res, token);
                        })
                        .failureHandler((req, res, ex) -> exchange.failure(req, res, "auth.google.failed")))
                .build();
    }

    private void unauthorized(HttpServletResponse res, Locale locale) throws IOException {
        res.setHeader("WWW-Authenticate", "Bearer");
        error(res, 401, "auth.unauthorized", locale);
    }

    private void error(HttpServletResponse res, int status, String code, Locale locale) throws IOException {
        res.setStatus(status);
        response(res, locale);
        json.writeValue(res.getOutputStream(), Map.of("code", code, "message", messages.text(code, locale)));
    }

    private void response(HttpServletResponse res, Locale locale) {
        res.setContentType("application/json;charset=UTF-8");
        res.setHeader("Cache-Control", "no-store");
        res.setHeader("Content-Language", AuthMessages.language(locale));
    }
}
