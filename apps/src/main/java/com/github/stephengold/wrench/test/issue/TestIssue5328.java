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
package com.github.stephengold.wrench.test.issue;

import java.util.logging.Logger;
import jme3utilities.MyString;
import org.lwjgl.PointerBuffer;
import org.lwjgl.assimp.AIScene;
import org.lwjgl.assimp.AITexture;
import org.lwjgl.assimp.Assimp;

/**
 * Console application to test aiImportFile on the "truck.3mf" model.
 * <p>
 * If Assimp issue 5328 is encountered, the application will suffer a native
 * crash (aiReleaseImport is invoke) or else print dimensions other than "width
 * = 54813, height = 0" (aiReleaseImport is commented out).
 *
 * @author Stephen Gold sgold@sonic.net
 */
final class TestIssue5328 {
    // *************************************************************************
    // constants and loggers

    /**
     * message logger for this class
     */
    final private static Logger logger
            = Logger.getLogger(TestIssue5328.class.getName());
    // *************************************************************************
    // constructors

    /**
     * A private constructor to inhibit instantiation of this class.
     */
    private TestIssue5328() {
        // do nothing
    }
    // *************************************************************************
    // new methods exposed

    /**
     * Main entry point for the TestIssue5328 application.
     *
     * @param arguments array of command-line arguments (ignored)
     */
    public static void main(String[] arguments) {
        String filename = "../downloads/threejs/3mf/truck.3mf";
        int postFlags = 0x0;

        for (int i = 0; i < 40; ++i) {
            AIScene aiScene = Assimp.aiImportFile(filename, postFlags);
            if (aiScene == null || aiScene.mRootNode() == null) {
                // Report the error:
                String quotedName = MyString.quote(filename);
                String errorString = Assimp.aiGetErrorString();
                String message = String.format(
                        "Assimp failed to import an asset from %s:%n %s",
                        quotedName, errorString);

            } else {
                PointerBuffer pTextures = aiScene.mTextures();
                long handle = pTextures.get(0);
                AITexture aiTexture = AITexture.createSafe(handle);

                int width = aiTexture.mWidth();
                int height = aiTexture.mHeight();
                System.out.println("width = " + width + ", height = " + height);

                //if (width != 54813 || height != 0) {
                //    System.out.println(
                //            "The embedded texture has the wrong dimensions!");
                //}
            }

            //Assimp.aiReleaseImport(aiScene);
        }
    }
}