package org.dhu.shiguang_market.coupon.event;

import org.dhu.shiguang_market.coupon.service.CouponTaskService;
import org.springframework.scheduling.annotation.Async;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
public class CouponRegistrationListener {
    private static final Logger log = LoggerFactory.getLogger(CouponRegistrationListener.class);
    private final CouponTaskService tasks;

    public CouponRegistrationListener(CouponTaskService tasks) {
        this.tasks = tasks;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onRegistered(UserRegisteredEvent event) {
        try {
            tasks.grantSystemForUser(event.userId());
        } catch (RuntimeException ex) {
            // The compensation task is the durable retry path; registration has already committed.
            log.warn("System coupon grant failed eventId={} userId={}", event.eventId(), event.userId(), ex);
        }
    }
}
