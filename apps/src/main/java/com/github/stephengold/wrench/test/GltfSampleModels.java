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

import java.io.File;
import java.util.logging.Level;
import java.util.logging.Logger;
import jme3utilities.MyString;

/**
 * ModelGroup for glTF-Sample-Models.
 *
 * @author Stephen Gold sgold@sonic.net
 */
class GltfSampleModels implements ModelGroup {
    // *************************************************************************
    // constants and loggers

    /**
     * message logger for this class
     */
    final static Logger logger
            = Logger.getLogger(GltfSampleModels.class.getName());
    // *************************************************************************
    // fields

    /**
     * true if accessible, otherwise false
     */
    final private boolean isAccessible;
    /**
     * for generating the asset path to a model/scene
     */
    final private String assetPathFormat;
    /**
     * filesystem path to the asset root
     */
    final private String rootPath;
    // *************************************************************************
    // constructors

    /**
     * Instantiate a group for the specified specification and asset form.
     *
     * @param version which version of the glTF specification ("1.0" or "2.0")
     * @param form which asset form ("glTF", "glTF-Binary", "glTF-Draco", or
     * "glTF-Embedded")
     */
    GltfSampleModels(String version, String form) {
        String path
                = String.format("../../ext/glTF-Sample-Models/%s/", version);
        String fileSeparator = System.getProperty("file.separator");
        this.rootPath = path.replace("/", fileSeparator);

        // Test for accessibility:
        File testDir = new File(rootPath);
        this.isAccessible = testDir.isDirectory() && testDir.canRead();
        if (!isAccessible) {
            String cwd = System.getProperty("user.dir");
            logger.log(Level.WARNING, "{0} is not accessible from {1}.",
                    new Object[]{
                        MyString.quote(rootPath), MyString.quote(cwd)
                    });
        }

        String pathFormat;
        switch (form) {
            case "glTF":
                pathFormat = "%s/glTF/%s.gltf";
                break;

            case "glTF-Binary":
                pathFormat = "%s/glTF-Binary/%s.glb";
                break;

            case "glTF-Draco":
                pathFormat = "%s/glTF-Draco/%s.gltf";
                break;

            case "glTF-Embedded":
                pathFormat = "%s/glTF-Embedded/%s.gltf";
                break;

            default:
                throw new IllegalArgumentException("form = " + form);
        }
        this.assetPathFormat = pathFormat;
    }
    // *************************************************************************
    // ModelGroup methods

    /**
     * Return the asset path to the specified model/scene.
     *
     * @param modelName the name of the model/scene (not null)
     * @return the asset path (not null)
     */
    @Override
    public String assetPath(String modelName) {
        String result = String.format(assetPathFormat, modelName, modelName);
        return result;
    }

    /**
     * Test whether this group is accessible.
     *
     * @return true if readable, otherwise false
     */
    @Override
    public boolean isAccessible() {
        return isAccessible;
    }

    /**
     * Enumerate the model/scene names.
     *
     * @return an array of model/scene names (not null, all elements non-null,
     * in ascending lexicographic order)
     */
    @Override
    public String[] listModels() {
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

    /**
     * Return the asset root for the specified model/scene.
     *
     * @param modelName the name of the model/scene (not null)
     * @return a filesystem path (not null, not empty)
     */
    @Override
    public String rootPath(String modelName) {
        return rootPath;
    }
}
