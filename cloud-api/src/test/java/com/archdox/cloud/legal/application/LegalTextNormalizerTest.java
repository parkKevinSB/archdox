package com.archdox.cloud.legal.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class LegalTextNormalizerTest {
    private final LegalTextNormalizer normalizer = new LegalTextNormalizer();
    private final LegalArticleHashService hashService = new LegalArticleHashService();

    @Test
    void whitespaceEquivalentTextHasStableHash() {
        var left = normalizer.normalize("Article 1\r\n  Supervision   evidence\tmust exist.  ");
        var right = normalizer.normalize("Article 1\nSupervision evidence must exist.");

        assertThat(left).isEqualTo(right);
        assertThat(hashService.sha256(left)).isEqualTo(hashService.sha256(right));
    }
}
