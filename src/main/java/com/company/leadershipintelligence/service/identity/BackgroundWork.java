package com.company.leadershipintelligence.service.identity;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

/**
 * Runs the slow, non-essential part of an account request (sending an email, issuing a link) off the request thread, so the
 * answer does not depend on the mail server and takes the same time whatever the address. A full queue or a failing job is
 * logged (type only) and dropped: the caller was already answered and the user can ask again. It never turns into a 500.
 */
@Component
public class BackgroundWork {

    private static final Logger LOG = LoggerFactory.getLogger(BackgroundWork.class);

    private final Executor executor;

    public BackgroundWork(@Qualifier("mailExecutor") Executor executor) {
        this.executor = executor;
    }

    public void run(Runnable job) {
        try {
            executor.execute(() -> {
                try {
                    job.run();
                } catch (RuntimeException e) {
                    LOG.warn("a background account task failed type={}", e.getClass().getSimpleName());
                }
            });
        } catch (RejectedExecutionException e) {
            LOG.warn("a background account task was dropped because the queue is full");
        }
    }
}
