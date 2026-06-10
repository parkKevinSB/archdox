package com.archdox.documentai;

import io.github.parkkevinsb.flower.ai.harness.finding.AiFinding;
import io.github.parkkevinsb.flower.ai.harness.finding.FindingExtractor;
import io.github.parkkevinsb.flower.ai.harness.run.AiHarnessRunContext;
import java.util.List;

public final class NarrativePolishFindingExtractor implements FindingExtractor<NarrativePolishResult> {
    @Override
    public List<AiFinding> extract(NarrativePolishResult value, AiHarnessRunContext ctx) {
        return List.of();
    }
}
