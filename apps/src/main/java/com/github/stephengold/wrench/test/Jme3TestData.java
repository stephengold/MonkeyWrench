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
 * AssetGroup for a specific version of jme3-testdata.
 *
 * @author Stephen Gold sgold@sonic.net
 */
class Jme3TestData implements AssetGroup {
    // *************************************************************************
    // constants and loggers

    /**
     * message logger for this class
     */
    private final static Logger logger
            = Logger.getLogger(Jme3TestData.class.getName());
    /**
     * names of assets in jme3-testdata v3.1.0, in ascending lexicographic order
     */
    final private static String[] names310 = {
        "AnimTest", "BaseMesh249", "BaseScene", "BasicCubeLow", "Boat",
        "Curves", "Elephant", "Ferrari", "HoverTank", "ManyLights", "Materials",
        "Modifiers", "MonkeyHead", "MountainValley", "Ninja", "Nurbs",
        "ObjectAnimation", "Particles", "Positions", "Rocket", "SignPost",
        "SinbadBlend", "SinbadXml", "Sword", "TeapotObj", "TeapotXml",
        "Terrain", "TexturedPlane", "Textures", "Tree", "WaterTest"
    };
    /**
     * names of the assets in jme3-testdata v3.6.1, in ascending lexicographic
     * order
     */
    final private static String[] names361 = {
        "BasicCubeLow", "Boat", "Box", "Duck", "Elephant", "Ferrari",
        "HoverTank", "ManyLights", "MonkeyHead", "Ninja", "PbrRef", "Rocket",
        "SignPost", "SinbadXml", "Sword", "TeapotObj", "TeapotXml", "Terrain",
        "Tree", "TwoChairs", "WaterTest"
    };
    // *************************************************************************
    // fields

    /**
     * true if accessible, otherwise false
     */
    final private boolean isAccessible;
    /**
     * filesystem path to the asset root
     */
    final private String rootPath;
    /**
     * names of the assets in ascending lexicographic order (not empty)
     */
    final private String[] namesArray;
    // *************************************************************************
    // constructors

    /**
     * Instantiate a group for the specified version of jme3-testdata.
     *
     * @param version which version of JMonkeyEngine ("3.1.0-stable" or
     * "3.6.1-stable")
     */
    Jme3TestData(String version) {
        String path
                = String.format("../downloads/jme3-testdata-%s.jar", version);
        String fileSeparator = System.getProperty("file.separator");
        this.rootPath = path.replace("/", fileSeparator);

        // Test for accessibility:
        File testDir = new File(rootPath);
        this.isAccessible = testDir.isFile() && testDir.canRead();
        if (!isAccessible) {
            String cwd = System.getProperty("user.dir");
            logger.log(Level.WARNING, "{0} is not accessible from {1}.",
                    new Object[]{
                        MyString.quote(rootPath), MyString.quote(cwd)
                    });
        }

        switch (version) {
            case "3.1.0-stable":
                this.namesArray = names310;
                break;

            case "3.6.1-stable":
                this.namesArray = names361;
                break;

            default:
                throw new IllegalArgumentException("version = " + version);
        }
    }
    // *************************************************************************
    // AssetGroup methods

    /**
     * Return the path to the specified asset.
     *
     * @param assetName the name of the asset (not null)
     * @return the asset path, or null if the name is not recognized
     */
    @Override
    public String assetPath(String assetName) {
        String result;
        switch (assetName) {
            case "AnimTest": // removed from v3.4.0
                result = "Blender/2.4x/animtest.blend";
                break;

            case "BaseMesh249": // removed from v3.4.0
                result = "Blender/2.4x/BaseMesh_249.blend";
                break;

            case "BaseMesh256": // removed from v3.4.0
                // Assimp fails due to issue 5242
                result = "Blender/2.5x/BaseMesh_256.blend";
                break;

            case "BaseScene": // removed from v3.4.0
                result = "Blender/2.4x/BaseScene.blend";
                break;

            case "BasicCubeLow":
                result = "Models/Test/BasicCubeLow.obj";
                break;

            case "Boat":
                result = "Models/Boat/boat.mesh.xml";
                break;

            case "Box": // added in v3.2.0
                result = "Models/gltf/box/box.gltf";
                break;

            case "Constraints": // removed from v3.4.0
                // Assimp fails due to issue 5242
                result = "Blender/2.4x/constraints.blend";
                break;

            case "Curves": // removed from v3.4.0
                result = "Blender/2.4x/curves.blend";
                break;

            case "Duck": // added in v3.2.0
                result = "Models/gltf/duck/Duck.gltf";
                break;

            case "Elephant":
                // TODO Assimp doesn't recognize materials in .j3m files.
                result = "Models/Elephant/Elephant.mesh.xml";
                break;

            case "Ferrari":
                /*
                 * TODO Assimp doesn't recognize the .scene file extension
                 * and has no suitable reader for the format.
                 */
                //result = "Models/Ferrari/Car.scene";
                result = "Models/Ferrari/Car.mesh.xml";
                break;

            case "HoverTank":
                // TODO Assimp doesn't recognize materials in .j3m files.
                result = "Models/HoverTank/Tank2.mesh.xml";
                break;

            case "Kerrigan": // removed from v3.4.0
                // Assimp fails due to issue 5242
                result = "Blender/2.4x/kerrigan.blend";
                break;

            case "ManyLights":
                /*
                 * TODO Assimp doesn't recognize the .scene file extension
                 * and has no suitable reader for the format.
                 */
                //result = "Scenes/ManyLights/Main.scene";
                result = "Scenes/ManyLights/Grid.mesh.xml";
                break;

            case "Materials": // removed from v3.4.0
                result = "Blender/2.4x/materials.blend";
                break;

            case "Modifiers": // removed from v3.4.0
                result = "Blender/2.4x/modifiers.blend";
                break;

            case "MonkeyHead":
                // TODO Assimp doesn't recognize materials in .j3m files.
                result = "Models/MonkeyHead/MonkeyHead.mesh.xml";
                break;

            case "MountainValley": // removed from v3.4.0
                result = "Blender/2.4x/MountainValley_Track.blend";
                break;

            case "Ninja":
                /*
                 * TODO Assimp ignores the 4th argument of "specular"
                 * which apparently indicates shininess.
                 */
                result = "Models/Ninja/Ninja.mesh.xml";
                break;

            case "Nurbs": // removed from v3.4.0
                result = "Blender/2.4x/nurbs.blend";
                break;

            case "ObjectAnimation": // removed from v3.4.0
                result = "Blender/2.4x/ObjectAnimation.blend";
                break;

            case "Oto": // Assimp hangs due to issue 5232
                result = "Models/Oto/Oto.mesh.xml";
                break;

            case "Particles": // removed from v3.4.0
                result = "Blender/2.4x/particles.blend";
                break;

            case "PbrRef": // added in v3.2.0
                result = "Scenes/PBR/ref/scene.gltf";
                break;

            case "Positions": // removed from v3.4.0
                result = "Blender/2.4x/positions.blend";
                break;

            case "Rocket":
                result = "Models/SpaceCraft/Rocket.mesh.xml";
                break;

            case "SignPost":
                result = "Models/Sign Post/Sign Post.mesh.xml";
                break;

            case "SimpleAnimation": // removed from v3.4.0
                // Assimp fails due to issue 5242
                result = "Blender/2.4x/SimpleAnimation.blend";
                break;

            case "SinbadBlend": // removed from v3.4.0
                result = "Blender/2.4x/Sinbad.blend";
                break;

            case "SinbadXml":
                /*
                 * TODO Assimp ignores the 4th argument of "specular"
                 * which apparently indicates shininess.
                 */
                result = "Models/Sinbad/Sinbad.mesh.xml";
                break;

            case "Sword":
                result = "Models/Sinbad/Sword.mesh.xml";
                break;

            case "TeapotObj":
                result = "Models/Teapot/Teapot.obj";
                break;

            case "TeapotXml":
                result = "Models/Teapot/Teapot.mesh.xml";
                break;

            case "Terrain":
                result = "Models/Terrain/Terrain.mesh.xml";
                break;

            case "TexturedPlane": // removed from v3.4.0
                result = "Blender/2.4x/texturedPlaneTest.blend";
                break;

            case "Textures": // removed from v3.4.0
                result = "Blender/2.4x/textures.blend";
                break;

            case "Tree":
                // TODO Assimp doesn't recognize materials in .j3m files.
                result = "Models/Tree/Tree.mesh.xml";
                break;

            case "TwoChairs":
                // added in v3.4.0
                result = "OBJLoaderTest/TwoChairs.obj";
                break;

            case "WaterTest":
                result = "Models/WaterTest/WaterTest.mesh.xml";
                break;

            default:
                return null;
        }

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
     * Enumerate the asset names.
     *
     * @return a pre-existing array of asset names (not null, all elements
     * non-null, in ascending lexicographic order)
     */
    @Override
    public String[] listAssets() {
        return namesArray;
    }

    /**
     * Return the asset root for the specified asset.
     *
     * @param assetName the name of the asset (not null)
     * @return a filesystem path (not null, not empty)
     */
    @Override
    public String rootPath(String assetName) {
        return rootPath;
    }
}
