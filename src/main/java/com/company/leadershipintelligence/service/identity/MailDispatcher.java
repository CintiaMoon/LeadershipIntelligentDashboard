package com.company.leadershipintelligence.service.identity;

import com.company.leadershipintelligence.config.properties.IdentityProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.concurrent.Executor;

/**
 * Sends the emails of the account flows through SMTP (decision D66). A message is sent only after the transaction that
 * asked for it has committed, so nobody gets a link for an account that was rolled back, and (in production) on a background
 * thread so the answer to the caller does not depend on the mail server. A failure is logged without the recipient or the
 * link; the user can ask again. Message bodies, which contain links, are never logged.
 */
@Component
public class MailDispatcher {

    private static final Logger LOG = LoggerFactory.getLogger(MailDispatcher.class);

    private final ObjectProvider<JavaMailSender> sender;
    private final IdentityProperties properties;
    private final Executor executor;

    public MailDispatcher(ObjectProvider<JavaMailSender> sender, IdentityProperties properties, @Qualifier("mailExecutor") Executor executor) {
        this.sender = sender;
        this.properties = properties;
        this.executor = executor;
    }

    public void send(String to, String subject, String body) {
        Runnable job = () -> deliver(to, subject, body);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    executor.execute(job);
                }
            });
        } else {
            executor.execute(job);
        }
    }

    private void deliver(String to, String subject, String body) {
        JavaMailSender mail = sender.getIfAvailable();
        if (mail == null) {
            LOG.warn("an email was not sent because no SMTP server is configured (set spring.mail.host)");
            return;
        }
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(properties.mailFrom());
            message.setTo(to);
            message.setSubject(subject);
            message.setText(body);
            mail.send(message);
        } catch (RuntimeException e) {
            LOG.warn("an email could not be sent type={}", e.getClass().getSimpleName());
        }
    }
}
