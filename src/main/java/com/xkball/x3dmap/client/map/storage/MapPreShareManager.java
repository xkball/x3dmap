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
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

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
        extractMapArchives(sourceDirectory, FMLPaths.GAMEDIR.get().resolve("x3dmap").resolve(target.encodedName()));
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

    private static void extractMapArchives(Path sourceDirectory, Path targetDirectory) {
        try (Stream<Path> paths = Files.list(sourceDirectory)) {
            paths.filter(Files::isRegularFile)
                    .filter(MapPreShareManager::isZipArchive)
                    .sorted()
                    .forEach(path -> extractMapArchive(path, targetDirectory));
        } catch (IOException e) {
            LOGGER.error("Failed to enumerate pre-share archives in {}", sourceDirectory.toAbsolutePath(), e);
        }
    }

    private static boolean isZipArchive(Path path) {
        var fileName = path.getFileName().toString();
        return fileName.regionMatches(true, fileName.length() - 4, ".zip", 0, 4);
    }

    private static void extractMapArchive(Path archive, Path targetDirectory) {
        var targetRoot = targetDirectory.toAbsolutePath().normalize();
        try (var input = new ZipInputStream(Files.newInputStream(archive))) {
            ZipEntry entry;
            while ((entry = input.getNextEntry()) != null) {
                extractMapEntry(archive, input, entry, targetRoot);
                input.closeEntry();
            }
        } catch (Exception e) {
            LOGGER.error("Failed to extract pre-share archive {}", archive.toAbsolutePath(), e);
        }
    }

    private static void extractMapEntry(Path archive, ZipInputStream input, ZipEntry entry, Path targetRoot) {
        try {
            var target = targetRoot.resolve(entry.getName()).normalize();
            if (!target.startsWith(targetRoot)) {
                LOGGER.error("Rejected unsafe entry {} in pre-share archive {}", entry.getName(), archive.toAbsolutePath());
                return;
            }
            if (entry.isDirectory()) {
                Files.createDirectories(target);
                return;
            }
            if (Files.exists(target)) {
                return;
            }
            Files.createDirectories(target.getParent());
            Files.copy(input, target);
        } catch (FileAlreadyExistsException ignored) {
        } catch (Exception e) {
            LOGGER.error("Failed to extract entry {} from pre-share archive {}", entry.getName(), archive.toAbsolutePath(), e);
        }
    }

    private record TargetContext(String saveName, @Nullable String serverIp, String encodedName) {
    }
}
