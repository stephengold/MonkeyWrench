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
import java.util.Set;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.logging.Logger;
import jme3utilities.MyString;

/**
 * AssetGroup for a subset of the Assimp model database.
 * <p>
 * https://github.com/assimp/assimp-mdb
 *
 * @author Stephen Gold sgold@sonic.net
 */
class AssimpMdb implements AssetGroup {
    // *************************************************************************
    // constants and loggers

    /**
     * message logger for this class
     */
    final private static Logger logger
            = Logger.getLogger(AssimpMdb.class.getName());
    // *************************************************************************
    // fields

    /**
     * true if accessible, otherwise false
     */
    final private boolean isAccessible;
    /**
     * which file format ("3mf", "blender", "fbx", "glTF2", or "Obj")
     */
    final private String format;
    /**
     * filesystem path to the asset root
     */
    private String rootPath;
    /**
     * names of the assets in ascending lexicographic order (not empty)
     */
    final private String[] namesArray;
    // *************************************************************************
    // constructors

    /**
     * Instantiate a group for the specified file format.
     *
     * @param format which file format ("3mf", "blender", "fbx", "glTF2", or
     * "Obj")
     */
    AssimpMdb(String format) {
        this.format = format;

        // Test for overall accessibility:
        this.rootPath
                = String.format("../../ext/assimp-mdb/model-db/%s/", format);
        String fileSeparator = System.getProperty("file.separator");
        this.rootPath = rootPath.replace("/", fileSeparator);
        File testDir = new File(rootPath);
        this.isAccessible = testDir.isDirectory() && testDir.canRead();
        if (!isAccessible) {
            String cwd = System.getProperty("user.dir");
            logger.log(Level.WARNING, "{0} is not accessible from {1}.",
                    new Object[]{
                        MyString.quote(rootPath), MyString.quote(cwd)
                    });
        }

        // Populate the array of asset names:
        String[] fileNames = testDir.list();
        if (fileNames == null) {
            this.namesArray = null;
            return;
        }
        Set<String> nameSet = new TreeSet<>();
        for (String fileName : fileNames) {
            File file = new File(testDir, fileName);
            if (file.isDirectory() && file.canRead()) {
                if (fileName.equals("SeaLife_Rigged")) {
                    nameSet.add("Green_Sea_Turtle");
                    nameSet.add("Manta_Ray");
                } else {
                    nameSet.add(fileName);
                }
            } else {
                int charPosition = fileName.indexOf('.');
                if (charPosition >= 0) {
                    String name = fileName.substring(0, charPosition);
                    nameSet.add(name);
                }
            }
        }
        int numNames = nameSet.size();
        if (numNames == 0) {
            this.namesArray = null;
            return;
        }

        this.namesArray = new String[numNames];
        nameSet.toArray(namesArray);
    }
    // *************************************************************************
    // AssetGroup methods

    /**
     * Return the path to the specified asset.
     *
     * @param assetName the name of the asset (not null)
     * @return the asset path, or null if the path is unknown
     */
    @Override
    public String assetPath(String assetName) {
        String result;
        switch (assetName) {
            case "box":
                result = "box.3mf";
                break;

            case "huesitos":
                result = "huesitos.fbx";
                break;

            case "Green_Sea_Turtle":
            case "Manta_Ray":
                result = "SeaLife_Rigged/" + assetName + ".fbx";
                break;

            case "simplerig_cube":
                result = assetName + "/SimpleRig_cube.fbx";
                break;

            case "simple_si_rigging":
                result = assetName + "/Simple_SI_Rigging_Done.fbx";
                break;

            case "sphere_blend_shape_animation": // Assimp issue #5300
                result = assetName + "/sphereBlendShapeAnimation.fbx";
                break;

            case "sphere_rig_advanced": // multiple bones named "Jnt_C_Root"
                result = assetName + "/sphere_rig_advanced.fbx";
                break;

            case "Spider":
                result = "Spider/spider.obj";
                break;

            case "spider":
                if (format.equals("3mf")) { // Assimp issue #5298
                    result = "spider.3mf";
                } else if (format.equals("glTF2")) {
                    result = "spider.glb";
                } else {
                    result = null;
                }
                break;

            case "StarDestroyer": // Assimp issue #5299
                result = assetName + "/star_destroyer.blend";
                break;

            case "Wuson":
                result = assetName + "/WusonOBJ.obj";
                break;

            default:
                result = null;
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
