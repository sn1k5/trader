package com.cpptrader.admin.stp;

import com.cpptrader.admin.idempotent.DedupTableService;
import com.cpptrader.admin.protocol.ProtocolConstants;
import com.cpptrader.admin.protocol.events.OrderUpdateEvent;
import com.cpptrader.admin.user.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class StpOrderEventListener {

    private final SelfTradePreventionService stpService;
    private final UserService userService;
    private final DedupTableService dedupTableService;

    public void onOrderUpdate(OrderUpdateEvent event) {
        try {
            String messageId = "stp-order-" + event.getSequence();
            if (!dedupTableService.tryAcquire(messageId, "stp-order-listener")) {
                log.debug("STP: Duplicate order event, skipping: seq={}", event.getSequence());
                return;
            }

            byte action = event.getAction();
            var order = event.getOrder();

            Long userId = resolveUserId();
            if (userId == null) {
                log.debug("STP: Cannot resolve userId, skipping order update event");
                return;
            }

            long orderId = order.id;
            int symbolId = order.symbolId;
            byte side = order.orderSide;
            long price = order.price;
            long leavesQty = order.leavesQuantity;

            switch (action) {
                case ProtocolConstants.ActionType.ADD -> {
                    stpService.addActiveOrder(userId, orderId, symbolId, side, price, leavesQty);
                }
                case ProtocolConstants.ActionType.UPDATE -> {
                    stpService.updateActiveOrder(userId, orderId, symbolId, side, price, leavesQty);
                }
                case ProtocolConstants.ActionType.DELETE -> {
                    stpService.removeActiveOrder(userId, orderId, symbolId, side);
                }
                case ProtocolConstants.ActionType.EXECUTE -> {
                    if (leavesQty <= 0) {
                        stpService.removeActiveOrder(userId, orderId, symbolId, side);
                    } else {
                        stpService.updateActiveOrder(userId, orderId, symbolId, side, price, leavesQty);
                    }
                }
                default -> log.warn("STP: Unknown order action: {}", action);
            }
        } catch (Exception e) {
            log.error("STP: Error processing order update event", e);
        }
    }

    private Long resolveUserId() {
        try {
            return userService.getCurrentUserId();
        } catch (Exception e) {
            return null;
        }
    }
}
