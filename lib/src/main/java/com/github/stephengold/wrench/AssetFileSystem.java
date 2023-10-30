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

import com.jme3.asset.AssetInfo;
import com.jme3.asset.AssetKey;
import com.jme3.asset.AssetManager;
import java.util.Map;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.lwjgl.assimp.AIFileIO;
import org.lwjgl.system.MemoryUtil;

/**
 * A read-only virtual filesystem based on a JMonkeyEngine AssetManager, to be
 * used by lwjgl-assimp.
 *
 * @author Stephen Gold sgold@sonic.net
 */
class AssetFileSystem {
    // *************************************************************************
    // fields

    /**
     * callbacks used by lwjgl-assimp to access the filesystem
     */
    private AIFileIO aiFileIo;
    /**
     * map AIFile handles to open file objects
     */
    final private Map<Long, AssetFile> openFileMap = new TreeMap<>();
    /**
     * map asset paths to file content
     */
    final private Map<String, byte[]> contentCache = new TreeMap<>();
    // *************************************************************************
    // constructors

    /**
     * Instantiate a filesystem based on the specified AssetManager.
     *
     * @param assetManager (not null, alias created)
     */
    AssetFileSystem(AssetManager assetManager) {
        // Configure callbacks for lwjgl-assimp:
        this.aiFileIo = AIFileIO.calloc();

        aiFileIo.OpenProc((long fsHandle, long fileName, long openMode) -> {
            String mode = MemoryUtil.memUTF8Safe(openMode);
            assert mode != null && mode.equals("rb");

            String assetPath = MemoryUtil.memUTF8Safe(fileName);
            AssetKey<Object> assetKey = new AssetKey<>(assetPath);

            // Temporarily hush AssetManager warnings about missing resources:
            Logger amLogger = Logger.getLogger(AssetManager.class.getName());
            Level savedLevel = amLogger.getLevel();
            amLogger.setLevel(Level.SEVERE);
            AssetInfo assetInfo = assetManager.locateAsset(assetKey);
            amLogger.setLevel(savedLevel);

            long fileHandle = open(assetInfo);
            return fileHandle;
        });

        aiFileIo.CloseProc((long fsHandle, long fileHandle) -> {
            assert fsHandle == aiFileIo.address();
            AssetFile openFile = findFile(fileHandle);
            if (openFile != null) {
                openFile.destroy();
                openFileMap.remove(fileHandle);
            }
        });
    }
    // *************************************************************************
    // new methods exposed

    /**
     * Invoked when the filesystem is no longer needed, to free its resources.
     */
    void destroy() {
        // Destroy all open files:
        for (AssetFile file : openFileMap.values()) {
            file.destroy();
        }
        openFileMap.clear();

        if (aiFileIo != null) {
            aiFileIo.free();
            this.aiFileIo = null;
        }
    }

    /**
     * Access an open file via its handle.
     *
     * @param fileHandle the handle of an {@code AIFile} instance
     * @return the pre-existing AssetFile instance, or null if none
     */
    AssetFile findFile(long fileHandle) {
        AssetFile result = openFileMap.get(fileHandle);
        return result;
    }

    /**
     * Return the object used by lwjgl-assimp to access the filesystem.
     *
     * @return the pre-existing instance (not null)
     */
    AIFileIO getAccess() {
        assert aiFileIo != null;
        return aiFileIo;
    }
    // *************************************************************************
    // private methods

    /**
     * Open the specified asset.
     * <p>
     * Opening an asset causes all its content to be read and cached.
     *
     * @param assetInfo the asset to open, or null for a non-existent asset
     * @return a new AIFileIO handle, or zero if {@code assetInfo} was null
     */
    private long open(AssetInfo assetInfo) {
        long result = 0L;
        if (assetInfo != null) { // The asset exists:
            AssetKey key = assetInfo.getKey();
            String assetPath = key.getName();
            byte[] contentArray = contentCache.get(assetPath);
            AssetFile loaderFile = new AssetFile(this, assetInfo, contentArray);

            result = loaderFile.handle();
            openFileMap.put(result, loaderFile);

            // Cache the content for future use:
            contentArray = loaderFile.getContentArray();
            contentCache.put(assetPath, contentArray);
        }

        return result;
    }
}
