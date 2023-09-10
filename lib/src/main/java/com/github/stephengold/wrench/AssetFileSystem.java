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
import jme3utilities.MyString;
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
    // constants and loggers

    /**
     * message logger for this class
     */
    final private static Logger logger
            = Logger.getLogger(AssetFileSystem.class.getName());
    // *************************************************************************
    // fields

    /**
     * callbacks used by lwjgl-assimp to access the filesystem
     */
    private AIFileIO aiFileIo;
    /**
     * map AIFile handles to file objects
     */
    final private Map<Long, AssetFile> fileMap = new TreeMap<>();
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
            if (!logger.isLoggable(Level.INFO)) {
                amLogger.setLevel(Level.SEVERE);
            }
            AssetInfo assetInfo = assetManager.locateAsset(assetKey);
            amLogger.setLevel(savedLevel);

            long fileHandle = open(assetInfo);
            return fileHandle;
        });

        aiFileIo.CloseProc((long fsHandle, long fileHandle) -> {
            assert fsHandle == aiFileIo.address();
            if (logger.isLoggable(Level.INFO)) {
                logger.log(Level.INFO, "Closing handle {0}",
                        Long.toHexString(fileHandle));
            }

            AssetFile loaderFile = findFile(fileHandle);
            if (loaderFile == null) {
                logger.log(Level.WARNING, "File not found during close");
            } else {
                loaderFile.destroy();
                fileMap.remove(fileHandle);
            }
        });
    }
    // *************************************************************************
    // new methods exposed

    /**
     * Invoked when the filesystem is no longer needed, to free its resources.
     */
    void destroy() {
        // Close all open files:
        for (AssetFile file : fileMap.values()) {
            file.destroy();
        }
        fileMap.clear();

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
        AssetFile result = fileMap.get(fileHandle);
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
     * Open the asset at the specified path.
     * <p>
     * Opening an asset causes its entire contents to be read and cached.
     *
     * @param assetInfo (may be null)
     * @return a new AIFileIO handle, or zero if {@code assetInfo} is null
     */
    private long open(AssetInfo assetInfo) {
        long result = 0L;
        if (assetInfo != null) { // asset not found
            AssetFile loaderFile = new AssetFile(this, assetInfo);
            result = loaderFile.handle();

            AssetFile oldFile = fileMap.put(result, loaderFile);
            if (oldFile != null) {
                logger.log(Level.WARNING,
                        "Possible duplicate handle {0} removed",
                        Long.toHexString(result));
            }
        }

        if (logger.isLoggable(Level.INFO)) {
            AssetKey assetKey = assetInfo.getKey();
            String filename = assetKey.getName();
            String quotedName = MyString.quote(filename);
            logger.log(Level.INFO, "Opening {0} returns handle {1}",
                    new Object[]{quotedName, Long.toHexString(result)});
        }

        return result;
    }
}
