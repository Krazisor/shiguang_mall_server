package org.dhu.shiguang_market.coupon.scheduler;

import org.dhu.shiguang_market.coupon.service.CouponTaskService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class CouponTasks {
    private final CouponTaskService service;public CouponTasks(CouponTaskService service){this.service=service;}
    @Scheduled(cron="0 * * * * *") public void lifecycle(){service.start(500,false);service.end(500,false);service.expire(500,false);}
    @Scheduled(cron="30 * * * * *") public void recover(){service.recover(500,false);}
    @Scheduled(cron="0 */5 * * * *") public void grantSystem(){service.grantSystem(1000,false);}
    @Scheduled(cron="0 0 3 * * *") public void reconcile(){service.reconcile(1000);}
}
