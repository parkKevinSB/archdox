package com.archdox.cloud.system.application;

import com.archdox.shared.version.ArchDoxBuildInfo;
import org.springframework.stereotype.Service;

@Service
public class ArchDoxCloudBuildInfoService {
    private final ArchDoxBuildInfo buildInfo = ArchDoxBuildInfo.load("cloud-api", "0.0.1-SNAPSHOT");

    public ArchDoxBuildInfo current() {
        return buildInfo;
    }
}
