package com.cpptrader.marketdata.indicator;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class IndicatorCalculatorTest {

    @Test
    void ma_shouldCalculateCorrectly() {
        List<Double> prices = List.of(10.0, 20.0, 30.0, 40.0, 50.0);
        List<Double> ma3 = IndicatorCalculator.ma(prices, 3);
        assertEquals(3, ma3.size());
        assertEquals(20.0, ma3.get(0), 0.001);
        assertEquals(30.0, ma3.get(1), 0.001);
        assertEquals(40.0, ma3.get(2), 0.001);
    }

    @Test
    void ma_insufficientData_shouldReturnEmpty() {
        List<Double> prices = List.of(10.0, 20.0);
        List<Double> ma5 = IndicatorCalculator.ma(prices, 5);
        assertTrue(ma5.isEmpty());
    }

    @Test
    void ema_shouldCalculateCorrectly() {
        List<Double> prices = List.of(10.0, 20.0, 30.0, 40.0, 50.0);
        List<Double> ema3 = IndicatorCalculator.ema(prices, 3);
        assertEquals(5, ema3.size());
        assertEquals(10.0, ema3.get(0), 0.001);
    }

    @Test
    void rsi_shouldReturnValues() {
        List<Double> prices = List.of(44.0, 44.34, 44.09, 43.61, 44.33, 44.83, 45.10, 45.42, 45.84, 46.08,
                45.89, 46.03, 45.61, 46.28, 46.28, 46.00, 46.03, 46.41, 46.22, 45.64);
        List<Double> rsi14 = IndicatorCalculator.rsi(prices, 14);
        assertFalse(rsi14.isEmpty());
        assertTrue(rsi14.get(0) >= 0 && rsi14.get(0) <= 100);
    }

    @Test
    void macd_shouldReturnThreeComponents() {
        List<Double> prices = new java.util.ArrayList<>();
        for (int i = 0; i < 50; i++) {
            prices.add(100.0 + Math.sin(i * 0.3) * 10);
        }
        IndicatorCalculator.MacdResult result = IndicatorCalculator.macd(prices, 12, 26, 9);
        assertNotNull(result.dif);
        assertNotNull(result.dea);
        assertNotNull(result.macdBar);
    }

    @Test
    void boll_shouldReturnThreeBands() {
        List<Double> prices = new java.util.ArrayList<>();
        for (int i = 0; i < 30; i++) {
            prices.add(100.0 + i);
        }
        IndicatorCalculator.BollResult result = IndicatorCalculator.boll(prices, 20, 2.0);
        assertNotNull(result.upper);
        assertNotNull(result.mid);
        assertNotNull(result.lower);
        assertTrue(result.upper.get(0) > result.mid.get(0));
        assertTrue(result.lower.get(0) < result.mid.get(0));
    }
}
