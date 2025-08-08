package com.neobuildbattle.core.player;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public final class PlayerRegistry {
    private final Set<UUID> activePlayers = Collections.synchronizedSet(new HashSet<>());

    public void add(UUID id) {
        activePlayers.add(id);
    }

    public void remove(UUID id) {
        activePlayers.remove(id);
    }

    public Set<UUID> getActivePlayers() {
        return Collections.unmodifiableSet(activePlayers);
    }
}


