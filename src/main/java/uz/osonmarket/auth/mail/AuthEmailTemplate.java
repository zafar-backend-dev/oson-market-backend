package uz.osonmarket.auth.mail;

import java.net.URI;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;
import uz.osonmarket.auth.api.AuthMessages;

@Component
class AuthEmailTemplate {
    private final AuthMessages messages;
    private final String logoUrl;

    AuthEmailTemplate(AuthMessages messages, @Value("${app.email.public-base-url:}") String baseUrl) {
        this.messages = messages;
        if (baseUrl.isBlank()) {
            logoUrl = "";
        } else {
            URI uri = URI.create(baseUrl.strip());
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null
                    || uri.getRawQuery() != null || uri.getRawFragment() != null) {
                throw new IllegalArgumentException("app.email.public-base-url must be an HTTPS base URL");
            }
            logoUrl = uri.toASCIIString().replaceAll("/+$", "") + "/api/site/logo";
        }
    }

    String render(String purpose, String code, long ttlSeconds, Locale locale) {
        String title = messages.text("mail.heading." + purpose, locale);
        String logo = logoUrl.isEmpty() ? "" : """
                <img src="%s" alt="Oson Market" width="180"
                  style="display:block;width:180px;max-width:100%%;height:auto;margin:0 auto 12px;border:0;">
                """.formatted(HtmlUtils.htmlEscape(logoUrl));
        return """
                <!doctype html>
                <html lang="%s"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
                <title>%s</title></head>
                <body style="margin:0;background:#f1f5f3;color:#142922;font-family:Arial,sans-serif;">
                <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" style="background:#f1f5f3;">
                <tr><td align="center" style="padding:28px 12px;">
                <table role="presentation" width="100%%" cellspacing="0" cellpadding="0"
                  style="max-width:520px;background:#ffffff;border:1px solid #dce8e0;border-radius:18px;">
                <tr><td align="center" style="padding:26px 20px;background:#eaf7ef;border-radius:18px 18px 0 0;">
                %s<strong style="font-size:22px;color:#145632;">Oson Market</strong>
                </td></tr>
                <tr><td style="padding:28px 20px;">
                <h1 style="font-size:25px;line-height:1.3;margin:0 0 12px;">%s</h1>
                <p style="font-size:16px;line-height:1.6;margin:0 0 20px;">%s</p>
                <table role="presentation" width="100%%" cellspacing="0" cellpadding="0">
                <tr><td align="center" style="padding:18px 10px;background:#eaf7ef;border:1px solid #b8dec5;border-radius:12px;">
                <strong dir="ltr" style="font:700 32px/1.2 monospace;letter-spacing:5px;color:#145632;">%s</strong>
                </td></tr></table>
                <p style="font-size:14px;line-height:1.5;margin:16px 0 0;">%s</p>
                <p style="font-size:14px;line-height:1.5;color:#465b50;margin:12px 0 0;">%s</p>
                </td></tr></table>
                </td></tr></table></body></html>
                """.formatted(AuthMessages.language(locale), HtmlUtils.htmlEscape(title), logo, HtmlUtils.htmlEscape(title),
                HtmlUtils.htmlEscape(messages.text("mail.intro", locale)), HtmlUtils.htmlEscape(code),
                HtmlUtils.htmlEscape(messages.text("mail.expiry", locale, ttlSeconds)),
                HtmlUtils.htmlEscape(messages.text("mail.safety", locale)));
    }
}
