package com.qtua.aihub.gateway.router;

import com.qtua.aihub.config.GatewayProperties.Deployment;

import java.util.List;

public interface DeploymentSelector {

    Deployment select(String alias, List<Deployment> deployments);
}
