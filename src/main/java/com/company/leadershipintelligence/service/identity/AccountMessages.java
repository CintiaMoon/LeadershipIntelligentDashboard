package com.company.leadershipintelligence.service.identity;

import com.company.leadershipintelligence.config.properties.IdentityProperties;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/** The text of the emails of the account flows. Plain text; the only link in a message is the one the recipient asked for. */
@Component
public class AccountMessages {

    private final MailDispatcher mail;
    private final IdentityProperties properties;

    public AccountMessages(MailDispatcher mail, IdentityProperties properties) {
        this.mail = mail;
        this.properties = properties;
    }

    public void verification(String to, String name, String token) {
        mail.send(to, "Confirm your email address",
                "Hello " + name + ",\n\nConfirm your email address to finish creating your account:\n\n" + link("/verify-email", token)
                        + "\n\nThe link works for " + hours(properties.verificationTtl()) + " and only once. If you did not create an account, ignore this message.\n");
    }

    public void alreadyRegistered(String to, String name) {
        mail.send(to, "You already have an account",
                "Hello " + name + ",\n\nSomeone tried to create an account with this email address, but you already have one. "
                        + "If it was you and you forgot your password, you can reset it here:\n\n" + properties.webBaseUrl() + "/forgot-password"
                        + "\n\nIf it was not you, ignore this message; nothing was changed.\n");
    }

    public void passwordReset(String to, String name, String token) {
        mail.send(to, "Reset your password",
                "Hello " + name + ",\n\nSomeone asked to reset the password of your account. To choose a new one, open:\n\n" + link("/reset-password", token)
                        + "\n\nThe link works for " + minutes(properties.resetTtl()) + " and only once. If you did not ask for this, ignore this message; your password is unchanged.\n");
    }

    public void passwordChanged(String to, String name) {
        mail.send(to, "Your password was changed",
                "Hello " + name + ",\n\nThe password of your account was just changed and every other sign-in was ended. "
                        + "If this was not you, reset your password now:\n\n" + properties.webBaseUrl() + "/forgot-password\n");
    }

    private String link(String path, String token) {
        return properties.webBaseUrl() + path + "?token=" + URLEncoder.encode(token, StandardCharsets.UTF_8);
    }

    private static String hours(Duration d) {
        return d.toHours() + " hours";
    }

    private static String minutes(Duration d) {
        return d.toMinutes() + " minutes";
    }
}
