/*
 Copyright (c) 2023-2024 Stephen Gold

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

import com.jme3.asset.AssetInfo;
import com.jme3.asset.AssetKey;
import com.jme3.asset.AssetLoader;
import com.jme3.asset.AssetManager;
import com.jme3.math.FastMath;
import com.jme3.scene.Node;
import com.jme3.texture.Texture;
import java.io.IOException;
import java.util.logging.Logger;
import jme3utilities.MyString;
import org.lwjgl.PointerBuffer;
import org.lwjgl.assimp.AIFileIO;
import org.lwjgl.assimp.AIScene;
import org.lwjgl.assimp.Assimp;

/**
 * A versatile loader for animation/model/scene assets based on lwjgl-assimp.
 *
 * @author Stephen Gold sgold@sonic.net
 */
final public class LwjglAssetLoader implements AssetLoader {
    // *************************************************************************
    // constants and loggers

    /**
     * message logger for this class
     */
    final private static Logger logger
            = Logger.getLogger(LwjglAssetLoader.class.getName());
    // *************************************************************************
    // constructors

    /**
     * The publicly accessible no-arg constructor required by
     * {@code DesktopAssetManager}, made explicit to avoid javadoc warnings from
     * JDK 18.
     */
    public LwjglAssetLoader() {
        // do nothing
    }
    // *************************************************************************
    // AssetLoader methods

    /**
     * Load a located asset using lwjgl-assimp and an AssetManager-based virtual
     * filesystem.
     *
     * @param assetInfo the located asset (not null)
     * @return a new instance (not null)
     */
    @Override
    public Object load(AssetInfo assetInfo) throws IOException {
        AssetKey<?> assetKey = assetInfo.getKey();
        if (assetKey == null) {
            throw new IllegalArgumentException("AssetInfo lacks a key!");
        }
        LwjglAssetKey key;
        if (assetKey instanceof LwjglAssetKey) {
            key = (LwjglAssetKey) assetKey;
        } else {
            key = new LwjglAssetKey(assetKey);
        }

        try {
            Node result = loadScene(assetInfo, key);
            return result;

        } catch (IOException exception) {
            // To aid troubleshooters:
            exception.printStackTrace();

            throw exception;
        }
    }
    // *************************************************************************
    // AssetLoader methods

    /**
     * Load an asset using lwjgl-assimp and an AssetManager-based virtual
     * filesystem.
     *
     * @param info the located asset (not null)
     * @param assetKey the asset key (not null, unaffected)
     * @return a new scene-graph subtree (not null)
     * @throws IOException if lwjgl-assimp fails to import an asset or if the
     * imported asset cannot be converted to a scene graph
     */
    private static Node loadScene(AssetInfo info, LwjglAssetKey assetKey)
            throws IOException {
        boolean verboseLogging = assetKey.isVerboseLogging();
        if (verboseLogging) {
            LwjglReader.enableVerboseLogging();
        }

        // Create a temporary virtual filesystem:
        AssetManager assetManager = info.getManager();
        AssetFileSystem tempFileSystem = new AssetFileSystem(assetManager);
        AIFileIO aiFileIo = tempFileSystem.getAccess();

        String filename = assetKey.getName();
        int postFlags = assetKey.flags();
        AIScene aiScene = Assimp.aiImportFileEx(filename, postFlags, aiFileIo);
        Assimp.aiDetachAllLogStreams();

        if (aiScene == null || aiScene.mRootNode() == null) {
            Assimp.aiReleaseImport(aiScene);
            tempFileSystem.destroy();

            // Report the error:
            String quotedName = MyString.quote(filename);
            String errorString = Assimp.aiGetErrorString();
            String message = String.format(
                    "Assimp failed to import an asset from %s:%n %s",
                    quotedName, errorString);
            throw new IOException(message);
        }

        AssetBuilder assetBuilder = new AssetBuilder(aiScene, assetKey);
        Node result;
        if (assetBuilder.isComplete()) {
            // Convert the embedded textures, if any:
            Texture[] textureArray = new Texture[0];
            int numTextures = aiScene.mNumTextures();
            if (numTextures > 0) {
                PointerBuffer pTextures = aiScene.mTextures();
                textureArray
                        = ConversionUtils.convertTextures(pTextures, postFlags);
            }

            // Convert the materials:
            int numMaterials = aiScene.mNumMaterials();
            if (numMaterials > 0) {
                assetBuilder.convertMaterials(assetManager, textureArray);
            }

            tempFileSystem.destroy();
            try {
                result = assetBuilder.buildCompleteScene();
            } finally {
                Assimp.aiReleaseImport(aiScene);
            }

            boolean zUp = assetBuilder.isZUp();
            if (zUp) {
                // Rotate to JMonkeyEngine's Y-up orientation:
                result.rotate(-FastMath.HALF_PI, 0f, 0f);
            }

        } else { // Incomplete AIScene, return a single Node:
            tempFileSystem.destroy();

            try {
                result = assetBuilder.buildAnimationNode();
            } catch (IOException exception) {
                result = null;
            }

            if (result == null) {
                try {
                    result = assetBuilder.buildCameraAndLightNodes();
                } catch (IOException exception) {
                    // do nothing
                }
            }

            Assimp.aiReleaseImport(aiScene);
        }

        return result;
    }
}
