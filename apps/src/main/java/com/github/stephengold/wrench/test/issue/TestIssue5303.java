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
import org.lwjgl.PointerBuffer;
import org.lwjgl.assimp.AIMesh;
import org.lwjgl.assimp.AIScene;
import org.lwjgl.assimp.Assimp;

/**
 * Console application to test aiImportFile on the "SimpleMorph" model.
 * <p>
 * If Assimp issue 5303 is encountered, the application will print a diagnostic
 * 'meshes[0] with 2 anim meshes has UNKNOWN morphing method.'
 *
 * @author Stephen Gold sgold@sonic.net
 */
final class TestIssue5303 {
    // *************************************************************************
    // constants and loggers

    /**
     * message logger for this class
     */
    final private static Logger logger
            = Logger.getLogger(TestIssue5303.class.getName());
    // *************************************************************************
    // constructors

    /**
     * A private constructor to inhibit instantiation of this class.
     */
    private TestIssue5303() {
        // do nothing
    }
    // *************************************************************************
    // new methods exposed

    /**
     * Main entry point for the TestIssue5303 application.
     *
     * @param arguments array of command-line arguments (ignored)
     */
    public static void main(String[] arguments) {
        String filename = "Models/Issue5303/SimpleMorph.gltf";
        int postFlags = Assimp.aiProcess_Triangulate;
        AIScene aiScene = Assimp.aiImportFile(filename, postFlags);

        PointerBuffer pMeshes = aiScene.mMeshes();
        int numMeshes = aiScene.mNumMeshes();
        for (int meshIndex = 0; meshIndex < numMeshes; ++meshIndex) {
            long handle = pMeshes.get(meshIndex);
            AIMesh aiMesh = AIMesh.createSafe(handle);
            int numAnimMeshes = aiMesh.mNumAnimMeshes();
            if (numAnimMeshes > 0) {
                int morphingMethod = aiMesh.mMethod();
                if (morphingMethod == Assimp.aiMorphingMethod_UNKNOWN) {
                    String meshName = aiMesh.mName().dataString();
                    System.out.printf("%s with %d anim meshes has UNKNOWN "
                            + "morphing method.", meshName, numAnimMeshes);
                }
            }
        }
    }
}
