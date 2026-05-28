package com.cpptrader.admin.user;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AccountService {

    private final TradingAccountRepository tradingAccountRepository;
    private final PositionRepository positionRepository;
    private final UserService userService;

    public List<TradingAccount> getCurrentUserAccounts() {
        Long userId = userService.getCurrentUserId();
        return tradingAccountRepository.findByUserId(userId);
    }

    public List<Position> getCurrentUserPositions() {
        Long userId = userService.getCurrentUserId();
        return positionRepository.findByUserId(userId);
    }

    public Position getPositionBySymbol(Integer symbolId, Integer side) {
        Long userId = userService.getCurrentUserId();
        return positionRepository.findByUserIdAndSymbolIdAndSide(userId, symbolId, side).orElse(null);
    }
}
