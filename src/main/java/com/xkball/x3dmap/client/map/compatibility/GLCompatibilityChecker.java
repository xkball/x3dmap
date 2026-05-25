package com.xkball.x3dmap.client.map.compatibility;

import com.mojang.logging.LogUtils;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GLCapabilities;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;

public class GLCompatibilityChecker {

    private static final Logger LOGGER = LogUtils.getLogger();

    public static List<String> checkMissingExtensions() {
        var missing = new ArrayList<String>();
        try {
            GLCapabilities caps = GL.getCapabilities();
            if (!caps.GL_ARB_multi_draw_indirect) {
                missing.add("GL_ARB_multi_draw_indirect (MDI)");
            }
            if (!caps.GL_ARB_sparse_texture) {
                missing.add("GL_ARB_sparse_texture (Sparse Texture)");
            }
            if (!caps.GL_ARB_shader_storage_buffer_object) {
                missing.add("GL_ARB_shader_storage_buffer_object (SSBO)");
            }
        } catch (Exception e) {
            LOGGER.warn("Failed to check GL capabilities, assuming extensions are present", e);
        }
        return missing;
    }
}
