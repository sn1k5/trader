package com.cpptrader.admin.stp;

import com.cpptrader.admin.protocol.ProtocolConstants;
import com.cpptrader.admin.protocol.client.ProtocolClientService;
import com.cpptrader.admin.protocol.requests.DeleteOrderRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class SelfTradePreventionService {

    private final SelfTradePreventionConfigRepository stpConfigRepository;
    private final SelfTradeAlertRepository stpAlertRepository;
    private final StringRedisTemplate redisTemplate;
    private final ProtocolClientService protocolClient;

    private static final String ACTIVE_ORDERS_KEY_PREFIX = "stp:active:";
    private static final String ORDER_META_KEY_PREFIX = "stp:meta:";

    private static final String CHECK_AND_APPLY_SCRIPT =
        "local userId = ARGV[1]\n" +
        "local orderId = ARGV[2]\n" +
        "local symbolId = ARGV[3]\n" +
        "local orderSide = tonumber(ARGV[4])\n" +
        "local price = tonumber(ARGV[5])\n" +
        "local quantity = tonumber(ARGV[6])\n" +
        "local policy = ARGV[7]\n" +
        "local oppositeSide = orderSide == 0 and 1 or 0\n" +
        "local sideStr = oppositeSide == 0 and 'BUY' or 'SELL'\n" +
        "local oppositeKey = 'stp:active:' .. userId .. ':' .. symbolId .. ':' .. sideStr\n" +
        "local oppositeOrders = redis.call('ZRANGE', oppositeKey, 0, -1, 'WITHSCORES')\n" +
        "local overlaps = {}\n" +
        "for i = 1, #oppositeOrders, 2 do\n" +
        "    local member = oppositeOrders[i]\n" +
        "    local restingPrice = tonumber(oppositeOrders[i+1])\n" +
        "    local wouldMatch = (orderSide == 0 and price >= restingPrice) or (orderSide == 1 and price <= restingPrice)\n" +
        "    if wouldMatch then\n" +
        "        table.insert(overlaps, member)\n" +
        "        table.insert(overlaps, tostring(restingPrice))\n" +
        "    end\n" +
        "end\n" +
        "if #overlaps == 0 then\n" +
        "    return 'PASS'\n" +
        "end\n" +
        "if policy == 'REJECT_NEW' then\n" +
        "    return 'INCOMING_REJECTED'\n" +
        "elseif policy == 'CANCEL_OLDEST' then\n" +
        "    for i = 1, #overlaps, 2 do\n" +
        "        redis.call('ZREM', oppositeKey, overlaps[i])\n" +
        "    end\n" +
        "    return 'RESTING_CANCELLED'\n" +
        "elseif policy == 'CANCEL_NEWEST' then\n" +
        "    return 'INCOMING_CANCELLED'\n" +
        "elseif policy == 'CANCEL_BOTH' then\n" +
        "    for i = 1, #overlaps, 2 do\n" +
        "        redis.call('ZREM', oppositeKey, overlaps[i])\n" +
        "    end\n" +
        "    return 'BOTH_CANCELLED'\n" +
        "elseif policy == 'DECREMENT' then\n" +
        "    return 'QUANTITY_DECREMENTED'\n" +
        "else\n" +
        "    return 'INCOMING_REJECTED'\n" +
        "end\n";

    private static final String ADD_ACTIVE_ORDER_SCRIPT =
        "local key = KEYS[1]\n" +
        "local metaKey = KEYS[2]\n" +
        "local member = ARGV[1]\n" +
        "local price = tonumber(ARGV[2])\n" +
        "local userId = ARGV[3]\n" +
        "local symbolId = ARGV[4]\n" +
        "local side = ARGV[5]\n" +
        "redis.call('ZADD', key, price, member)\n" +
        "redis.call('HSET', metaKey, 'userId', userId, 'symbolId', symbolId, 'side', side, 'price', tostring(price))\n" +
        "return 1\n";

    private static final String UPDATE_ACTIVE_ORDER_SCRIPT =
        "local key = KEYS[1]\n" +
        "local oldPrice = tonumber(ARGV[1])\n" +
        "local orderId = ARGV[2]\n" +
        "local newMember = ARGV[3]\n" +
        "local newPrice = tonumber(ARGV[4])\n" +
        "local existing = redis.call('ZRANGE', key, oldPrice, oldPrice)\n" +
        "for _, member in ipairs(existing) do\n" +
        "    if string.sub(member, 1, string.len(orderId .. ':')) == orderId .. ':' then\n" +
        "        redis.call('ZREM', key, member)\n" +
        "        break\n" +
        "    end\n" +
        "end\n" +
        "redis.call('ZADD', key, newPrice, newMember)\n" +
        "return 1\n";

    public StpCheckResult check(Long userId, Long orderId, Integer symbolId,
                                 byte orderSide, long price, long quantity) {
        SelfTradePreventionConfig config = getStpConfig(symbolId);
        if (config == null || config.getEnabled() != 1) {
            return StpCheckResult.pass();
        }

        String policy = config.getPolicy();
        byte oppositeSide = (orderSide == ProtocolConstants.OrderSide.BUY)
                ? ProtocolConstants.OrderSide.SELL
                : ProtocolConstants.OrderSide.BUY;

        String oppositeKey = buildActiveOrdersKey(userId, symbolId, oppositeSide);

        Set<ZSetOperations.TypedTuple<String>> oppositeOrders =
                redisTemplate.opsForZSet().rangeWithScores(oppositeKey, 0, -1);

        if (oppositeOrders == null || oppositeOrders.isEmpty()) {
            return StpCheckResult.pass();
        }

        List<OverlapInfo> overlaps = new ArrayList<>();

        for (ZSetOperations.TypedTuple<String> entry : oppositeOrders) {
            String member = entry.getValue();
            Double score = entry.getScore();
            if (member == null || score == null) continue;

            long restingPrice = score.longValue();
            String[] parts = member.split(":");
            long restingOrderId = Long.parseLong(parts[0]);
            long restingLeavesQty = Long.parseLong(parts[1]);

            boolean wouldMatch = (orderSide == ProtocolConstants.OrderSide.BUY && price >= restingPrice)
                    || (orderSide == ProtocolConstants.OrderSide.SELL && price <= restingPrice);

            if (wouldMatch) {
                long overlapQty = Math.min(quantity, restingLeavesQty);
                overlaps.add(new OverlapInfo(restingOrderId, restingPrice, restingLeavesQty, overlapQty));
            }
        }

        if (overlaps.isEmpty()) {
            return StpCheckResult.pass();
        }

        OverlapInfo primaryOverlap = overlaps.get(0);
        String actionTaken = applyPolicy(policy, userId, orderId, symbolId, orderSide, price, quantity, overlaps);

        recordAlert(userId, symbolId, orderId, orderSide, price, quantity,
                primaryOverlap.restingOrderId, oppositeSide, primaryOverlap.restingPrice,
                primaryOverlap.restingLeavesQty, primaryOverlap.overlapQuantity, policy, actionTaken);

        return new StpCheckResult(false, "SELF_TRADE_PREVENTED",
                policy, actionTaken, overlaps.size());
    }

    public StpCheckResult checkAtomic(Long userId, Long orderId, Integer symbolId,
                                       byte orderSide, long price, long quantity) {
        SelfTradePreventionConfig config = getStpConfig(symbolId);
        if (config == null || config.getEnabled() != 1) {
            return StpCheckResult.pass();
        }

        DefaultRedisScript<String> script = new DefaultRedisScript<>(CHECK_AND_APPLY_SCRIPT, String.class);
        String result = redisTemplate.execute(script,
                java.util.Collections.emptyList(),
                userId.toString(), orderId.toString(), symbolId.toString(),
                String.valueOf(orderSide), String.valueOf(price), String.valueOf(quantity),
                config.getPolicy());

        if ("PASS".equals(result)) {
            return StpCheckResult.pass();
        }
        return new StpCheckResult(false, "SELF_TRADE_PREVENTED",
                config.getPolicy(), result, 1);
    }

    private String applyPolicy(String policy, Long userId, Long incomingOrderId,
                                Integer symbolId, byte incomingSide, long incomingPrice, long incomingQuantity,
                                List<OverlapInfo> overlaps) {
        byte oppositeSide = (incomingSide == ProtocolConstants.OrderSide.BUY)
                ? ProtocolConstants.OrderSide.SELL
                : ProtocolConstants.OrderSide.BUY;
        return switch (policy) {
            case SelfTradePreventionPolicy.REJECT_NEW -> {
                log.info("STP REJECT_NEW: userId={}, incomingOrderId={}, overlaps={}",
                        userId, incomingOrderId, overlaps.size());
                yield "INCOMING_REJECTED";
            }
            case SelfTradePreventionPolicy.CANCEL_OLDEST -> {
                for (OverlapInfo overlap : overlaps) {
                    cancelRestingOrder(overlap.restingOrderId);
                    removeActiveOrder(userId, overlap.restingOrderId, symbolId, oppositeSide);
                }
                log.info("STP CANCEL_OLDEST: userId={}, cancelledCount={}, incomingOrderId={}",
                        userId, overlaps.size(), incomingOrderId);
                yield "RESTING_CANCELLED";
            }
            case SelfTradePreventionPolicy.CANCEL_NEWEST -> {
                log.info("STP CANCEL_NEWEST: userId={}, incomingOrderId={}",
                        userId, incomingOrderId);
                yield "INCOMING_CANCELLED";
            }
            case SelfTradePreventionPolicy.CANCEL_BOTH -> {
                for (OverlapInfo overlap : overlaps) {
                    cancelRestingOrder(overlap.restingOrderId);
                    removeActiveOrder(userId, overlap.restingOrderId, symbolId, oppositeSide);
                }
                log.info("STP CANCEL_BOTH: userId={}, cancelledCount={}, incomingOrderId={}",
                        userId, overlaps.size(), incomingOrderId);
                yield "BOTH_CANCELLED";
            }
            case SelfTradePreventionPolicy.DECREMENT -> {
                long totalOverlap = overlaps.stream()
                        .mapToLong(o -> o.overlapQuantity)
                        .sum();
                log.info("STP DECREMENT: userId={}, incomingOrderId={}, totalOverlap={}, originalQty={}",
                        userId, incomingOrderId, totalOverlap, incomingQuantity);
                yield "QUANTITY_DECREMENTED";
            }
            default -> {
                log.warn("STP unknown policy: {}, defaulting to REJECT_NEW", policy);
                yield "INCOMING_REJECTED";
            }
        };
    }

    public void addActiveOrder(Long userId, Long orderId, Integer symbolId,
                                byte orderSide, long price, long leavesQuantity) {
        String key = buildActiveOrdersKey(userId, symbolId, orderSide);
        String metaKey = buildOrderMetaKey(orderId);
        String member = orderId + ":" + leavesQuantity;

        DefaultRedisScript<Long> script = new DefaultRedisScript<>(ADD_ACTIVE_ORDER_SCRIPT, Long.class);
        redisTemplate.execute(script,
                java.util.Arrays.asList(key, metaKey),
                member, String.valueOf(price), userId.toString(), symbolId.toString(), String.valueOf(orderSide));
    }

    public void updateActiveOrder(Long userId, Long orderId, Integer symbolId,
                                   byte orderSide, long price, long newLeavesQuantity) {
        if (newLeavesQuantity <= 0) {
            removeActiveOrder(userId, orderId, symbolId, orderSide);
            return;
        }

        String key = buildActiveOrdersKey(userId, symbolId, orderSide);
        String newMember = orderId + ":" + newLeavesQuantity;

        DefaultRedisScript<Long> script = new DefaultRedisScript<>(UPDATE_ACTIVE_ORDER_SCRIPT, Long.class);
        redisTemplate.execute(script,
                java.util.Collections.singletonList(key),
                String.valueOf(price), orderId.toString(), newMember, String.valueOf(price));
    }

    public void removeActiveOrder(Long userId, Long orderId, Integer symbolId, byte orderSide) {
        String key = buildActiveOrdersKey(userId, symbolId, orderSide);

        Set<ZSetOperations.TypedTuple<String>> all =
                redisTemplate.opsForZSet().rangeWithScores(key, 0, -1);
        if (all != null) {
            for (ZSetOperations.TypedTuple<String> entry : all) {
                String member = entry.getValue();
                if (member != null && member.startsWith(orderId + ":")) {
                    redisTemplate.opsForZSet().remove(key, member);
                    break;
                }
            }
        }

        String metaKey = buildOrderMetaKey(orderId);
        redisTemplate.delete(metaKey);

        log.debug("STP: Removed active order userId={} orderId={}", userId, orderId);
    }

    public void removeActiveOrderById(Long orderId) {
        String metaKey = buildOrderMetaKey(orderId);
        Object userIdObj = redisTemplate.opsForHash().get(metaKey, "userId");
        Object symbolIdObj = redisTemplate.opsForHash().get(metaKey, "symbolId");
        Object sideObj = redisTemplate.opsForHash().get(metaKey, "side");

        if (userIdObj != null && symbolIdObj != null && sideObj != null) {
            Long userId = Long.parseLong(userIdObj.toString());
            Integer symbolId = Integer.parseInt(symbolIdObj.toString());
            byte side = Byte.parseByte(sideObj.toString());
            removeActiveOrder(userId, orderId, symbolId, side);
        }
    }

    public long computeDecrementQuantity(Long userId, Integer symbolId,
                                          byte incomingSide, long incomingPrice, long incomingQuantity) {
        byte oppositeSide = (incomingSide == ProtocolConstants.OrderSide.BUY)
                ? ProtocolConstants.OrderSide.SELL
                : ProtocolConstants.OrderSide.BUY;
        String oppositeKey = buildActiveOrdersKey(userId, symbolId, oppositeSide);

        Set<ZSetOperations.TypedTuple<String>> oppositeOrders =
                redisTemplate.opsForZSet().rangeWithScores(oppositeKey, 0, -1);

        if (oppositeOrders == null || oppositeOrders.isEmpty()) {
            return 0;
        }

        long totalOverlap = 0;
        for (ZSetOperations.TypedTuple<String> entry : oppositeOrders) {
            String member = entry.getValue();
            Double score = entry.getScore();
            if (member == null || score == null) continue;

            long restingPrice = score.longValue();
            String[] parts = member.split(":");
            long restingLeavesQty = Long.parseLong(parts[1]);

            boolean wouldMatch = (incomingSide == ProtocolConstants.OrderSide.BUY && incomingPrice >= restingPrice)
                    || (incomingSide == ProtocolConstants.OrderSide.SELL && incomingPrice <= restingPrice);

            if (wouldMatch) {
                totalOverlap += Math.min(incomingQuantity - totalOverlap, restingLeavesQty);
                if (totalOverlap >= incomingQuantity) break;
            }
        }

        return totalOverlap;
    }

    private SelfTradePreventionConfig getStpConfig(Integer symbolId) {
        return stpConfigRepository.findBySymbolId(symbolId)
                .orElse(null);
    }

    private void cancelRestingOrder(long orderId) {
        try {
            DeleteOrderRequest req = new DeleteOrderRequest(orderId);
            protocolClient.sendAsync(req.toBytes());
            log.info("STP: Sent cancel request for resting order {}", orderId);
        } catch (Exception e) {
            log.error("STP: Failed to cancel resting order {}", orderId, e);
        }
    }

    private void recordAlert(Long userId, Integer symbolId,
                              Long incomingOrderId, byte incomingSide,
                              long incomingPrice, long incomingQuantity,
                              Long restingOrderId, byte restingSide,
                              long restingPrice, long restingQuantity,
                              long overlapQuantity, String policy, String actionTaken) {
        SelfTradeAlert alert = new SelfTradeAlert();
        alert.setUserId(userId);
        alert.setSymbolId(symbolId);
        alert.setIncomingOrderId(incomingOrderId);
        alert.setIncomingSide((int) incomingSide);
        alert.setIncomingPrice(incomingPrice);
        alert.setIncomingQuantity(incomingQuantity);
        alert.setRestingOrderId(restingOrderId);
        alert.setRestingSide((int) restingSide);
        alert.setRestingPrice(restingPrice);
        alert.setRestingQuantity(restingQuantity);
        alert.setOverlapQuantity(overlapQuantity);
        alert.setPolicyApplied(policy);
        alert.setActionTaken(actionTaken);
        stpAlertRepository.save(alert);

        log.warn("STP Alert: userId={}, symbolId={}, incoming={}, resting={}, policy={}, action={}",
                userId, symbolId, incomingOrderId, restingOrderId, policy, actionTaken);
    }

    private Integer resolveSymbolIdFromOverlap(Long userId, Long orderId) {
        try {
            String metaKey = buildOrderMetaKey(orderId);
            var hashOps = redisTemplate.opsForHash();
            if (hashOps == null) return null;
            Object symbolIdObj = hashOps.get(metaKey, "symbolId");
            return symbolIdObj != null ? Integer.parseInt(symbolIdObj.toString()) : null;
        } catch (Exception e) {
            log.warn("STP: Failed to resolve symbolId for order {}", orderId, e);
            return null;
        }
    }

    private String buildActiveOrdersKey(Long userId, Integer symbolId, byte side) {
        String sideStr = (side == ProtocolConstants.OrderSide.BUY) ? "BUY" : "SELL";
        return ACTIVE_ORDERS_KEY_PREFIX + userId + ":" + symbolId + ":" + sideStr;
    }

    private String buildOrderMetaKey(Long orderId) {
        return ORDER_META_KEY_PREFIX + orderId;
    }

    public static class StpCheckResult {
        public final boolean passed;
        public final String rejectReason;
        public final String policy;
        public final String actionTaken;
        public final int overlapCount;

        private StpCheckResult(boolean passed, String rejectReason,
                               String policy, String actionTaken, int overlapCount) {
            this.passed = passed;
            this.rejectReason = rejectReason;
            this.policy = policy;
            this.actionTaken = actionTaken;
            this.overlapCount = overlapCount;
        }

        public static StpCheckResult pass() {
            return new StpCheckResult(true, null, null, null, 0);
        }

        public boolean shouldRejectIncoming() {
            return !passed
                    && (SelfTradePreventionPolicy.REJECT_NEW.equals(policy)
                    || SelfTradePreventionPolicy.CANCEL_NEWEST.equals(policy)
                    || SelfTradePreventionPolicy.CANCEL_BOTH.equals(policy));
        }

        public boolean shouldAllowIncoming() {
            return passed
                    || SelfTradePreventionPolicy.CANCEL_OLDEST.equals(policy);
        }

        public boolean shouldDecrement() {
            return !passed && SelfTradePreventionPolicy.DECREMENT.equals(policy);
        }
    }

    private static class OverlapInfo {
        final long restingOrderId;
        final long restingPrice;
        final long restingLeavesQty;
        final long overlapQuantity;

        OverlapInfo(long restingOrderId, long restingPrice,
                    long restingLeavesQty, long overlapQuantity) {
            this.restingOrderId = restingOrderId;
            this.restingPrice = restingPrice;
            this.restingLeavesQty = restingLeavesQty;
            this.overlapQuantity = overlapQuantity;
        }
    }
}
