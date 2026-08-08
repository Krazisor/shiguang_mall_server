package org.dhu.shiguang_market.merchantwallet.scheduler;

import org.dhu.shiguang_market.merchantwallet.service.MerchantWalletService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class MerchantWalletTasks {
    private final MerchantWalletService service;
    public MerchantWalletTasks(MerchantWalletService service) { this.service = service; }
    @Scheduled(cron = "0 */5 * * * *")
    public void releaseSettlements() { service.releaseSettlements(100, false); }
    @Scheduled(cron = "30 */5 * * * *")
    public void processVirtualWithdrawals() { service.processWithdrawals(100, false); }
}
