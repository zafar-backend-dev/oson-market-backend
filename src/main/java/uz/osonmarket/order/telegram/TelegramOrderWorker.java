package uz.osonmarket.order.telegram;

import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@EnableScheduling
class TelegramOrderWorker {
    private final TelegramOrderQueue queue;
    private final TelegramOrderSender sender;

    TelegramOrderWorker(TelegramOrderQueue queue, TelegramOrderSender sender) {
        this.queue = queue;
        this.sender = sender;
    }

    @Scheduled(fixedDelayString = "${app.order-telegram.poll-interval-ms:5000}", initialDelayString = "${app.order-telegram.poll-interval-ms:5000}")
    void sendDueOrders() {
        if (sender.configured()) {
            queue.due().forEach(queue::deliver);
        }
    }
}
