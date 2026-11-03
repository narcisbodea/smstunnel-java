package io.smstunnel.spring.boot.demo;

import io.smstunnel.SendOptions;
import io.smstunnel.SendResult;
import io.smstunnel.SmsTunnelClient;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.stereotype.Service;

/** A tiny Spring Boot app that uses the starter the way a shop would: one bean, injected, one text per order. */
@SpringBootApplication
public class DemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(DemoApplication.class, args);
    }

    @Service
    public static class ShippingTexts {

        private final SmsTunnelClient sms;

        public ShippingTexts(SmsTunnelClient sms) {
            this.sms = sms;
        }

        /** One text to the customer; the order id makes a retry of the same notice safe. */
        public SendResult orderShipped(String orderId, String phone) {
            return sms.sendSms(phone, "Your order #" + orderId + " has shipped.",
                    SendOptions.builder().idempotencyKey("order-" + orderId + "-shipped").build());
        }
    }
}
