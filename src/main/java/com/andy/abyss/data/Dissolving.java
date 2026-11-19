package com.andy.abyss.data;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Ids of entities (client side) that are being unmade into the abyss: their vanilla death poof is suppressed. */
public final class Dissolving {
    public static final Set<Integer> IDS = ConcurrentHashMap.newKeySet();
    private Dissolving() {}
}
