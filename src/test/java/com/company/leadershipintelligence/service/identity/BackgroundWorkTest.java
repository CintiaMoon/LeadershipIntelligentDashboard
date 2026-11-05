package com.company.leadershipintelligence.service.identity;

import org.junit.jupiter.api.Test;

import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class BackgroundWorkTest {

    @Test
    void aFullQueueDropsTheJobInsteadOfFailingTheRequest() {
        BackgroundWork work = new BackgroundWork(job -> {
            throw new RejectedExecutionException("queue full");
        });
        assertThatCode(() -> work.run(() -> { })).doesNotThrowAnyException();
    }

    @Test
    void aFailingJobDoesNotReachTheCaller() {
        AtomicInteger ran = new AtomicInteger();
        BackgroundWork work = new BackgroundWork(Runnable::run);
        assertThatCode(() -> work.run(() -> {
            ran.incrementAndGet();
            throw new IllegalStateException("smtp down");
        })).doesNotThrowAnyException();
        assertThat(ran).hasValue(1);
    }
}
