package com.archdox.cloud.checklist.infra;

import com.archdox.cloud.checklist.domain.ChecklistItem;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChecklistItemRepository extends JpaRepository<ChecklistItem, Long> {
    List<ChecklistItem> findByChecklistSchemaIdOrderByDisplayOrderAscIdAsc(Long checklistSchemaId);

    Optional<ChecklistItem> findByChecklistSchemaIdAndItemCode(Long checklistSchemaId, String itemCode);
}
