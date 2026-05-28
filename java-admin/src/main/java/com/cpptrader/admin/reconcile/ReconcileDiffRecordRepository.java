package com.cpptrader.admin.reconcile;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ReconcileDiffRecordRepository extends JpaRepository<ReconcileDiffRecord, Long> {
    List<ReconcileDiffRecord> findByFixedFalse();
}
