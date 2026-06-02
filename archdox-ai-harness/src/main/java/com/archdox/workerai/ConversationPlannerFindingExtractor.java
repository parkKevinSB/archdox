package com.archdox.workerai;

import io.github.parkkevinsb.flower.ai.harness.finding.AiFinding;
import io.github.parkkevinsb.flower.ai.harness.finding.FindingExtractor;
import io.github.parkkevinsb.flower.ai.harness.run.AiHarnessRunContext;
import java.util.List;

public final class ConversationPlannerFindingExtractor implements FindingExtractor<ConversationPlannerResult> {
    @Override
    public List<AiFinding> extract(ConversationPlannerResult value, AiHarnessRunContext ctx) {
        return List.of();
    }
}
