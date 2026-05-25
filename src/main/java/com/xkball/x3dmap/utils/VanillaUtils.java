package com.xkball.x3dmap.utils;

import com.xkball.x3dmap.X3dMap;
import com.xkball.xklib.resource.ResourceLocation;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import org.joml.Vector3f;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public class VanillaUtils {
    
    public static final Identifier MISSING_TEXTURE = Identifier.withDefaultNamespace("missingno");
    public static final Direction[] DIRECTIONS = Direction.values();
    
    public static Identifier modRL(String path) {
        return resourceLocationOf(X3dMap.MODID, path);
    }
    
    public static ResourceLocation modrl(String path) {
        return new ResourceLocation(X3dMap.MODID, path);
    }
    
    public static Identifier resourceLocationOf(String namespace, String path) {
        return Identifier.fromNamespaceAndPath(namespace, path);
    }
    
    //irrelevant vanilla(笑)
    public static int getColor(int r, int g, int b, int a) {
        return a << 24 | r << 16 | g << 8 | b;
    }
    
    public static byte[] gzip(byte[] bytes, int off, int len) {
        try (var byteOut = new ByteArrayOutputStream()) {
            try (GZIPOutputStream gzip = new GZIPOutputStream(byteOut)) {
                gzip.write(bytes, off, len);
            }
            return byteOut.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
    
    public static byte[] unGzip(byte[] bytes) {
        try (GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(bytes))) {
            return gzip.readAllBytes();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
    
    public static int mulColor(int a, int b) {
        var aa = (a >> 24 & 0xff) / 255f;
        var ar = (a >> 16 & 0xff) / 255f;
        var ag = (a >> 8 & 0xff) / 255f;
        var ab = (a & 0xff) / 255f;
        var ba = (b >> 24 & 0xff) / 255f;
        var br = (b >> 16 & 0xff) / 255f;
        var bg = (b >> 8 & 0xff) / 255f;
        var bb = (b & 0xff) / 255f;
        return getColor((int) (ar * br * 255), (int) (ag * bg * 255), (int) (ab * bb * 255), (int) (aa * ba * 255));
    }
    
    public static String memSize(long size) {
        if (size == 0) return "0byte";
        String[] units = {"byte", "kb", "mb", "gb"};
        int i;
        long divider = 1;
        if (size < 1024L) {
            i = 0;
        } else if (size < 1024L * 1024L) {
            i = 1;
            divider = 1024L;
        } else if (size < 1024L * 1024L * 1024L) {
            i = 2;
            divider = 1024L * 1024L;
        } else {
            i = 3;
            divider = 1024L * 1024L * 1024L;
        }
        float value = (float) size / divider;
        return String.format("%.2f", value) + units[i];
    }
    
    public static Vector3f dirVec(float xRot, float yRot) {
        var x = (float) (Math.cos(Math.toRadians(xRot)) * Math.sin(Math.toRadians(yRot)));
        var y = (float) (Math.sin(Math.toRadians(xRot)));
        var z = (float) (Math.cos(Math.toRadians(xRot)) * Math.cos(Math.toRadians(yRot)));
        return new Vector3f(x, y, z).normalize();
    }
}
