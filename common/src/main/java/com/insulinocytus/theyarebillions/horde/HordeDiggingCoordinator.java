package com.insulinocytus.theyarebillions.horde;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

final class HordeDiggingCoordinator<K> {
    static final int DEFAULT_LIMIT = 64;
    static final int MIN_LIMIT = 8;
    static final int MAX_PARTICIPANTS = 3;
    static final int EMPTY_GRACE_TICKS = 40;
    static final int DENIED_TICKS = 100;

    private final Map<K, Site> sites = new LinkedHashMap<>();
    private final Map<K, Long> deniedUntilTick = new HashMap<>();
    private int limit = DEFAULT_LIMIT;
    private int activeCount;

    HordeDiggingCoordinator() {
    }

    boolean request(K site, int participantId, double playerDistance, float progressPerTick, long tick) {
        if (isDenied(site, tick)) {
            return false;
        }
        Site existing = sites.get(site);
        if (existing == null) {
            existing = new Site(site, playerDistance);
            sites.put(site, existing);
        }
        Participant participant = existing.participants.get(participantId);
        if (participant == null) {
            if (existing.participants.size() >= MAX_PARTICIPANTS) {
                return false;
            }
            participant = new Participant();
            existing.participants.put(participantId, participant);
        }
        participant.progressPerTick = progressPerTick;
        if (existing.active && participant.lastHeartbeat != tick) {
            existing.progress = Math.min(1.0F, existing.progress + progressPerTick);
            existing.lastActivityTick = tick;
        }
        participant.lastHeartbeat = tick;
        existing.playerDistance = playerDistance;
        return true;
    }

    void release(K site, int participantId) {
        Site existing = sites.get(site);
        if (existing != null) {
            existing.participants.remove(participantId);
        }
    }

    void deny(K site, long tick) {
        remove(site);
        deniedUntilTick.put(site, tick + DENIED_TICKS);
    }

    void complete(K site) {
        remove(site);
    }

    boolean isDenied(K site, long tick) {
        Long until = deniedUntilTick.get(site);
        return until != null && tick < until;
    }

    void tick(long tick) {
        deniedUntilTick.values().removeIf(until -> tick >= until);

        Iterator<Site> iterator = sites.values().iterator();
        while (iterator.hasNext()) {
            Site site = iterator.next();
            if (!site.active) {
                continue;
            }
            site.participants.values().removeIf(participant -> participant.lastHeartbeat < tick - 1);
            if (site.participants.isEmpty() && tick - site.lastActivityTick >= EMPTY_GRACE_TICKS) {
                iterator.remove();
                activeCount--;
            }
        }

        List<Site> pending = new ArrayList<>();
        for (Site site : sites.values()) {
            if (!site.active) {
                pending.add(site);
            }
        }
        pending.sort(Comparator.comparingDouble(site -> site.playerDistance));
        for (Site site : pending) {
            site.participants.values().removeIf(participant -> participant.lastHeartbeat < tick - 1);
            if (site.participants.isEmpty()) {
                sites.remove(site.key);
                continue;
            }
            if (activeCount >= limit) {
                continue;
            }
            site.active = true;
            activeCount++;
            apply(site, tick);
            site.lastActivityTick = tick;
        }
    }

    float progress(K site) {
        Site existing = sites.get(site);
        return existing == null ? 0.0F : existing.progress;
    }

    boolean contains(K site) {
        return sites.containsKey(site);
    }

    boolean isActive(K site) {
        Site existing = sites.get(site);
        return existing != null && existing.active;
    }

    int activeCount() {
        return activeCount;
    }

    void setLimit(int limit) {
        this.limit = Math.clamp(limit, MIN_LIMIT, DEFAULT_LIMIT);
    }

    void removeMatching(Predicate<K> match) {
        Iterator<Map.Entry<K, Site>> iterator = sites.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<K, Site> entry = iterator.next();
            if (match.test(entry.getKey())) {
                if (entry.getValue().active) {
                    activeCount--;
                }
                iterator.remove();
            }
        }
        deniedUntilTick.keySet().removeIf(match);
    }

    private void apply(Site site, long tick) {
        if (site.lastAppliedTick == tick) {
            return;
        }
        float added = 0.0F;
        for (Participant participant : site.participants.values()) {
            added += participant.progressPerTick;
        }
        site.progress = Math.min(1.0F, site.progress + added);
        site.lastAppliedTick = tick;
    }

    private void remove(K site) {
        Site existing = sites.remove(site);
        if (existing != null && existing.active) {
            activeCount--;
        }
    }

    private final class Site {
        private final K key;
        private final Map<Integer, Participant> participants = new LinkedHashMap<>();
        private double playerDistance;
        private float progress;
        private long lastActivityTick;
        private long lastAppliedTick = Long.MIN_VALUE;
        private boolean active;

        private Site(K key, double playerDistance) {
            this.key = key;
            this.playerDistance = playerDistance;
        }
    }

    private static final class Participant {
        private float progressPerTick;
        private long lastHeartbeat = Long.MIN_VALUE;
    }
}
