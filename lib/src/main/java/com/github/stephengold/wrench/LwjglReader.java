/*
 Copyright (c) 2023-2025 Stephen Gold

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
package com.github.stephengold.wrench;

import com.jme3.asset.DesktopAssetManager;
import com.jme3.asset.plugins.ClasspathLocator;
import com.jme3.asset.plugins.FileLocator;
import com.jme3.material.plugins.J3MLoader;
import com.jme3.math.FastMath;
import com.jme3.math.Matrix4f;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.jme3.texture.Texture;
import com.jme3.texture.plugins.AWTLoader;
import java.io.IOException;
import java.nio.IntBuffer;
import java.util.Map;
import java.util.logging.Logger;
import jme3utilities.Heart;
import jme3utilities.MyString;
import org.lwjgl.PointerBuffer;
import org.lwjgl.assimp.AILogStream;
import org.lwjgl.assimp.AIMatrix4x4;
import org.lwjgl.assimp.AINode;
import org.lwjgl.assimp.AIScene;
import org.lwjgl.assimp.Assimp;

/**
 * Read assets from the real filesystem using lwjgl-assimp.
 *
 * @author Stephen Gold sgold@sonic.net
 */
final public class LwjglReader {
    // *************************************************************************
    // constants and loggers

    /**
     * message logger for this class
     */
    final private static Logger logger
            = Logger.getLogger(LwjglReader.class.getName());
    // *************************************************************************
    // constructors

    /**
     * A private constructor to inhibit instantiation of this class.
     */
    private LwjglReader() {
        // do nothing
    }
    // *************************************************************************
    // new methods exposed

    /**
     * Count the meshes in the specified AINode and all its descendants. Note:
     * recursive!
     *
     * @param aiNode the node to process (not null, unaffected)
     * @return the count (&ge;0)
     */
    static int countMeshesInSubtree(AINode aiNode) {
        int result = aiNode.mNumMeshes();

        int numChildren = aiNode.mNumChildren();
        if (numChildren > 0) {
            PointerBuffer pChildren = aiNode.mChildren();
            for (int childIndex = 0; childIndex < numChildren; ++childIndex) {
                long handle = pChildren.get(childIndex);
                AINode aiChild = AINode.createSafe(handle);
                result += countMeshesInSubtree(aiChild);
            }
        }

        return result;
    }

    /**
     * Print metadata that's been converted to a Map. Recursive.
     *
     * @param map the converted data (not null, unaffected)
     * @param prefix printed at the start of each line of output
     */
    static void dumpMetaData(Map<String, Object> map, String prefix) {
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            String mdKey = entry.getKey();
            Object data = entry.getValue();
            if (data instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> submap = (Map<String, Object>) data;
                dumpMetaData(submap, prefix + mdKey + "/");

            } else {
                if (data instanceof String) {
                    String stringData = (String) data;
                    data = MyString.quote(stringData);
                }
                System.out.printf("%s%s: %s%n", prefix, mdKey, data);
            }
        }
    }

    /**
     * Print the specified tree of Assimp nodes to {@code System.out}. Note:
     * recursive!
     *
     * @param aiNode the node to start from (not null, unaffected)
     * @param indent the indent text (not null, may be empty)
     */
    static void dumpNodes(AINode aiNode, String indent) {
        System.out.print(indent);

        String name = aiNode.mName().dataString();
        System.out.print(MyString.quote(name));

        System.out.print(" (");
        int numMeshes = aiNode.mNumMeshes();
        IntBuffer pMeshIds = aiNode.mMeshes();
        for (int i = 0; i < numMeshes; ++i) {
            if (i > 0) {
                System.out.print(" ");
            }
            int meshId = pMeshIds.get(i);
            System.out.print(meshId);
        }
        System.out.print(")");

        AIMatrix4x4 aiMatrix = aiNode.mTransformation();
        Matrix4f t = ConversionUtils.convertMatrix(aiMatrix);
        System.out.printf(" [%s %s %s %s]",
                MyString.describe(t.m00), MyString.describe(t.m01),
                MyString.describe(t.m02), MyString.describe(t.m03));
        System.out.printf(" [%s %s %s %s]",
                MyString.describe(t.m10), MyString.describe(t.m11),
                MyString.describe(t.m12), MyString.describe(t.m13));
        System.out.printf(" [%s %s %s %s]%n",
                MyString.describe(t.m20), MyString.describe(t.m21),
                MyString.describe(t.m22), MyString.describe(t.m23));

        int numChildren = aiNode.mNumChildren();
        if (numChildren > 0) {
            PointerBuffer pChildren = aiNode.mChildren();
            for (int childIndex = 0; childIndex < numChildren; ++childIndex) {
                long handle = pChildren.get(childIndex);
                AINode aiChild = AINode.createSafe(handle);
                dumpNodes(aiChild, indent + "  ");
            }
        }
    }

    /**
     * Log importer progress to the standard output.
     * <p>
     * Remember to invoke {@code Assimp.aiDetachAllLogStreams()} when done
     * importing the mode/scene!
     */
    static void enableVerboseLogging() {
        String logFilename = null;
        AILogStream logStream = AILogStream.create();
        logStream = Assimp.aiGetPredefinedLogStream(
                Assimp.aiDefaultLogStream_STDOUT, logFilename, logStream);
        Assimp.aiAttachLogStream(logStream);

        Assimp.aiEnableVerboseLogging(true);
    }

    /**
     * Read an animation/model/scene asset from the real filesystem.
     *
     * @param filename the filesystem path to the main asset (not null)
     * @param verboseLogging true to enable verbose logging, otherwise false
     * @param flags post-processing flags
     * @return a new scene-graph subtree (not null)
     * @throws IOException if lwjgl-assimp fails to import an asset or if the
     * imported asset cannot be converted to a scene graph
     */
    public static Spatial readCgm(
            String filename, boolean verboseLogging, AssimpProcessFlag... flags)
            throws IOException {
        int bitmask = 0x0;
        for (AssimpProcessFlag flag : flags) {
            bitmask |= flag.getValue();
        }

        Spatial result = readCgm(filename, verboseLogging, bitmask);
        return result;
    }

    /**
     * Read an animation/model/scene asset from the real filesystem.
     *
     * @param filename the filesystem path to the main asset (not null)
     * @param verboseLogging true to enable verbose logging, otherwise false
     * @param loadFlags post-processing flags to be passed to
     * {@code aiImportFile()}
     * @return a new scene-graph subtree (not null)
     * @throws IOException if lwjgl-assimp fails to import an asset or if the
     * imported asset cannot be converted to a scene graph
     */
    public static Spatial readCgm(
            String filename, boolean verboseLogging, int loadFlags)
            throws IOException {
        if (verboseLogging) {
            enableVerboseLogging();
        }

        AIScene aiScene = Assimp.aiImportFile(filename, loadFlags);
        Assimp.aiDetachAllLogStreams();

        if (aiScene == null || aiScene.mRootNode() == null) {
            Assimp.aiReleaseImport(aiScene);

            // Report the error:
            String quotedName = MyString.quote(filename);
            String errorString = Assimp.aiGetErrorString();
            String message = String.format(
                    "Assimp failed to import an asset from %s:%n %s",
                    quotedName, errorString);
            throw new IOException(message);
        }

        // Create an LwjglAssetKey for the main asset:
        String assetPath = Heart.fixPath(filename);
        LwjglAssetKey mainKey = new LwjglAssetKey(assetPath, loadFlags);
        mainKey.setVerboseLogging(verboseLogging);

        AssetBuilder assetBuilder = new AssetBuilder(aiScene, mainKey);
        if (!assetBuilder.isComplete()) {
            Assimp.aiReleaseImport(aiScene);
            throw new IOException(
                    "The imported data structure is not a complete scene.");
        }

        // Convert the embedded textures, if any:
        Texture[] textureArray = new Texture[0];
        int numTextures = aiScene.mNumTextures();
        if (numTextures > 0) {
            PointerBuffer pTextures = aiScene.mTextures();
            textureArray
                    = ConversionUtils.convertTextures(pTextures, loadFlags);
        }

        // Convert the materials:
        int numMaterials = aiScene.mNumMaterials();
        if (numMaterials > 0) {
            /*
             * Create a temporary AssetManager for loading
             * material definitions and non-embedded textures:
             */
            DesktopAssetManager assetManager = new DesktopAssetManager();
            assetManager.registerLocator("/", FileLocator.class);
            assetManager.registerLocator("/", ClasspathLocator.class);
            assetManager.registerLoader(
                    AWTLoader.class, "bmp", "gif", "jpg", "jpeg", "png");
            assetManager.registerLoader(J3MLoader.class, "j3md");

            assetBuilder.convertMaterials(assetManager, textureArray);
        }

        Node result;
        try {
            result = assetBuilder.buildCompleteScene();
        } finally {
            Assimp.aiReleaseImport(aiScene);
        }

        boolean zUp = assetBuilder.isZUp();
        if (zUp) {
            // Rotate to JMonkeyEngine's Y-up orientation.
            result.rotate(-FastMath.HALF_PI, 0f, 0f);
        }

        return result;
    }

    /**
     * Return the version string of the MonkeyWrench library.
     *
     * @return a release name or a snapshot name (not null, not empty)
     */
    public static String version() {
        return "1.0.1-SNAPSHOT";
    }
}
