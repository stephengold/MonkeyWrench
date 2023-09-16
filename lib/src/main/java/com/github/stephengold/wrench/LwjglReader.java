/*
 Copyright (c) 2023, Stephen Gold

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

import com.jme3.asset.AssetKey;
import com.jme3.asset.DesktopAssetManager;
import com.jme3.asset.plugins.ClasspathLocator;
import com.jme3.asset.plugins.FileLocator;
import com.jme3.material.Material;
import com.jme3.material.plugins.J3MLoader;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.jme3.texture.Texture;
import com.jme3.texture.plugins.AWTLoader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import jme3utilities.Heart;
import jme3utilities.MyString;
import org.lwjgl.PointerBuffer;
import org.lwjgl.assimp.AILogStream;
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
     * Read a model/scene from the real filesystem.
     *
     * @param filename the filesystem path to the model/scene file (not null)
     * @param verboseLogging true to enable verbose logging, otherwise false
     * @param loadFlags flags to be passed to {@code aiImportFile()}
     * @return a new instance (not null)
     * @throws IOException if lwjgl-assimp fails to import a model/scene
     */
    public static Spatial readCgm(
            String filename, boolean verboseLogging, int loadFlags)
            throws IOException {
        if (verboseLogging) {
            String logFilename = null;
            AILogStream logStream = AILogStream.create();
            logStream = Assimp.aiGetPredefinedLogStream(
                    Assimp.aiDefaultLogStream_STDOUT, logFilename, logStream);
            Assimp.aiAttachLogStream(logStream);
            Assimp.aiEnableVerboseLogging(true);
        }

        AIScene aiScene = Assimp.aiImportFile(filename, loadFlags);
        Assimp.aiDetachAllLogStreams();

        if (aiScene == null || aiScene.mRootNode() == null) {
            // Report the error:
            String quotedName = MyString.quote(filename);
            String errorString = Assimp.aiGetErrorString();
            String message = String.format(
                    "Assimp failed to import a model/scene from %s:%n %s",
                    quotedName, errorString);
            throw new IOException(message);
        }

        // Convert the embedded textures, if any:
        List<Texture> textureList = new ArrayList<>(1); // empty list
        PointerBuffer pTextures = aiScene.mTextures();
        if (pTextures != null) {
            textureList = LwjglAssetLoader.convertTextures(pTextures);
        }

        // Convert the materials:
        List<Material> materialList = new ArrayList<>(1); // empty list
        PointerBuffer pMaterials = aiScene.mMaterials();
        if (pMaterials != null) {
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

            String assetPath = Heart.fixPath(filename);
            AssetKey key = new AssetKey(assetPath);
            String assetFolder = key.getFolder();
            materialList = LwjglAssetLoader.convertMaterials(
                    pMaterials, assetManager, assetFolder, textureList);
        }

        // Convert the nodes and meshes:
        AINode rootNode = aiScene.mRootNode();
        PointerBuffer pMeshes = aiScene.mMeshes();
        Node result
                = LwjglAssetLoader.convertNode(rootNode, materialList, pMeshes);

        return result;
    }
}
