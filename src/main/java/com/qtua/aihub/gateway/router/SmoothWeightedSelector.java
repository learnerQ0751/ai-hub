package com.qtua.aihub.gateway.router;

import com.qtua.aihub.config.GatewayProperties.Deployment;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class SmoothWeightedSelector implements DeploymentSelector {

    private static final int DEFAULT_WEIGHT = 1;

    private final Map<String, RoundState> states = new ConcurrentHashMap<>();

    @Override
    public Deployment select(String alias, List<Deployment> deployments) {
        if (deployments.size() == 1) {
            return deployments.get(0);
        }
        RoundState state = states.computeIfAbsent(stateKey(alias, deployments.size()),
                key -> new RoundState(deployments.size()));
        return state.selectNext(deployments);
    }

    private String stateKey(String alias, int size) {
        return alias + "#" + size;
    }

    private static long weightOf(Deployment deployment) {
        if (!ObjectUtils.isEmpty(deployment.getRpm())) {
            return validWeight(deployment.getRpm());
        }
        if (!ObjectUtils.isEmpty(deployment.getTpm())) {
            return validWeight(deployment.getTpm());
        }
        return DEFAULT_WEIGHT;
    }

    private static long validWeight(Integer weight) {
        return weight > 0 ? weight : DEFAULT_WEIGHT;
    }

    private static final class RoundState {

        private final long[] currentWeights;

        private RoundState(int size) {
            this.currentWeights = new long[size];
        }

        private synchronized Deployment selectNext(List<Deployment> deployments) {
            long totalWeight = 0;
            long bestWeight = Long.MIN_VALUE;
            int bestIndex = 0;
            for (int index = 0; index < deployments.size(); index++) {
                long weight = weightOf(deployments.get(index));
                currentWeights[index] += weight;
                totalWeight += weight;
                if (currentWeights[index] > bestWeight) {
                    bestWeight = currentWeights[index];
                    bestIndex = index;
                }
            }
            currentWeights[bestIndex] -= totalWeight;
            return deployments.get(bestIndex);
        }
    }
}
