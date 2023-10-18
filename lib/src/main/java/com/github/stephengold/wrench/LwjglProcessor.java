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

import com.jme3.asset.AssetManager;
import com.jme3.scene.Node;
import com.jme3.texture.Texture;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import org.lwjgl.PointerBuffer;
import org.lwjgl.assimp.AIScene;

/**
 * Process data that has been imported by lwjgl-assimp.
 *
 * @author Stephen Gold sgold@sonic.net
 */
class LwjglProcessor {
    // *************************************************************************
    // constants and loggers

    /**
     * message logger for this class
     */
    final private static Logger logger
            = Logger.getLogger(LwjglProcessor.class.getName());
    // *************************************************************************
    // fields

    /**
     * data imported by lwjgl-assimp
     */
    final private AIScene aiScene;
    /**
     * true if verbose logging is requested, otherwise false
     */
    final private boolean verboseLogging;
    /**
     * true if the scene has Z-up orientation, otherwise false
     */
    final private boolean zUp;
    /**
     * post-processing flags that were passed to {@code aiImportFile()}
     */
    final private int loadFlags;
    /**
     * builder for each material in the AIScene
     */
    private List<MaterialBuilder> builderList;
    // *************************************************************************
    // constructors

    /**
     * Instantiate a processor to process the specified AIScene.
     *
     * @param aiScene the imported data (not null, alias created)
     * @param loadFlags the post-processing flags that were passed to
     * {@code aiImportFile()}
     * @param verboseLogging true if verbose logging was requested, otherwise
     * false
     * @throws IOException if the AIScene metadata cannot be processed
     */
    LwjglProcessor(AIScene aiScene, int loadFlags, boolean verboseLogging)
            throws IOException {
        this.aiScene = aiScene;
        this.loadFlags = loadFlags;
        this.verboseLogging = verboseLogging;

        this.builderList = new ArrayList<>(1); // empty list
        this.zUp = LwjglReader.processFlagsAndMetadata(aiScene, verboseLogging);
    }
    // *************************************************************************
    // new methods exposed

    /**
     * Convert the materials in the AIScene to builders.
     *
     * @param assetManager for loading textures (not null)
     * @param assetFolder the asset path to the folder from which the
     * model/scene was loaded (not null)
     * @param embeddedTextures the array of embedded textures (not null)
     * @throws IOException if the materials cannot be converted
     */
    void convertMaterials(AssetManager assetManager, String assetFolder,
            Texture[] embeddedTextures) throws IOException {
        PointerBuffer pMaterials = aiScene.mMaterials();
        this.builderList = LwjglReader.convertMaterials(
                pMaterials, assetManager, assetFolder, embeddedTextures,
                loadFlags, verboseLogging);
    }

    /**
     * Complete the conversion of the AIScene into a JMonkeyEngine scene graph.
     * <p>
     * Before invoking this method, any materials in the AIScene should've
     * already been converted to builders.
     *
     * @return a new scene-graph subtree (not null)
     * @throws IOException if the AIScene cannot be converted to a scene graph
     */
    Node toSceneGraph() throws IOException {
        Node result = LwjglReader.toSceneGraph(aiScene, builderList);
        return result;
    }

    /**
     * Test whether the model/scene has Z-up orientation.
     *
     * @return true if the orientation is Z-up, otherwise false
     */
    boolean zUp() {
        return zUp;
    }
}
