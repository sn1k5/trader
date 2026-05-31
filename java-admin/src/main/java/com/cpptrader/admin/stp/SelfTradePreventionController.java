package com.cpptrader.admin.stp;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/stp")
@RequiredArgsConstructor
public class SelfTradePreventionController {

    private final SelfTradePreventionService stpService;
    private final SelfTradePreventionConfigRepository stpConfigRepository;
    private final SelfTradeAlertRepository stpAlertRepository;

    @GetMapping("/configs")
    public List<SelfTradePreventionConfig> getConfigs() {
        return stpConfigRepository.findAll();
    }

    @GetMapping("/configs/{symbolId}")
    public Map<String, Object> getConfigBySymbol(@PathVariable Integer symbolId) {
        Map<String, Object> result = new HashMap<>();
        SelfTradePreventionConfig config = stpConfigRepository.findBySymbolId(symbolId).orElse(null);
        if (config == null) {
            result.put("enabled", false);
            result.put("message", "No STP config for symbol " + symbolId);
        } else {
            result.put("id", config.getId());
            result.put("symbolId", config.getSymbolId());
            result.put("policy", config.getPolicy());
            result.put("enabled", config.getEnabled() == 1);
        }
        return result;
    }

    @PostMapping("/configs")
    public Map<String, Object> createConfig(@RequestBody SelfTradePreventionConfig config) {
        Map<String, Object> result = new HashMap<>();
        if (!SelfTradePreventionPolicy.isValid(config.getPolicy())) {
            result.put("error", "INVALID_POLICY");
            result.put("message", "Policy must be one of: REJECT_NEW, CANCEL_OLDEST, CANCEL_NEWEST, CANCEL_BOTH, DECREMENT");
            return result;
        }
        if (config.getSymbolId() != null) {
            stpConfigRepository.findBySymbolId(config.getSymbolId()).ifPresent(existing -> {
                stpConfigRepository.delete(existing);
            });
        }
        SelfTradePreventionConfig saved = stpConfigRepository.save(config);
        result.put("id", saved.getId());
        result.put("symbolId", saved.getSymbolId());
        result.put("policy", saved.getPolicy());
        result.put("enabled", saved.getEnabled() == 1);
        return result;
    }

    @PutMapping("/configs/{id}")
    public Map<String, Object> updateConfig(@PathVariable Long id, @RequestBody SelfTradePreventionConfig updated) {
        Map<String, Object> result = new HashMap<>();
        SelfTradePreventionConfig config = stpConfigRepository.findById(id).orElse(null);
        if (config == null) {
            result.put("error", "NOT_FOUND");
            return result;
        }
        if (updated.getPolicy() != null) {
            if (!SelfTradePreventionPolicy.isValid(updated.getPolicy())) {
                result.put("error", "INVALID_POLICY");
                return result;
            }
            config.setPolicy(updated.getPolicy());
        }
        if (updated.getEnabled() != null) {
            config.setEnabled(updated.getEnabled());
        }
        SelfTradePreventionConfig saved = stpConfigRepository.save(config);
        result.put("id", saved.getId());
        result.put("symbolId", saved.getSymbolId());
        result.put("policy", saved.getPolicy());
        result.put("enabled", saved.getEnabled() == 1);
        return result;
    }

    @DeleteMapping("/configs/{id}")
    public Map<String, Object> deleteConfig(@PathVariable Long id) {
        Map<String, Object> result = new HashMap<>();
        if (stpConfigRepository.existsById(id)) {
            stpConfigRepository.deleteById(id);
            result.put("deleted", true);
        } else {
            result.put("error", "NOT_FOUND");
        }
        return result;
    }

    @GetMapping("/alerts")
    public Page<SelfTradeAlert> getAlerts(
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) Integer symbolId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        if (userId != null) {
            return stpAlertRepository.findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(page, size));
        }
        if (symbolId != null) {
            return stpAlertRepository.findBySymbolIdOrderByCreatedAtDesc(symbolId, PageRequest.of(page, size));
        }
        return stpAlertRepository.findAllByOrderByCreatedAtDesc(PageRequest.of(page, size));
    }

    @GetMapping("/policies")
    public Map<String, Object> getPolicies() {
        Map<String, Object> result = new HashMap<>();
        result.put("policies", List.of(
                Map.of("name", "REJECT_NEW", "description", "Reject the incoming order that would cause self-trade"),
                Map.of("name", "CANCEL_OLDEST", "description", "Cancel the older resting order, allow the new order"),
                Map.of("name", "CANCEL_NEWEST", "description", "Cancel the newer incoming order, keep the resting order"),
                Map.of("name", "CANCEL_BOTH", "description", "Cancel both the resting and incoming orders"),
                Map.of("name", "DECREMENT", "description", "Decrement quantities to avoid self-trade overlap")
        ));
        return result;
    }
}
