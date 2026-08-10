/*
 Copyright (c) 2026 Stephen Gold

 Redistribution and use in source and binary forms, with or without
 modification, are permitted provided that the following conditions are met:

 1. Redistributions of source code must retain the above copyright notice, this
    list of conditions and the following disclaimer.

 2. Redistributions in binary form must reproduce the above copyright notice,
    this list of conditions and the following disclaimer in the documentation
    and/or other materials provided with the distribution.

 3. Neither the name of the copyright holder nor the names of its
    contributors may be used to endorse or promote products derived from
    this software without specific prior written permission.

 THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
 FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.github.stephengold.wrench.junit4;

import com.github.stephengold.wrench.AssetKeyBuilder;
import com.github.stephengold.wrench.AssimpProcessFlag;
import com.github.stephengold.wrench.Component;
import com.github.stephengold.wrench.LwjglAssetKey;
import com.github.stephengold.wrench.PathEdit;
import com.github.stephengold.wrench.Primitive;
import com.github.stephengold.wrench.TextureLoader;
import com.jme3.math.Matrix4f;
import org.junit.Assert;
import org.junit.Test;
import org.lwjgl.assimp.Assimp;

/**
 * Automated JUnit4 tests for the {@code AssetKeyBuilder} class.
 *
 * @author Stephen Gold sgold@sonic.net
 */
public class TestAssetKeyBuilder {
    // *************************************************************************
    // new methods exposed

    /**
     * Test a key built from a default builder.
     */
    @Test
    public void testDefaultBuilder() {
        AssetKeyBuilder defaultBuilder = new AssetKeyBuilder();
        LwjglAssetKey key = defaultBuilder.build("Models/model.gltf");

        Assert.assertEquals(LwjglAssetKey.defaultFlags, key.flags());
        Assert.assertEquals("gltf", key.getExtension());
        Assert.assertEquals("Models/", key.getFolder());
        Assert.assertEquals(
                LwjglAssetKey.defaultGlobalScale, key.getGlobalScale(), 0f);
        Assert.assertEquals("Models/model.gltf", key.getName());
        Assert.assertEquals(new TextureLoader(), key.getTextureLoader());
        Assert.assertFalse(key.isVerboseLogging());
    }

    /**
     * Test a key built from a builder on which many import properties have been
     * set.
     */
    @Test
    public void testImportProperties() {
        Matrix4f rootTransform = new Matrix4f(
                +2f, +3f, -4f, +5f,
                +6f, -2f, -3f, +4f,
                +9f, +0f, +8f, +7f,
                -5f, -7f, -6f, 1f);
        Matrix4f copyRootTransform = rootTransform.clone();

        AssetKeyBuilder builder = new AssetKeyBuilder()
                .setCtMaxSmoothingAngle(33f)
                .setCtTextureChannelIndex(2)
                .setDbAllOrNone(true)
                .setDbThreshold(3f)
                .setFdCheckArea(false)
                .setFidAnimAccuracy(9e-3f)
                .setGlobalScale(39.37f)
                .setGsnMaxSmoothingAngle(99f)
                .setIclPtCacheSize(10)
                .setLbwMaxWeights(5)
                .setPtvAddRootTransformation(true)
                .setPtvKeepHierarchy(false)
                .setPtvNormalize(true)
                .setPtvRootTransformation(rootTransform)
                .setRvcFlags(Component.CAMERAS, Component.LIGHTS)
                .setSbbcMaxBones(3)
                .setSbpRemove(Primitive.LINE, Primitive.POINT)
                .setSlmTriangleLimit(65535)
                .setSlmVertexLimit(32767);

        // Ensure the matrix isn't aliased:
        rootTransform.loadIdentity();

        LwjglAssetKey key = builder.build();

        Assert.assertEquals(Float.valueOf(33f), key.findFloatProperty(
                Assimp.AI_CONFIG_PP_CT_MAX_SMOOTHING_ANGLE));
        Assert.assertEquals(Integer.valueOf(2), key.findIntegerProperty(
                Assimp.AI_CONFIG_PP_CT_TEXTURE_CHANNEL_INDEX));
        Assert.assertEquals(Integer.valueOf(1), key.findIntegerProperty(
                Assimp.AI_CONFIG_PP_DB_ALL_OR_NONE));
        Assert.assertEquals(Float.valueOf(3f), key.findFloatProperty(
                Assimp.AI_CONFIG_PP_DB_THRESHOLD));
        Assert.assertEquals(Integer.valueOf(0), key.findIntegerProperty(
                Assimp.AI_CONFIG_PP_FD_CHECKAREA));
        Assert.assertEquals(Float.valueOf(9e-3f), key.findFloatProperty(
                Assimp.AI_CONFIG_PP_FID_ANIM_ACCURACY));
        Assert.assertEquals(39.37f, key.getGlobalScale(), 0f);
        Assert.assertEquals(Float.valueOf(99f), key.findFloatProperty(
                Assimp.AI_CONFIG_PP_GSN_MAX_SMOOTHING_ANGLE));
        Assert.assertEquals(Integer.valueOf(10), key.findIntegerProperty(
                Assimp.AI_CONFIG_PP_ICL_PTCACHE_SIZE));
        Assert.assertEquals(Integer.valueOf(5), key.findIntegerProperty(
                Assimp.AI_CONFIG_PP_LBW_MAX_WEIGHTS));
        Assert.assertEquals(Integer.valueOf(1), key.findIntegerProperty(
                Assimp.AI_CONFIG_PP_PTV_ADD_ROOT_TRANSFORMATION));
        Assert.assertEquals(Integer.valueOf(0), key.findIntegerProperty(
                Assimp.AI_CONFIG_PP_PTV_KEEP_HIERARCHY));
        Assert.assertEquals(Integer.valueOf(1), key.findIntegerProperty(
                Assimp.AI_CONFIG_PP_PTV_NORMALIZE));
        Assert.assertEquals(copyRootTransform, key.findMatrixProperty(
                Assimp.AI_CONFIG_PP_PTV_ROOT_TRANSFORMATION));
        Assert.assertEquals(
                Integer.valueOf(Assimp.aiComponent_CAMERAS
                        | Assimp.aiComponent_LIGHTS),
                key.findIntegerProperty(Assimp.AI_CONFIG_PP_RVC_FLAGS));
        Assert.assertEquals(Integer.valueOf(3), key.findIntegerProperty(
                Assimp.AI_CONFIG_PP_SBBC_MAX_BONES));
        Assert.assertEquals(
                Integer.valueOf(Assimp.aiPrimitiveType_LINE
                        | Assimp.aiPrimitiveType_POINT),
                key.findIntegerProperty(Assimp.AI_CONFIG_PP_SBP_REMOVE));
        Assert.assertEquals(Integer.valueOf(65535), key.findIntegerProperty(
                Assimp.AI_CONFIG_PP_SLM_TRIANGLE_LIMIT));
        Assert.assertEquals(Integer.valueOf(32767), key.findIntegerProperty(
                Assimp.AI_CONFIG_PP_SLM_VERTEX_LIMIT));
    }

    /**
     * Test keys built from a builder whose process flags have been modified in
     * various ways.
     */
    @Test
    public void testProcessFlags() {
        AssetKeyBuilder builder = new AssetKeyBuilder();
        {
            builder.setProcessFlags(0x1234);

            Assert.assertEquals(0x1234, builder.getProcessFlags());
            LwjglAssetKey key = builder.build();
            Assert.assertEquals(0x1234, key.flags());
        }
        {
            builder.clearProcessFlags();

            Assert.assertEquals(0x0, builder.getProcessFlags());
            LwjglAssetKey key = builder.build();
            Assert.assertEquals(0x0, key.flags());
        }
        {
            builder.add(AssimpProcessFlag.DEBONE);

            Assert.assertEquals(
                    Assimp.aiProcess_Debone, builder.getProcessFlags());
            LwjglAssetKey key = builder.build();
            Assert.assertEquals(Assimp.aiProcess_Debone, key.flags());
        }
        {
            builder.remove(AssimpProcessFlag.DEBONE)
                    .add(AssimpProcessFlag.FLIP_UVS)
                    .setVerboseLogging(true);

            Assert.assertEquals(
                    Assimp.aiProcess_FlipUVs, builder.getProcessFlags());
            LwjglAssetKey key = builder.build();
            Assert.assertEquals(Assimp.aiProcess_FlipUVs, key.flags());
            Assert.assertTrue(key.isVerboseLogging());
        }
    }

    /**
     * Test texture loaders built from a builder.
     */
    @Test
    public void testTextureLoaders() {
        AssetKeyBuilder builder = new AssetKeyBuilder();
        {
            builder.appendFormatString("%s");

            TextureLoader loader1 = builder.createTextureLoader();
            Assert.assertEquals(
                    new TextureLoader(PathEdit.NoOp, "%s"), loader1);

            LwjglAssetKey key = builder.build();
            TextureLoader loader2 = key.getTextureLoader();
            Assert.assertEquals(
                    new TextureLoader(PathEdit.NoOp, "%s"), loader2);
        }
        {
            builder.setPathEdit(PathEdit.LastComponent);

            TextureLoader loader1 = builder.createTextureLoader();
            Assert.assertEquals(
                    new TextureLoader(PathEdit.LastComponent, "%s"), loader1);

            LwjglAssetKey key = builder.build();
            TextureLoader loader2 = key.getTextureLoader();
            Assert.assertEquals(
                    new TextureLoader(PathEdit.LastComponent, "%s"), loader2);
        }
        {
            builder.setFormatStrings("t/%3$s", "%3$s");

            TextureLoader loader1 = builder.createTextureLoader();
            Assert.assertEquals(
                    new TextureLoader(PathEdit.LastComponent, "t/%3$s", "%3$s"),
                    loader1);

            LwjglAssetKey key = builder.build();
            TextureLoader loader2 = key.getTextureLoader();
            Assert.assertEquals(
                    new TextureLoader(PathEdit.LastComponent, "t/%3$s", "%3$s"),
                    loader2);
        }
    }
}
