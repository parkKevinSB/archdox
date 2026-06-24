package com.archdox.cloud.photo.infra;

import com.archdox.cloud.photo.domain.Photo;
import com.archdox.cloud.photo.domain.PhotoPickupStatus;
import com.archdox.cloud.photo.domain.PhotoStatus;
import com.archdox.cloud.photo.domain.PhotoUploadTarget;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface PhotoRepository extends JpaRepository<Photo, Long> {
    List<Photo> findByOfficeIdAndReportIdOrderByIdDesc(Long officeId, Long reportId);

    List<Photo> findByOfficeIdAndReportIdAndStatusNotOrderByIdDesc(Long officeId, Long reportId, PhotoStatus status);

    List<Photo> findByOfficeIdAndReportIdAndStatusAndCreatedAtBefore(
            Long officeId,
            Long reportId,
            PhotoStatus status,
            OffsetDateTime createdBefore);

    List<Photo> findByStatusAndOriginalPickupStatusAndUploadTargetOrderByConfirmedAtAsc(
            PhotoStatus status,
            PhotoPickupStatus originalPickupStatus,
            PhotoUploadTarget uploadTarget);

    List<Photo> findByStatusAndOriginalPickupStatusAndUpdatedAtBeforeOrderByUpdatedAtAsc(
            PhotoStatus status,
            PhotoPickupStatus originalPickupStatus,
            OffsetDateTime updatedBefore,
            Pageable pageable);

    Optional<Photo> findByIdAndOfficeId(Long id, Long officeId);

    Optional<Photo> findFirstByOfficeIdAndHashSha256AndStatus(Long officeId, String hashSha256, PhotoStatus status);

    long countByOfficeId(Long officeId);

    long countByOfficeIdAndStatus(Long officeId, PhotoStatus status);

    long countByOfficeIdAndOriginalPickupStatus(Long officeId, PhotoPickupStatus status);

    long countByStatus(PhotoStatus status);

    long countByStatusAndOriginalPickupStatus(PhotoStatus status, PhotoPickupStatus originalPickupStatus);

    long countByOriginalPickupStatus(PhotoPickupStatus status);

    @Query("""
            select photo
            from Photo photo
            where photo.officeId = :officeId
              and (:status is null or photo.status = :status)
              and (:originalPickupStatus is null or photo.originalPickupStatus = :originalPickupStatus)
            order by photo.createdAt desc, photo.id desc
            """)
    List<Photo> searchOfficePhotos(
            Long officeId,
            PhotoStatus status,
            PhotoPickupStatus originalPickupStatus,
            Pageable pageable);

    @Query("""
            select photo
            from Photo photo
            where (:officeId is null or photo.officeId = :officeId)
              and (:status is null or photo.status = :status)
              and (:originalPickupStatus is null or photo.originalPickupStatus = :originalPickupStatus)
            order by photo.createdAt desc, photo.id desc
            """)
    List<Photo> searchPlatformPhotos(
            Long officeId,
            PhotoStatus status,
            PhotoPickupStatus originalPickupStatus,
            Pageable pageable);
}
