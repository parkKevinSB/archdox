package com.archdox.cloud.photo.infra;

import com.archdox.cloud.photo.domain.PhotoAsset;
import com.archdox.cloud.photo.domain.PhotoAssetStatus;
import com.archdox.cloud.photo.domain.PhotoAssetType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PhotoAssetRepository extends JpaRepository<PhotoAsset, Long> {
    List<PhotoAsset> findByPhotoIdOrderById(Long photoId);

    List<PhotoAsset> findByPhotoIdInOrderByPhotoIdAscIdAsc(List<Long> photoIds);

    Optional<PhotoAsset> findByPhotoIdAndAssetType(Long photoId, PhotoAssetType assetType);

    @Query("""
            select count(asset) > 0
            from PhotoAsset asset
            join asset.photo photo
            where photo.reportId = :reportId
              and photo.officeId = :officeId
              and asset.assetType = :assetType
              and asset.status = :status
            """)
    boolean existsUploadedAsset(
            @Param("reportId") Long reportId,
            @Param("officeId") Long officeId,
            @Param("assetType") PhotoAssetType assetType,
            @Param("status") PhotoAssetStatus status
    );

    @Query("""
            select count(asset)
            from PhotoAsset asset
            join asset.photo photo
            where photo.reportId = :reportId
              and photo.officeId = :officeId
              and asset.assetType = :assetType
              and asset.status = :status
            """)
    long countUploadedAsset(
            @Param("reportId") Long reportId,
            @Param("officeId") Long officeId,
            @Param("assetType") PhotoAssetType assetType,
            @Param("status") PhotoAssetStatus status
    );
}
