package com.cpptrader.admin.stp;

import com.cpptrader.admin.protocol.ProtocolConstants;
import com.cpptrader.admin.protocol.client.ProtocolClientService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SelfTradePreventionServiceTest {

    @Mock
    private SelfTradePreventionConfigRepository stpConfigRepository;

    @Mock
    private SelfTradeAlertRepository stpAlertRepository;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ProtocolClientService protocolClient;

    @Mock
    private ZSetOperations<String, String> zSetOperations;

    private SelfTradePreventionService stpService;

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
        stpService = new SelfTradePreventionService(
                stpConfigRepository, stpAlertRepository, redisTemplate, protocolClient);
    }

    @Test
    @DisplayName("STP check passes when no config exists for symbol")
    void check_noConfig_passes() {
        when(stpConfigRepository.findBySymbolId(anyInt())).thenReturn(Optional.empty());

        var result = stpService.check(1L, 100L, 1, ProtocolConstants.OrderSide.BUY, 1000L, 10L);

        assertTrue(result.passed);
        assertNull(result.rejectReason);
    }

    @Test
    @DisplayName("STP check passes when config is disabled")
    void check_disabledConfig_passes() {
        SelfTradePreventionConfig config = new SelfTradePreventionConfig();
        config.setEnabled(0);
        config.setPolicy(SelfTradePreventionPolicy.REJECT_NEW);
        when(stpConfigRepository.findBySymbolId(1)).thenReturn(Optional.of(config));

        var result = stpService.check(1L, 100L, 1, ProtocolConstants.OrderSide.BUY, 1000L, 10L);

        assertTrue(result.passed);
    }

    @Test
    @DisplayName("STP check passes when no opposing orders exist")
    void check_noOpposingOrders_passes() {
        SelfTradePreventionConfig config = new SelfTradePreventionConfig();
        config.setEnabled(1);
        config.setPolicy(SelfTradePreventionPolicy.REJECT_NEW);
        when(stpConfigRepository.findBySymbolId(1)).thenReturn(Optional.of(config));
        when(zSetOperations.rangeWithScores(anyString(), anyLong(), anyLong()))
                .thenReturn(Collections.emptySet());

        var result = stpService.check(1L, 100L, 1, ProtocolConstants.OrderSide.BUY, 1000L, 10L);

        assertTrue(result.passed);
    }

    @Test
    @DisplayName("STP check detects self-trade when buy price >= sell resting price")
    void check_buyOverlapsSell_detected() {
        SelfTradePreventionConfig config = new SelfTradePreventionConfig();
        config.setEnabled(1);
        config.setPolicy(SelfTradePreventionPolicy.REJECT_NEW);
        when(stpConfigRepository.findBySymbolId(1)).thenReturn(Optional.of(config));

        Set<ZSetOperations.TypedTuple<String>> sellOrders = new HashSet<>();
        sellOrders.add(createTypedTuple("200:10", 950.0));
        when(zSetOperations.rangeWithScores(anyString(), anyLong(), anyLong()))
                .thenReturn(sellOrders);

        var result = stpService.check(1L, 100L, 1, ProtocolConstants.OrderSide.BUY, 1000L, 10L);

        assertFalse(result.passed);
        assertEquals("SELF_TRADE_PREVENTED", result.rejectReason);
        assertEquals(SelfTradePreventionPolicy.REJECT_NEW, result.policy);
        assertEquals(1, result.overlapCount);
    }

    @Test
    @DisplayName("STP check passes when buy price < sell resting price (no overlap)")
    void check_buyBelowSell_noOverlap() {
        SelfTradePreventionConfig config = new SelfTradePreventionConfig();
        config.setEnabled(1);
        config.setPolicy(SelfTradePreventionPolicy.REJECT_NEW);
        when(stpConfigRepository.findBySymbolId(1)).thenReturn(Optional.of(config));

        Set<ZSetOperations.TypedTuple<String>> sellOrders = new HashSet<>();
        sellOrders.add(createTypedTuple("200:10", 1050.0));
        when(zSetOperations.rangeWithScores(anyString(), anyLong(), anyLong()))
                .thenReturn(sellOrders);

        var result = stpService.check(1L, 100L, 1, ProtocolConstants.OrderSide.BUY, 1000L, 10L);

        assertTrue(result.passed);
    }

    @Test
    @DisplayName("STP check detects self-trade when sell price <= buy resting price")
    void check_sellOverlapsBuy_detected() {
        SelfTradePreventionConfig config = new SelfTradePreventionConfig();
        config.setEnabled(1);
        config.setPolicy(SelfTradePreventionPolicy.REJECT_NEW);
        when(stpConfigRepository.findBySymbolId(1)).thenReturn(Optional.of(config));

        Set<ZSetOperations.TypedTuple<String>> buyOrders = new HashSet<>();
        buyOrders.add(createTypedTuple("200:10", 1050.0));
        when(zSetOperations.rangeWithScores(anyString(), anyLong(), anyLong()))
                .thenReturn(buyOrders);

        var result = stpService.check(1L, 100L, 1, ProtocolConstants.OrderSide.SELL, 1000L, 10L);

        assertFalse(result.passed);
        assertEquals("SELF_TRADE_PREVENTED", result.rejectReason);
    }

    @Test
    @DisplayName("REJECT_NEW policy rejects incoming order")
    void check_rejectNewPolicy_rejectsIncoming() {
        SelfTradePreventionConfig config = new SelfTradePreventionConfig();
        config.setEnabled(1);
        config.setPolicy(SelfTradePreventionPolicy.REJECT_NEW);
        when(stpConfigRepository.findBySymbolId(1)).thenReturn(Optional.of(config));

        Set<ZSetOperations.TypedTuple<String>> sellOrders = new HashSet<>();
        sellOrders.add(createTypedTuple("200:10", 950.0));
        when(zSetOperations.rangeWithScores(anyString(), anyLong(), anyLong()))
                .thenReturn(sellOrders);

        var result = stpService.check(1L, 100L, 1, ProtocolConstants.OrderSide.BUY, 1000L, 10L);

        assertFalse(result.passed);
        assertTrue(result.shouldRejectIncoming());
        assertFalse(result.shouldAllowIncoming());
        assertFalse(result.shouldDecrement());
    }

    @Test
    @DisplayName("CANCEL_OLDEST policy allows incoming, cancels resting")
    void check_cancelOldestPolicy_allowsIncoming() {
        SelfTradePreventionConfig config = new SelfTradePreventionConfig();
        config.setEnabled(1);
        config.setPolicy(SelfTradePreventionPolicy.CANCEL_OLDEST);
        when(stpConfigRepository.findBySymbolId(1)).thenReturn(Optional.of(config));

        Set<ZSetOperations.TypedTuple<String>> sellOrders = new HashSet<>();
        sellOrders.add(createTypedTuple("200:10", 950.0));
        when(zSetOperations.rangeWithScores(anyString(), anyLong(), anyLong()))
                .thenReturn(sellOrders);

        var result = stpService.check(1L, 100L, 1, ProtocolConstants.OrderSide.BUY, 1000L, 10L);

        assertFalse(result.passed);
        assertTrue(result.shouldAllowIncoming());
        assertFalse(result.shouldRejectIncoming());
    }

    @Test
    @DisplayName("DECREMENT policy shouldDecrement returns true")
    void check_decrementPolicy_shouldDecrement() {
        SelfTradePreventionConfig config = new SelfTradePreventionConfig();
        config.setEnabled(1);
        config.setPolicy(SelfTradePreventionPolicy.DECREMENT);
        when(stpConfigRepository.findBySymbolId(1)).thenReturn(Optional.of(config));

        Set<ZSetOperations.TypedTuple<String>> sellOrders = new HashSet<>();
        sellOrders.add(createTypedTuple("200:10", 950.0));
        when(zSetOperations.rangeWithScores(anyString(), anyLong(), anyLong()))
                .thenReturn(sellOrders);

        var result = stpService.check(1L, 100L, 1, ProtocolConstants.OrderSide.BUY, 1000L, 10L);

        assertFalse(result.passed);
        assertTrue(result.shouldDecrement());
        assertFalse(result.shouldRejectIncoming());
    }

    @Test
    @DisplayName("CANCEL_NEWEST policy rejects incoming order")
    void check_cancelNewestPolicy_rejectsIncoming() {
        SelfTradePreventionConfig config = new SelfTradePreventionConfig();
        config.setEnabled(1);
        config.setPolicy(SelfTradePreventionPolicy.CANCEL_NEWEST);
        when(stpConfigRepository.findBySymbolId(1)).thenReturn(Optional.of(config));

        Set<ZSetOperations.TypedTuple<String>> sellOrders = new HashSet<>();
        sellOrders.add(createTypedTuple("200:10", 950.0));
        when(zSetOperations.rangeWithScores(anyString(), anyLong(), anyLong()))
                .thenReturn(sellOrders);

        var result = stpService.check(1L, 100L, 1, ProtocolConstants.OrderSide.BUY, 1000L, 10L);

        assertFalse(result.passed);
        assertTrue(result.shouldRejectIncoming());
    }

    @Test
    @DisplayName("CANCEL_BOTH policy rejects incoming order")
    void check_cancelBothPolicy_rejectsIncoming() {
        SelfTradePreventionConfig config = new SelfTradePreventionConfig();
        config.setEnabled(1);
        config.setPolicy(SelfTradePreventionPolicy.CANCEL_BOTH);
        when(stpConfigRepository.findBySymbolId(1)).thenReturn(Optional.of(config));

        Set<ZSetOperations.TypedTuple<String>> sellOrders = new HashSet<>();
        sellOrders.add(createTypedTuple("200:10", 950.0));
        when(zSetOperations.rangeWithScores(anyString(), anyLong(), anyLong()))
                .thenReturn(sellOrders);

        var result = stpService.check(1L, 100L, 1, ProtocolConstants.OrderSide.BUY, 1000L, 10L);

        assertFalse(result.passed);
        assertTrue(result.shouldRejectIncoming());
    }

    @Test
    @DisplayName("STP alert is recorded when self-trade is prevented")
    void check_alertRecorded() {
        SelfTradePreventionConfig config = new SelfTradePreventionConfig();
        config.setEnabled(1);
        config.setPolicy(SelfTradePreventionPolicy.REJECT_NEW);
        when(stpConfigRepository.findBySymbolId(1)).thenReturn(Optional.of(config));

        Set<ZSetOperations.TypedTuple<String>> sellOrders = new HashSet<>();
        sellOrders.add(createTypedTuple("200:10", 950.0));
        when(zSetOperations.rangeWithScores(anyString(), anyLong(), anyLong()))
                .thenReturn(sellOrders);
        when(stpAlertRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        stpService.check(1L, 100L, 1, ProtocolConstants.OrderSide.BUY, 1000L, 10L);

        verify(stpAlertRepository).save(argThat(alert ->
                alert.getUserId().equals(1L)
                        && alert.getIncomingOrderId().equals(100L)
                        && alert.getRestingOrderId().equals(200L)
                        && alert.getPolicyApplied().equals(SelfTradePreventionPolicy.REJECT_NEW)
        ));
    }

    @Test
    @DisplayName("SelfTradePreventionPolicy.isValid validates correctly")
    void policy_isValid() {
        assertTrue(SelfTradePreventionPolicy.isValid("REJECT_NEW"));
        assertTrue(SelfTradePreventionPolicy.isValid("CANCEL_OLDEST"));
        assertTrue(SelfTradePreventionPolicy.isValid("CANCEL_NEWEST"));
        assertTrue(SelfTradePreventionPolicy.isValid("CANCEL_BOTH"));
        assertTrue(SelfTradePreventionPolicy.isValid("DECREMENT"));
        assertFalse(SelfTradePreventionPolicy.isValid("INVALID"));
        assertFalse(SelfTradePreventionPolicy.isValid(""));
        assertFalse(SelfTradePreventionPolicy.isValid(null));
    }

    @Test
    @DisplayName("StpCheckResult.pass creates passing result")
    void stpCheckResult_pass() {
        var result = SelfTradePreventionService.StpCheckResult.pass();
        assertTrue(result.passed);
        assertNull(result.rejectReason);
        assertNull(result.policy);
        assertNull(result.actionTaken);
        assertEquals(0, result.overlapCount);
    }

    private ZSetOperations.TypedTuple<String> createTypedTuple(String value, double score) {
        return new ZSetOperations.TypedTuple<>() {
            @Override
            public String getValue() {
                return value;
            }

            @Override
            public Double getScore() {
                return score;
            }

            @Override
            public int compareTo(ZSetOperations.TypedTuple<String> o) {
                return Double.compare(getScore(), o.getScore());
            }
        };
    }
}
