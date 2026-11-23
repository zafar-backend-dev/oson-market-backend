package uz.osonmarket.auth.api;

import java.util.Locale;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;

@Component
public class AuthMessages {
    private final MessageSource messages;

    public AuthMessages(MessageSource messages) {
        this.messages = messages;
    }

    public String text(String code, Locale locale, Object... args) {
        return messages.getMessage(code, args, locale);
    }

    public static String language(Locale locale) {
        return "ru".equals(locale.getLanguage()) ? "ru" : "uz";
    }
}
