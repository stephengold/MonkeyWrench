/*
 Copyright (c) 2025 Stephen Gold

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

import org.lwjgl.assimp.Assimp;

/**
 * Represents common post-processing flags for the Assimp library. These flags
 * are used to tell Assimp how to process the imported 3D model data. Each enum
 * constant corresponds to a specific Assimp.aiProcess_* flag value.
 *
 * @author Wyatt Gillette (aka capdevon)
 */
public enum AssimpProcessFlag {
    /**
     * Joins identical vertices into a single vertex. This reduces the number of
     * vertices and can improve performance.
     */
    JOIN_IDENTICAL_VERTICES(Assimp.aiProcess_JoinIdenticalVertices),
    /**
     * Converts all non-triangular faces (e.g., quads, N-gons) into triangles.
     * Most rendering pipelines require triangular meshes.
     */
    TRIANGULATE(Assimp.aiProcess_Triangulate),
    /**
     * Generates per-face normals for all meshes if they are not already
     * present. Useful for flat shading.
     */
    GEN_NORMALS(Assimp.aiProcess_GenNormals),
    /**
     * Generates per-vertex smooth normals for all meshes if they are not
     * already present. Essential for smooth lighting and shading.
     */
    GEN_SMOOTH_NORMALS(Assimp.aiProcess_GenSmoothNormals),
    /**
     * Splits large meshes into smaller sub-meshes. Can be useful for optimizing
     * rendering with limited vertex buffer sizes.
     */
    SPLIT_LARGE_MESHES(Assimp.aiProcess_SplitLargeMeshes),
    /**
     * Applies the node transformations to the vertex data directly, effectively
     * flattening the scene graph.
     */
    PRE_TRANSFORM_VERTICES(Assimp.aiProcess_PreTransformVertices),
    /**
     * Limits the number of bone weights per vertex to a specified maximum.
     * Important for hardware skinning compatibility.
     */
    LIMIT_BONE_WEIGHTS(Assimp.aiProcess_LimitBoneWeights),
    /**
     * Validates the imported data structure to check for inconsistencies.
     * Useful for debugging.
     */
    VALIDATE_DATA_STRUCTURE(Assimp.aiProcess_ValidateDataStructure),
    /**
     * Reorders vertices and indices to improve vertex cache locality. Can lead
     * to better rendering performance.
     */
    IMPROVE_CACHE_LOCALITY(Assimp.aiProcess_ImproveCacheLocality),
    /**
     * Removes redundant materials if multiple materials are identical. Saves
     * memory.
     */
    REMOVE_REDUNDANT_MATERIALS(Assimp.aiProcess_RemoveRedundantMaterials),
    /**
     * Fixes normals that are pointing inwards. Corrects lighting artifacts.
     */
    FIX_INFACING_NORMALS(Assimp.aiProcess_FixInfacingNormals),
    /**
     * Sorts meshes by their primitive type. If a mesh contains mixed primitive
     * types, it will be split into multiple meshes.
     */
    SORT_BY_PTYPE(Assimp.aiProcess_SortByPType),
    /**
     * Finds and removes degenerate primitives (e.g., zero-area triangles).
     */
    FIND_DEGENERATES(Assimp.aiProcess_FindDegenerates),
    /**
     * Detects and removes invalid model data, such as invalid normal vectors or
     * UVs.
     */
    FIND_INVALID_DATA(Assimp.aiProcess_FindInvalidData),
    /**
     * Generates a default set of UV coordinates if the model does not have
     * them.
     */
    GEN_UV_COORDS(Assimp.aiProcess_GenUVCoords),
    /**
     * Applies an arbitrary 4x4 matrix to the UV coordinates.
     */
    TRANSFORM_UV_COORDS(Assimp.aiProcess_TransformUVCoords),
    /**
     * Searches for and replaces duplicate meshes with references to a single
     * instance.
     */
    FIND_INSTANCES(Assimp.aiProcess_FindInstances),
    /**
     * Optimizes the mesh data by combining small meshes into larger ones.
     * Reduces draw calls.
     */
    OPTIMIZE_MESHES(Assimp.aiProcess_OptimizeMeshes),
    /**
     * Optimizes the scene graph by collapsing redundant nodes or simplifying
     * the hierarchy.
     */
    OPTIMIZE_GRAPH(Assimp.aiProcess_OptimizeGraph),
    /**
     * Flips the V (or Y) component of all texture coordinates. Useful for
     * aligning UVs with different coordinate conventions.
     */
    FLIP_UVS(Assimp.aiProcess_FlipUVs),
    /**
     * Reverses the winding order of all faces. Affects back-face culling.
     */
    FLIP_WINDING_ORDER(Assimp.aiProcess_FlipWindingOrder),
    /**
     * Calculates tangent and bitangent vectors for each vertex. Essential for
     * normal mapping.
     */
    CALC_TANGENT_SPACE(Assimp.aiProcess_CalcTangentSpace),
    /**
     * Converts the entire scene from a right-handed coordinate system to a
     * left-handed one.
     */
    MAKE_LEFT_HANDED(Assimp.aiProcess_MakeLeftHanded),
    /**
     * Removes bones that have no influence on any vertices.
     */
    DEBONE(Assimp.aiProcess_Debone),
    /**
     * Applies a global scaling factor to the scene.
     */
    GLOBAL_SCALE(Assimp.aiProcess_GlobalScale),
    /**
     * Embeds textures into the model file itself.
     */
    EMBED_TEXTURES(Assimp.aiProcess_EmbedTextures),
    /**
     * Forces the generation of normals, even if they are already present.
     */
    FORCE_GEN_NORMALS(Assimp.aiProcess_ForceGenNormals),
    /**
     * Drops all normals from the scene.
     */
    DROP_NORMALS(Assimp.aiProcess_DropNormals),
    /**
     * Generates bounding boxes for all meshes.
     */
    GEN_BOUNDING_BOXES(Assimp.aiProcess_GenBoundingBoxes),
    /**
     * Converts the entire scene to a left-handed coordinate system. This is a
     * combination of other flags, often used as a convenience.
     */
    CONVERT_TO_LEFT_HANDED(Assimp.aiProcess_ConvertToLeftHanded);

    private final int value;

    /**
     * Private constructor to set the integer value for each enum constant.
     *
     * @param value The integer value representing the Assimp flag.
     */
    AssimpProcessFlag(int value) {
        this.value = value;
    }

    /**
     * Returns the integer value of the Assimp processing flag.
     *
     * @return The integer value of the flag.
     */
    public int getValue() {
        return value;
    }

    /**
     * Combines an array of AssimpProcessFlag enums into a single integer
     * bitmask. This bitmask can then be passed to Assimp's import functions.
     *
     * @param flags An array of AssimpProcessFlag enums to combine.
     * @return An integer representing the bitwise OR of all provided flags.
     */
    public static int getBitmask(AssimpProcessFlag... flags) {
        int ppFlags = 0;
        for (AssimpProcessFlag flag : flags) {
            ppFlags |= flag.getValue();
        }
        return ppFlags;
    }
}
