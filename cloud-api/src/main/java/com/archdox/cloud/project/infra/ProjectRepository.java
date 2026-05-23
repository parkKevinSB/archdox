package com.archdox.cloud.project.infra;

import com.archdox.cloud.project.domain.Project;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectRepository extends JpaRepository<Project, Long> {
    List<Project> findByOfficeIdOrderByUpdatedAtDesc(Long officeId);

    Optional<Project> findByIdAndOfficeId(Long id, Long officeId);
}
