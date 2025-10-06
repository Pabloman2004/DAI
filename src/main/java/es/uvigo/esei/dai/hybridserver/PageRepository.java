package es.uvigo.esei.dai.hybridserver;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.*;
import java.util.stream.Collectors;

public final class PageRepository {
    private final ConcurrentMap<String, String> pages = new ConcurrentHashMap<>();

    public PageRepository() {
    }

    public PageRepository(Map<String, String> seed) {
        if (seed != null)
            this.pages.putAll(seed);
    }

    public String get(String uuid) {
        return this.pages.get(uuid);
    }

    public void put(String uuid, String html) {
        if (uuid == null)
            throw new IllegalArgumentException("uuid null");
        this.pages.put(uuid, html != null ? html : "");
    }

    public boolean contains(String uuid) {
        return this.pages.containsKey(uuid);
    }

    public String remove(String uuid) {
        return this.pages.remove(uuid);
    }


    public Map<String, String> all() {
        // snapshot inmutable y ordenado por UUID para que sea estable
        return this.pages.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (a, b) -> a,
                        LinkedHashMap::new));
    }
}
