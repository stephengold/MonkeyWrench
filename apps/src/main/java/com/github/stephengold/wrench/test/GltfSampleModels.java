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

import java.util.logging.Logger;

/**
 * Utility methods to access the models/scenes in glTF-Sample-Models.
 *
 * @author Stephen Gold sgold@sonic.net
 */
final class GltfSampleModels {
    // *************************************************************************
    // constants and loggers

    /**
     * message logger for this class
     */
    final static Logger logger
            = Logger.getLogger(GltfSampleModels.class.getName());
    // *************************************************************************
    // constructors

    /**
     * A private constructor to inhibit instantiation of this class.
     */
    private GltfSampleModels() {
        // do nothing
    }
    // *************************************************************************
    // new methods exposed

    /**
     * Return the asset path to the specified model/scene.
     *
     * @param modelName the name of the model/scene (not null)
     * @return the asset path (not null)
     */
    static String assetPath(String modelName) {
        String result = String.format("%s/glTF/%s.gltf", modelName, modelName);
        return result;
    }

    /**
     * Enumerate the model/scene names.
     *
     * @return a pre-existing array of names (not null, all elements non-null,
     * in ascending lexicographic order)
     */
    static String[] listModels() {
        String[] result = {
            "2CylinderEngine",
            "ABeautifulGame",
            "AlphaBlendModeTest",
            "AnimatedCube",
            "AnimatedMorphCube",
            "AnimatedMorphSphere",
            "AnimatedTriangle",
            "AntiqueCamera",
            "AttenuationTest",
            "Avocado",
            "BarramundiFish",
            "BoomBox",
            "BoomBoxWithAxes",
            "Box",
            "BoxAnimated",
            "BoxInterleaved",
            "BoxTextured",
            "BoxTexturedNonPowerOfTwo",
            "BoxVertexColors",
            "BrainStem",
            "Buggy",
            "Cameras",
            "CesiumMan",
            "CesiumMilkTruck",
            "ClearCoatCarPaint",
            "ClearCoatTest",
            "ClearcoatWicker",
            "Corset",
            "Cube",
            "DamagedHelmet",
            "DragonAttenuation",
            "Duck",
            "EmissiveStrengthTest",
            "EnvironmentTest",
            "FlightHelmet",
            "Fox",
            "GearboxAssy",
            "GlamVelvetSofa",
            "InterpolationTest",
            "IridescenceDielectricSpheres",
            "IridescenceLamp",
            "IridescenceMetallicSpheres",
            "IridescenceSuzanne",
            "IridescentDishWithOlives",
            "Lantern",
            "LightsPunctualLamp",
            "MaterialsVariantsShoe",
            "MeshPrimitiveModes",
            "MetalRoughSpheres",
            "MetalRoughSpheresNoTextures",
            "MorphPrimitivesTest",
            "MorphStressTest",
            "MosquitoInAmber",
            "MultiUVTest",
            "MultipleScenes",
            "NegativeScaleTest",
            "NormalTangentMirrorTest",
            "NormalTangentTest",
            "OrientationTest",
            "ReciprocatingSaw",
            "RecursiveSkeletons",
            "RiggedFigure",
            "RiggedSimple",
            "SciFiHelmet",
            "SheenChair",
            "SheenCloth",
            "SimpleInstancing",
            "SimpleMeshes",
            "SimpleMorph",
            "SimpleSkin",
            "SimpleSparseAccessor",
            "SpecGlossVsMetalRough",
            "SpecularTest",
            "Sponza",
            "StainedGlassLamp",
            "Suzanne",
            "TextureCoordinateTest",
            "TextureEncodingTest",
            "TextureLinearInterpolationTest",
            "TextureSettingsTest",
            "TextureTransformMultiTest",
            "TextureTransformTest",
            "ToyCar",
            "TransmissionRoughnessTest",
            "TransmissionTest",
            "Triangle",
            "TriangleWithoutIndices",
            "TwoSidedPlane",
            "UnlitTest",
            "VC",
            "VertexColorTest",
            "WaterBottle"
        };

        // Verify that the array is in ascending lexicographic order:
        for (int i = 0; i < result.length - 1; ++i) {
            assert result[i].compareTo(result[i + 1]) < 0 : result[i + 1];
        }

        return result;
    }
}
