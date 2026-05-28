package com.cpptrader.marketdata.indicator;

import java.util.ArrayList;
import java.util.List;

public class IndicatorCalculator {

    public static List<Double> ma(List<Double> prices, int period) {
        List<Double> result = new ArrayList<>();
        if (prices.size() < period) return result;
        double sum = 0;
        for (int i = 0; i < period; i++) sum += prices.get(i);
        result.add(sum / period);
        for (int i = period; i < prices.size(); i++) {
            sum += prices.get(i) - prices.get(i - period);
            result.add(sum / period);
        }
        return result;
    }

    public static List<Double> ema(List<Double> prices, int period) {
        List<Double> result = new ArrayList<>();
        if (prices.isEmpty()) return result;
        double multiplier = 2.0 / (period + 1);
        result.add(prices.get(0));
        for (int i = 1; i < prices.size(); i++) {
            double value = prices.get(i) * multiplier + result.get(i - 1) * (1 - multiplier);
            result.add(value);
        }
        return result;
    }

    public static MacdResult macd(List<Double> prices, int fastPeriod, int slowPeriod, int signalPeriod) {
        List<Double> emaFast = ema(prices, fastPeriod);
        List<Double> emaSlow = ema(prices, slowPeriod);
        List<Double> dif = new ArrayList<>();
        int offset = slowPeriod - fastPeriod;
        for (int i = 0; i < emaSlow.size(); i++) {
            dif.add(emaFast.get(i + offset) - emaSlow.get(i));
        }
        List<Double> dea = ema(dif, signalPeriod);
        List<Double> macdBar = new ArrayList<>();
        int deaOffset = dif.size() - dea.size();
        for (int i = 0; i < dea.size(); i++) {
            macdBar.add(2 * (dif.get(i + deaOffset) - dea.get(i)));
        }
        return new MacdResult(dif, dea, macdBar);
    }

    public static List<Double> rsi(List<Double> prices, int period) {
        List<Double> result = new ArrayList<>();
        if (prices.size() < period + 1) return result;
        double avgGain = 0, avgLoss = 0;
        for (int i = 1; i <= period; i++) {
            double change = prices.get(i) - prices.get(i - 1);
            if (change > 0) avgGain += change;
            else avgLoss += Math.abs(change);
        }
        avgGain /= period;
        avgLoss /= period;
        result.add(avgLoss == 0 ? 100.0 : 100 - 100 / (1 + avgGain / avgLoss));
        for (int i = period + 1; i < prices.size(); i++) {
            double change = prices.get(i) - prices.get(i - 1);
            double gain = change > 0 ? change : 0;
            double loss = change < 0 ? Math.abs(change) : 0;
            avgGain = (avgGain * (period - 1) + gain) / period;
            avgLoss = (avgLoss * (period - 1) + loss) / period;
            result.add(avgLoss == 0 ? 100.0 : 100 - 100 / (1 + avgGain / avgLoss));
        }
        return result;
    }

    public static BollResult boll(List<Double> prices, int period, double multiplier) {
        List<Double> mid = ma(prices, period);
        List<Double> upper = new ArrayList<>();
        List<Double> lower = new ArrayList<>();
        for (int i = 0; i < mid.size(); i++) {
            int startIdx = i;
            double sumSq = 0;
            for (int j = startIdx; j < startIdx + period && j < prices.size(); j++) {
                sumSq += Math.pow(prices.get(j) - mid.get(i), 2);
            }
            double std = Math.sqrt(sumSq / period);
            upper.add(mid.get(i) + multiplier * std);
            lower.add(mid.get(i) - multiplier * std);
        }
        return new BollResult(upper, mid, lower);
    }

    public static class MacdResult {
        public final List<Double> dif;
        public final List<Double> dea;
        public final List<Double> macdBar;
        public MacdResult(List<Double> dif, List<Double> dea, List<Double> macdBar) {
            this.dif = dif; this.dea = dea; this.macdBar = macdBar;
        }
    }

    public static class BollResult {
        public final List<Double> upper;
        public final List<Double> mid;
        public final List<Double> lower;
        public BollResult(List<Double> upper, List<Double> mid, List<Double> lower) {
            this.upper = upper; this.mid = mid; this.lower = lower;
        }
    }
}
