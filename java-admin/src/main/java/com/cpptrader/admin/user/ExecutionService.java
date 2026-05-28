package com.cpptrader.admin.user;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ExecutionService {

    private final ExecutionRepository executionRepository;

    public Execution save(Execution execution) {
        return executionRepository.save(execution);
    }

    public Page<Execution> findByUserId(Long userId, int page, int size) {
        return executionRepository.findByUserIdOrderByExecutedAtDesc(userId, PageRequest.of(page, size));
    }

    public List<Execution> findByOrderId(Long orderId) {
        return executionRepository.findByOrderIdOrderByExecutedAtDesc(orderId);
    }
}
