package com.admin.common.dto;

import lombok.Data;

@Data
public class NodeInstallCommandDto {

    private String serviceName;

    private String installCommand;

    private String updateCommand;

    private String uninstallCommand;
}
