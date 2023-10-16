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
package com.github.stephengold.wrench.test;

/**
 * A group of named models/scenes for testing.
 *
 * @author Stephen Gold sgold@sonic.net
 */
interface ModelGroup {
    /**
     * Return the asset path to the specified model/scene.
     *
     * @param modelName the name of a model/scene (not null)
     * @return the asset path, or null if the name is not recognized for this
     * group
     */
    String assetPath(String modelName);

    /**
     * Test whether this group is accessible.
     *
     * @return true if readable, otherwise false
     */
    boolean isAccessible();

    /**
     * Enumerate the models/scenes in this group.
     *
     * @return a pre-existing array of names (not null, all elements non-null,
     * in ascending lexicographic order)
     */
    String[] listModels();

    /**
     * Return the asset root for the specified model/scene.
     *
     * @param modelName the name of a model/scene (not null)
     * @return a filesystem path, or null if the name is not recognized for this
     * group
     */
    String rootPath(String modelName);
}
