package com.cpptrader.marketdata.kline;

import lombok.Getter;

@Getter
public enum KlinePeriod {
    M1("1m", 60),
    M5("5m", 300),
    M15("15m", 900),
    H1("1h", 3600),
    D1("1d", 86400);

    private final String code;
    private final long seconds;

    KlinePeriod(String code, long seconds) {
        this.code = code;
        this.seconds = seconds;
    }

    public long calculateOpenTime(long timestampSeconds) {
        return (timestampSeconds / seconds) * seconds;
    }

    public long calculateCloseTime(long openTimeSeconds) {
        return openTimeSeconds + seconds - 1;
    }

    public static KlinePeriod fromCode(String code) {
        for (KlinePeriod p : values()) {
            if (p.code.equals(code)) return p;
        }
        return M1;
    }
}
