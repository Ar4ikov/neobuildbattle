package com.neobuildbattle.core.voting;

import java.util.HashMap;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class VotingScoreboard {
    private final Map<UUID, Integer> ownerToScore = new HashMap<>();

    public void addScore(UUID ownerId, int add) {
        ownerToScore.merge(ownerId, add, Integer::sum);
    }

    public UUID getWinner() {
        UUID best = null;
        int bestScore = Integer.MIN_VALUE;
        for (Map.Entry<UUID, Integer> e : ownerToScore.entrySet()) {
            if (e.getValue() > bestScore) {
                bestScore = e.getValue();
                best = e.getKey();
            }
        }
        return best;
    }

    public void clear() {
        ownerToScore.clear();
    }

    public int getScore(UUID ownerId) {
        return ownerToScore.getOrDefault(ownerId, 0);
    }

    public List<Map.Entry<UUID, Integer>> getTop(int n) {
        return ownerToScore.entrySet().stream()
                .sorted(Map.Entry.<UUID, Integer>comparingByValue(Comparator.reverseOrder()))
                .limit(n)
                .toList();
    }

    public int getPlace(UUID ownerId) {
        int place = 1;
        int my = getScore(ownerId);
        for (int score : ownerToScore.values()) {
            if (score > my) place++;
        }
        return place;
    }

    public Map<UUID, Integer> getAll() {
        return new LinkedHashMap<>(ownerToScore);
    }
}


