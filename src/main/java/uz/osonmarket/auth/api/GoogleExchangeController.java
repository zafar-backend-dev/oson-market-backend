package uz.osonmarket.auth.api;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.util.Locale;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import uz.osonmarket.auth.service.AccessTokens;
import uz.osonmarket.auth.service.GoogleLoginExchange;

@RestController
public class GoogleExchangeController {
    private final GoogleLoginExchange exchange;
    private final SessionController sessions;

    public GoogleExchangeController(GoogleLoginExchange exchange, SessionController sessions) {
        this.exchange = exchange;
        this.sessions = sessions;
    }

    @PostMapping("/api/auth/google/exchange")
    public ResponseEntity<AccessTokens.Token> exchange(@Valid @RequestBody ExchangeRequest body,
                                                        HttpServletRequest request, Locale locale) {
        var token = exchange.redeem(request, body.loginCode());
        var response = ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_LANGUAGE, AuthMessages.language(locale));
        sessions.attach(response, token, request);
        return response.body(token);
    }

    public record ExchangeRequest(@NotNull @Pattern(regexp = "[A-Za-z0-9_-]{43}") String loginCode) {}
}
