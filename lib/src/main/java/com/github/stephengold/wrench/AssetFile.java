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
import com.jme3.asset.AssetLoadException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.lwjgl.assimp.AIFile;
import org.lwjgl.assimp.Assimp;
import org.lwjgl.system.MemoryUtil;

/**
 * A file in an AssetFileSystem that's been opened for reading.
 *
 * @author Stephen Gold sgold@sonic.net
 */
class AssetFile {
    // *************************************************************************
    // constants and loggers

    /**
     * size of the buffers for reading input streams (in bytes)
     */
    final private static int tmpArrayNumBytes = 4096;
    /**
     * message logger for this class
     */
    final private static Logger logger
            = Logger.getLogger(AssetFile.class.getName());
    // *************************************************************************
    // fields

    /**
     * callbacks used by lwjgl-assimp to access the file
     */
    private AIFile aiFile;
    /**
     * contents of the file (also used to keep track of the read position) or
     * null if not yet read
     */
    private ByteBuffer contents;
    // *************************************************************************
    // constructors

    /**
     * Instantiate a file and open it for reading.
     *
     * @param fileSystem the filesystem that will contain this file (not null,
     * alias created)
     * @param assetInfo (not null, alias created)
     */
    AssetFile(AssetFileSystem fileSystem, AssetInfo assetInfo) {
        // Read the file contents:
        int numBytes = countBytes(assetInfo);
        this.contents = getContents(assetInfo, numBytes);

        // Configure some callbacks for lwjgl-assimp:
        this.aiFile = AIFile.calloc();

        aiFile.FileSizeProc((long fileHandle) -> {
            AssetFile file = fileSystem.findFile(fileHandle);
            long result = file.size();
            return result;
        });

        aiFile.ReadProc((long fileHandle, long destAddress, long bytesPerRecord,
                long recordCount) -> {
            if (logger.isLoggable(Level.INFO)) {
                logger.log(Level.INFO,
                        "Reading {0}*{1} bytes from handle {2} to buffer {3}",
                        new Object[]{recordCount, bytesPerRecord,
                            Long.toHexString(fileHandle),
                            Long.toHexString(destAddress)
                        });
            }

            long byteCount = bytesPerRecord * recordCount;
            assert byteCount >= 0 : byteCount;
            assert byteCount <= Integer.MAX_VALUE : byteCount;

            ByteBuffer targetBuffer = MemoryUtil.memByteBufferSafe(
                    destAddress, (int) byteCount);

            AssetFile file = fileSystem.findFile(fileHandle);
            long result = file.read(targetBuffer, bytesPerRecord, recordCount);

            return result;
        });

        aiFile.SeekProc((long fileHandle, long offset, int origin) -> {
            AssetFile file = fileSystem.findFile(fileHandle);
            file.seek(offset, origin);
            return Assimp.aiReturn_SUCCESS;
        });

        aiFile.TellProc((long fileHandle) -> {
            AssetFile file = fileSystem.findFile(fileHandle);
            long result = file.tell();
            return result;
        });
    }
    // *************************************************************************
    // new methods exposed

    /**
     * Invoked when the file is no longer needed, to free its resources.
     */
    void destroy() {
        if (aiFile != null) {
            aiFile.free();
            this.aiFile = null;
        }

        if (contents != null) {
            MemoryUtil.memFree(contents);
            this.contents = null;
        }
    }

    /**
     * Return the handle used by lwjgl-assimp to access the file.
     *
     * @return the handle of the pre-existing AIFile (not null)
     */
    long handle() {
        long result = aiFile.address();
        return result;
    }
    // *************************************************************************
    // private methods

    /**
     * Return the size of the specified binary asset.
     *
     * @param info the asset to read (not null)
     * @return the size (in bytes, &ge;0)
     */
    private static int countBytes(AssetInfo info) {
        int totalBytes = 0;
        byte[] tmpArray = new byte[tmpArrayNumBytes];
        try (InputStream inputStream = info.openStream()) {
            while (true) {
                int numBytesRead = inputStream.read(tmpArray);
                if (numBytesRead < 0) {
                    break;
                }
                totalBytes += numBytesRead;
            }

        } catch (IOException exception) {
            throw new AssetLoadException(
                    "Failed to determine size of asset.", exception);
        }

        return totalBytes;
    }

    /**
     * Read the entire contents of the specified binary asset to a buffer.
     *
     * @param info the asset to read (not null)
     * @param numBytes the size of the asset (in bytes, &ge;0)
     * @return a new flipped buffer (to be explicitly freed using MemoryUtil)
     */
    private static ByteBuffer getContents(AssetInfo info, int numBytes) {
        //ByteBuffer result = BufferUtils.createByteBuffer(numBytes);
        ByteBuffer result = MemoryUtil.memAlloc(numBytes);

        byte[] tmpArray = new byte[tmpArrayNumBytes];
        try (InputStream inputStream = info.openStream()) {
            while (true) {
                int numBytesRead = inputStream.read(tmpArray);
                if (numBytesRead < 0) {
                    break;

                } else if (numBytesRead == tmpArray.length) {
                    result.put(tmpArray);

                } else {
                    for (int i = 0; i < numBytesRead; ++i) {
                        byte b = tmpArray[i];
                        result.put(b);
                    }
                }
            }
        } catch (IOException exception) {
            throw new AssetLoadException(
                    "Failed to read asset contents.", exception);
        }
        result.flip();

        assert result.limit() == result.capacity() :
                result.limit() + " != " + result.capacity();
        return result;
    }

    /**
     * Starting from the current read position, copy file content to the
     * specified target buffer and advance the read position accordingly.
     *
     * @param targetBuffer the buffer to copy to (not null, rewound, modified)
     * @param bytesPerRecord the size of each record (in bytes, &ge;0)
     * @param recordCount the maximum number of records to copy (&ge;0)
     * @return the number of records copied (&ge;0, &le;recordCount)
     */
    private long read(
            ByteBuffer targetBuffer, long bytesPerRecord, long recordCount) {
        long numRecordsCopied = 0L;
        while (numRecordsCopied < recordCount
                && contents.remaining() >= bytesPerRecord) {
            // Copy one record:
            for (long byteIndex = 0; byteIndex < bytesPerRecord; ++byteIndex) {
                byte b = contents.get();
                targetBuffer.put(b);
            }
            ++numRecordsCopied;
        }

        return numRecordsCopied;
    }

    /**
     * Alter the read position.
     *
     * @param offset the desired offset relative to the origin position (in
     * bytes, may be negative)
     * @param origin the encoded starting point (either
     * {@code Assimp.aiOrigin_SET}, {@code Assimp.aiOrigin_CUR}, or
     * {@code Assimp.aiOrigin_END})
     */
    private void seek(long offset, int origin) {
        if (logger.isLoggable(Level.INFO)) {
            String oString;
            if (origin == Assimp.aiOrigin_SET) {
                oString = "SET";
            } else if (origin == Assimp.aiOrigin_CUR) {
                oString = "CUR";
            } else if (origin == Assimp.aiOrigin_END) {
                oString = "END";
            } else {
                oString = Integer.toString(origin);
            }
            long handle = handle();

            logger.log(Level.INFO, "Seeking to {0}+{1} on handle {2}",
                    new Object[]{oString, offset, Long.toHexString(handle)});
        }

        long originPosition;
        if (origin == Assimp.aiOrigin_SET) {
            originPosition = 0L;
        } else if (origin == Assimp.aiOrigin_CUR) {
            originPosition = tell();
        } else {
            assert origin == Assimp.aiOrigin_END : origin;
            originPosition = size();
        }
        long newPosition = originPosition + offset;

        assert newPosition >= 0L : newPosition;
        assert newPosition <= Integer.MAX_VALUE : newPosition;
        assert newPosition <= contents.capacity() :
                newPosition + " > " + contents.capacity();

        contents.position((int) newPosition);
    }

    /**
     * Return the size of the file.
     *
     * @return the size (in bytes, &ge;0)
     */
    private long size() {
        int result = contents.capacity();
        return result;
    }

    /**
     * Return the current read position.
     *
     * @return the buffer position relative to the start of the file (&ge;0)
     */
    private long tell() {
        int result = contents.position();
        return result;
    }
}
