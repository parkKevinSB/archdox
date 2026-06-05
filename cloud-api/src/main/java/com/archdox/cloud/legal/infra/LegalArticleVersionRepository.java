package com.archdox.cloud.legal.infra;

import com.archdox.cloud.legal.domain.LegalArticleVersion;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LegalArticleVersionRepository extends JpaRepository<LegalArticleVersion, Long> {
    List<LegalArticleVersion> findByLegalVersionId(Long legalVersionId);

    Optional<LegalArticleVersion> findByArticleIdAndLegalVersionId(Long articleId, Long legalVersionId);

    @Query("""
            select new com.archdox.cloud.legal.infra.LegalArticleCorpusRow(
                act.id,
                act.actCode,
                act.actName,
                act.actType,
                source.code,
                version.id,
                version.sourceVersionKey,
                version.effectiveDate,
                version.sourceUrl,
                article.id,
                articleVersion.id,
                articleVersion.articleKey,
                articleVersion.articleNo,
                articleVersion.articleTitle,
                articleVersion.articleText,
                articleVersion.contentHash
            )
            from LegalArticleVersion articleVersion, LegalArticle article, LegalVersion version, LegalAct act, LegalSource source
            where article.id = articleVersion.articleId
              and version.id = articleVersion.legalVersionId
              and act.id = version.actId
              and source.id = act.sourceId
              and source.code <> :excludedSourceCode
              and version.id = (
                  select max(version2.id)
                  from LegalVersion version2
                  where version2.actId = act.id
                    and (:effectiveDate is null or version2.effectiveDate is null or version2.effectiveDate <= :effectiveDate)
              )
              and (:hasActCode = false or upper(act.actCode) = upper(:actCode))
              and (:hasActName = false or lower(act.actName) like lower(concat('%', :actName, '%')))
              and (:hasArticleNo = false or articleVersion.articleNo = :articleNo)
              and (
                  :hasQuery = false
                  or lower(act.actName) like lower(concat('%', :query, '%'))
                  or lower(articleVersion.articleNo) like lower(concat('%', :query, '%'))
                  or lower(coalesce(articleVersion.articleTitle, '')) like lower(concat('%', :query, '%'))
                  or lower(articleVersion.normalizedText) like lower(concat('%', :query, '%'))
              )
            order by act.actName asc, article.sortOrder asc, articleVersion.articleNo asc
            """)
    List<LegalArticleCorpusRow> searchLatestArticles(
            @Param("query") String query,
            @Param("hasQuery") boolean hasQuery,
            @Param("actCode") String actCode,
            @Param("hasActCode") boolean hasActCode,
            @Param("actName") String actName,
            @Param("hasActName") boolean hasActName,
            @Param("articleNo") String articleNo,
            @Param("hasArticleNo") boolean hasArticleNo,
            @Param("effectiveDate") LocalDate effectiveDate,
            @Param("excludedSourceCode") String excludedSourceCode,
            Pageable pageable);

    @Query("""
            select new com.archdox.cloud.legal.infra.LegalArticleCorpusRow(
                act.id,
                act.actCode,
                act.actName,
                act.actType,
                source.code,
                version.id,
                version.sourceVersionKey,
                version.effectiveDate,
                version.sourceUrl,
                article.id,
                articleVersion.id,
                articleVersion.articleKey,
                articleVersion.articleNo,
                articleVersion.articleTitle,
                articleVersion.articleText,
                articleVersion.contentHash
            )
            from LegalArticleVersion articleVersion, LegalArticle article, LegalVersion version, LegalAct act, LegalSource source
            where article.id = articleVersion.articleId
              and version.id = articleVersion.legalVersionId
              and act.id = version.actId
              and source.id = act.sourceId
              and source.code <> :excludedSourceCode
              and articleVersion.id = :articleVersionId
            """)
    Optional<LegalArticleCorpusRow> findCorpusRowByArticleVersionId(
            @Param("articleVersionId") Long articleVersionId,
            @Param("excludedSourceCode") String excludedSourceCode);

    @Query("""
            select new com.archdox.cloud.legal.infra.LegalArticleCorpusRow(
                act.id,
                act.actCode,
                act.actName,
                act.actType,
                source.code,
                version.id,
                version.sourceVersionKey,
                version.effectiveDate,
                version.sourceUrl,
                article.id,
                articleVersion.id,
                articleVersion.articleKey,
                articleVersion.articleNo,
                articleVersion.articleTitle,
                articleVersion.articleText,
                articleVersion.contentHash
            )
            from LegalArticleVersion articleVersion, LegalArticle article, LegalVersion version, LegalAct act, LegalSource source
            where article.id = articleVersion.articleId
              and version.id = articleVersion.legalVersionId
              and act.id = version.actId
              and source.id = act.sourceId
              and source.code <> :excludedSourceCode
              and article.id = :articleId
              and version.id = (
                  select max(version2.id)
                  from LegalVersion version2
                  where version2.actId = act.id
                    and (:effectiveDate is null or version2.effectiveDate is null or version2.effectiveDate <= :effectiveDate)
              )
            order by version.id desc
            """)
    List<LegalArticleCorpusRow> findLatestCorpusRowsByArticleId(
            @Param("articleId") Long articleId,
            @Param("effectiveDate") LocalDate effectiveDate,
            @Param("excludedSourceCode") String excludedSourceCode,
            Pageable pageable);

    @Query("""
            select new com.archdox.cloud.legal.infra.LegalArticleCorpusRow(
                act.id,
                act.actCode,
                act.actName,
                act.actType,
                source.code,
                version.id,
                version.sourceVersionKey,
                version.effectiveDate,
                version.sourceUrl,
                article.id,
                articleVersion.id,
                articleVersion.articleKey,
                articleVersion.articleNo,
                articleVersion.articleTitle,
                articleVersion.articleText,
                articleVersion.contentHash
            )
            from LegalArticleVersion articleVersion, LegalArticle article, LegalVersion version, LegalAct act, LegalSource source
            where article.id = articleVersion.articleId
              and version.id = articleVersion.legalVersionId
              and act.id = version.actId
              and source.id = act.sourceId
              and source.code <> :excludedSourceCode
              and upper(act.actCode) = upper(:actCode)
              and articleVersion.articleNo = :articleNo
              and version.id = (
                  select max(version2.id)
                  from LegalVersion version2
                  where version2.actId = act.id
                    and (:effectiveDate is null or version2.effectiveDate is null or version2.effectiveDate <= :effectiveDate)
              )
            order by article.sortOrder asc
            """)
    List<LegalArticleCorpusRow> findLatestCorpusRowsByActCodeAndArticleNo(
            @Param("actCode") String actCode,
            @Param("articleNo") String articleNo,
            @Param("effectiveDate") LocalDate effectiveDate,
            @Param("excludedSourceCode") String excludedSourceCode,
            Pageable pageable);
}
