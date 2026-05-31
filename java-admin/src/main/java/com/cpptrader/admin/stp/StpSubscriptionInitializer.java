package com.cpptrader.admin.stp;

import com.cpptrader.admin.protocol.client.ProtocolClientService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class StpSubscriptionInitializer {

    private final ProtocolClientService protocolClient;
    private final StpOrderEventListener stpOrderEventListener;

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        protocolClient.getStreamSubscriber().addOrdersCallback(event -> {
            stpOrderEventListener.onOrderUpdate(event);
        });
        log.info("STP: Order event listener registered");
    }
}
