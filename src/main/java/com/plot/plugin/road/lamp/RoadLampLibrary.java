package com.plot.plugin.road.lamp;

import com.google.gson.Gson;
import com.plot.core.persistence.AtomicFileWriter;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class RoadLampLibrary {
    private static final Gson GSON = new Gson();
    private final Path path;

    public record Entry(UUID id, RoadLampStyle style) {
        public Entry {
            Objects.requireNonNull(id);
            Objects.requireNonNull(style);
        }
    }

    public RoadLampLibrary() {
        this(FabricLoader.getInstance().getConfigDir().resolve("plot-road-lamps.json"));
    }

    RoadLampLibrary(Path path) {
        this.path = path;
    }

    public List<Entry> load() throws IOException {
        if (Files.notExists(path)) return List.of();
        try {
            String json = Files.readString(path);
            if (json.isBlank()) {
                return List.of();
            }
            Entry[] entries = GSON.fromJson(json, Entry[].class);
            return entries == null ? List.of() : List.of(entries);
        } catch (RuntimeException e) {
            throw new IOException("Invalid road lamp library", e);
        }
    }

    public synchronized List<Entry> save(UUID existingId, RoadLampStyle style) throws IOException {
        var entries = new ArrayList<>(load());
        if (entries.stream().anyMatch(entry -> !entry.id().equals(existingId)
                && entry.style().name().equalsIgnoreCase(style.name()))) {
            throw new IllegalArgumentException("Duplicate road lamp preset name");
        }
        if (existingId == null) {
            entries.add(new Entry(UUID.randomUUID(), style));
        } else {
            int index = indexOf(entries, existingId);
            entries.set(index, new Entry(existingId, style));
        }
        return write(entries);
    }

    public synchronized List<Entry> delete(UUID id) throws IOException {
        var entries = new ArrayList<>(load());
        entries.remove(indexOf(entries, id));
        return write(entries);
    }

    private static int indexOf(List<Entry> entries, UUID id) throws IOException {
        for (int i = 0; i < entries.size(); i++) {
            if (entries.get(i).id().equals(id)) return i;
        }
        throw new IOException("Road lamp preset no longer exists");
    }

    private List<Entry> write(List<Entry> entries) throws IOException {
        AtomicFileWriter.write(path, GSON.toJson(entries));
        return List.copyOf(entries);
    }
}
