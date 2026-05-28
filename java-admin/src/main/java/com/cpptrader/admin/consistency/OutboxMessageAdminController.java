package com.cpptrader.admin.consistency;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/outbox")
@RequiredArgsConstructor
public class OutboxMessageAdminController {

    private final OutboxMessageService outboxMessageService;

    @GetMapping("/dead")
    public Map<String, Object> listDeadMessages() {
        List<OutboxMessage> deadMessages = outboxMessageService.findDeadMessages();
        Map<String, Object> result = new HashMap<>();
        result.put("count", deadMessages.size());
        result.put("messages", deadMessages);
        return result;
    }

    @PostMapping("/reactivate/{id}")
    public Map<String, Object> reactivateDeadMessage(@PathVariable Long id) {
        outboxMessageService.reactivateDeadMessage(id);
        Map<String, Object> result = new HashMap<>();
        result.put("id", id);
        result.put("status", "reactivated");
        return result;
    }
}
