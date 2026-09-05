package com.xkball.x3dmap.client.map.storage;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import com.xkball.xklibmc.annotation.NonNullByDefault;
import net.minecraft.client.Minecraft;
import net.minecraft.util.FileUtil;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

@NonNullByDefault
public final class MapPreShareManager {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final List<String> TARGET_FIELDS = List.of("targets", "saves", "saveNames", "servers", "serverIps", "name", "save", "saveName", "server", "ip", "serverIp");

    private MapPreShareManager() {
    }

    public static void applyIfMatched() {
        var target = currentTarget();
        if (target == null) {
            return;
        }
        var sourceDirectory = FMLPaths.GAMEDIR.get().resolve("x3dmap_preshare");
        var metadata = sourceDirectory.resolve("meta.json");
        if (!Files.isRegularFile(metadata)) {
            return;
        }
        var targets = readTargets(metadata);
        if (targets.isEmpty() || !matches(targets, target)) {
            return;
        }
        copyMapFiles(sourceDirectory, FMLPaths.GAMEDIR.get().resolve("x3dmap").resolve(target.encodedName()));
    }

    private static @Nullable TargetContext currentTarget() {
        var integratedServer = ServerLifecycleHooks.getCurrentServer();
        if (integratedServer != null) {
            var worldPath = integratedServer.getWorldPath(LevelResource.ROOT);
            var saveDirectory = worldPath.getParent();
            if (saveDirectory != null && saveDirectory.getFileName() != null) {
                var saveName = saveDirectory.getFileName().toString();
                return new TargetContext(saveName, null, FileUtil.sanitizeName(saveName));
            }
        }
        var player = Minecraft.getInstance().player;
        if (player == null) {
            return null;
        }
        var serverData = player.connection.getServerData();
        if (serverData == null) {
            return null;
        }
        return new TargetContext(serverData.name, serverData.ip, FileUtil.sanitizeName(serverData.name));
    }

    private static Set<String> readTargets(Path metadata) {
        try {
            var root = JsonParser.parseString(Files.readString(metadata, StandardCharsets.UTF_8));
            var targets = new HashSet<String>();
            collectTargets(root, targets);
            return targets;
        } catch (Exception e) {
            LOGGER.error("Failed to read pre-share metadata from {}", metadata.toAbsolutePath(), e);
            return Set.of();
        }
    }

    private static void collectTargets(JsonElement element, Set<String> targets) {
        if (element.isJsonNull()) {
            return;
        }
        if (element.isJsonPrimitive()) {
            var value = element.getAsString().strip();
            if (!value.isEmpty()) {
                targets.add(value);
            }
            return;
        }
        if (element.isJsonArray()) {
            for (var child : element.getAsJsonArray()) {
                collectTargets(child, targets);
            }
            return;
        }
        var object = element.getAsJsonObject();
        for (var field : TARGET_FIELDS) {
            if (object.has(field)) {
                collectTargets(object.get(field), targets);
            }
        }
    }

    private static boolean matches(Set<String> targets, TargetContext target) {
        for (var value : targets) {
            if (value.equals(target.saveName()) || value.equals(target.encodedName())) {
                return true;
            }
            if (target.serverIp() != null && value.equalsIgnoreCase(target.serverIp())) {
                return true;
            }
        }
        return false;
    }

    private static void copyMapFiles(Path sourceDirectory, Path targetDirectory) {
        try (Stream<Path> paths = Files.walk(sourceDirectory)) {
            paths.filter(Files::isRegularFile)
                    .filter(path -> !path.equals(sourceDirectory.resolve("meta.json")))
                    .forEach(path -> copyMapFile(sourceDirectory, targetDirectory, path));
        } catch (IOException e) {
            LOGGER.error("Failed to enumerate pre-share files in {}", sourceDirectory.toAbsolutePath(), e);
        }
    }

    private static void copyMapFile(Path sourceDirectory, Path targetDirectory, Path source) {
        try {
            var target = targetDirectory.resolve(sourceDirectory.relativize(source));
            if (Files.exists(target)) {
                return;
            }
            var parent = target.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            if (Files.notExists(target)) {
                Files.copy(source, target);
            }
        } catch (Exception e) {
            LOGGER.error("Failed to copy pre-share file {}", source.toAbsolutePath(), e);
        }
    }

    private record TargetContext(String saveName, @Nullable String serverIp, String encodedName) {
    }
}
