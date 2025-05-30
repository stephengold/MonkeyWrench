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
 * AssetGroup for assets downloaded from Sketchfab.
 * <p>
 * https://sketchfab.com/
 *
 * @author Stephen Gold sgold@sonic.net
 */
class SketchfabData implements AssetGroup {
    // *************************************************************************
    // constants and loggers

    /**
     * message logger for this class
     */
    final private static Logger logger
            = Logger.getLogger(SketchfabData.class.getName());
    // *************************************************************************
    // fields

    /**
     * true if accessible, otherwise false
     */
    final private boolean isAccessible;
    /**
     * file extension for assets (".gltf", for example)
     */
    final private String fileExtension;
    /**
     * for generating the filesystem path to an asset root, with forward slashes
     */
    final private String rootPathFormat;
    /**
     * names of the assets in ascending lexicographic order (not empty)
     */
    final private String[] namesArray;
    // *************************************************************************
    // constructors

    /**
     * Instantiate a group for the specified file format.
     *
     * @param format which file format ("3ds", "blend", "dae", "fbx", "glb",
     * "glTF", or "obj")
     */
    SketchfabData(String format) {
        this(format, false);
    }

    /**
     * Instantiate a group for the specified file format.
     *
     * @param format which file format ("3ds", "blend", "dae", "fbx", "glb",
     * "glTF", or "obj")
     * @param converted false if assets are in their "original" format, true if
     * converted (default=false)
     */
    SketchfabData(String format, boolean converted) {
        String subdir = converted ? "converted" : "original";
        String testPath
                = "../downloads/Sketchfab/" + subdir + "/" + format + "/";
        switch (format) {
            case "3ds":
            case "blend":
            case "dae":
            case "fbx":
            case "obj":
                this.fileExtension = "." + format;
                this.rootPathFormat = testPath + "%s.zip";
                break;

            case "glb":
                this.fileExtension = ".glb";
                this.rootPathFormat = testPath;
                break;

            case "glTF":
                this.fileExtension = ".gltf";
                this.rootPathFormat = testPath + "%s.zip";
                break;

            default:
                throw new IllegalArgumentException("format = " + format);
        }

        // Test for overall accessibility:
        String fileSeparator = System.getProperty("file.separator");
        testPath = testPath.replace("/", fileSeparator);
        File testDir = new File(testPath);
        this.isAccessible = testDir.isDirectory() && testDir.canRead();
        if (!isAccessible) {
            String cwd = System.getProperty("user.dir");
            logger.log(Level.WARNING, "{0} is not accessible from {1}.",
                    new Object[]{
                        MyString.quote(testPath), MyString.quote(cwd)
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
            if (fileName.endsWith(".zip")) {
                String name = MyString.removeSuffix(fileName, ".zip");
                nameSet.add(name);
            } else if (fileName.endsWith(".glb")) {
                String name = MyString.removeSuffix(fileName, ".glb");
                nameSet.add(name);
            } else {
                File file = new File(testDir, fileName);
                if (file.isDirectory() && file.canRead()) {
                    nameSet.add(fileName);
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
     * @return the asset path, or null if the name is not found
     */
    @Override
    public String assetPath(String assetName) {
        if (fileExtension.equals(".gltf")) {
            String result;
            switch (assetName) {
                case "adamHead":
                case "lieutenantHead":
                    result = assetName + ".gltf";
                    // lieutenantHead glTF: TODO armature is incorrect
                    break;

                default:
                    // Usual path for assets converted to glTF is "scene.gltf":
                    result = "scene.gltf";
                // fat_motorcycle glTF: multiple bones named "Point"
                // honda_motorcycle glTF: multiple bones named "Lamp"
                // motorcycle_helmet glTF: TODO transparency issue
                // zophrac glTF: not handled by MonkeyWrench
            }
            return result;

        } else if (fileExtension.equals(".glb")) {
            return assetName + ".glb";
        }

        // original ".3ds", ".blend", ".dae", ".fbx", or ".obj":
        String fileName;
        switch (assetName) {
            case "2014-chevrolet-corvette-c7-stingray-rigged":
                // Blender version 3.03
                // TODO Why doesn't Assimp material use any of the textures?
                fileName = "2014 - Chevrolet Covette C7 Stingray - Rigged";
                break;

            case "9a-91-assault-rifle-gameready-lowpoly":
                // TODO Why does Assimp choose Phong shading?
                // TODO Why doesn't Assimp material use any of the textures?
                fileName = "9a91 anim ready";
                break;

            case "alien-food-cart":
                // missing texture "Puesto_Base_color.png"
                fileName = "Tienda_Lista";
                break;

            case "buster-drone": // FBX must be unzipped twice!
                // needs "textures/%5$s%3$s" in the texture search path
                fileName = "model/BusterDrone";
                break;

            case "chair":
                // TODO length of input data unexpected for ByPolygon mapping
                fileName = "Chair"; // by Mora
                break;

            case "crates-and-barrels":
                // missing texture "Cloth2_Base_color.png"
                fileName = "CratesAndBarrels";
                break;

            case "cute-robot": // multiple bones named "Foot_END_R"
                fileName = "Robot"; // by Mora
                break;

            case "delivery-hover-mp-1":
                // missing texture "MotoPizza V4_low_3_Food_set_3_BaseColor.png"
                fileName = "MotoPizza V5 (UnrealEngine)";
                break;

            case "hazmat-ussr-backrooms":
                // TODO textures not found (" " -> "_")
                fileName = "lox";
                break;

            case "house-and-forge":
                // TODO length of input data unexpected, but looks okay
                fileName = "Forge";
                break;

            case "human-skull-and-neck": // OBJ must unzip and then "7z e"
                fileName = "kallo_DM";
                break;

            case "ipad-mini-2023":
                fileName = "model";
                break;

            case "japanese-food-pack-sushi-free":
                fileName = "All";
                break;

            case "joystick-nes":
                fileName = "Control";
                break;

            case "legotm-creator-plane-4953":
                fileName = "aceaca0038464a169d30b6fef368f412.zip";
                break;

            case "little-duck":
                fileName = "Pollito";
                break;

            case "martial-artist":
                // GLB has multiple bones named "_rootJoint"
                // FBX has multiple bones named "head"
                // FBX crashes with free(): invalid pointer (issue 5289)
                fileName = "dd6e7fe67ccb4751a4f19052b38c8db4";
                break;

            case "maserati-mc20": // FBX must be unzipped twice!
                fileName = "Maserati MC20";
                break;

            case "microphone-gxl-066-bafhcteks": // DAE must be unzipped twice!
                fileName = "model/model";
                break;

            case "moon-doll":
                // TODO Why doesn't Assimp material use any of the textures?
                fileName = "Doll_Moon";
                break;

            case "neon-gun-v1": // TODO textures look wrong
                fileName = "neon_gun_v01";
                break;

            case "nes-controller-free": // FBX must be unzipped twice!
                fileName = "controller_wireless_1024";
                break;

            case "old-brick-warehouse":
                // needs "textures/%2$s.jpeg" in the texture search path
                fileName = "frtr";
                break;

            case "orchid-flower": // OBJ must be unzipped twice!
                fileName = "Orchid_Highpoly";
                break;

            case "phoenix-bird":
                // needs "textures/%2$s%3$s.png" in the texture search path
                // TODO armature is incorrect
                fileName = "fly";
                break;

            case "ramen": // FBX must be unzipped twice!
                return "source/Ramen.FBX"; // by cuadot.fbx

            case "rolls-royce-spectre":
                // fails to triangulate 2 polygons
                // TODO Why doesn't Assimp material use any of the textures?
                fileName = "Spectre";
                break;

            case "sci-fi-hanger": // FBX must be unzipped twice!
                fileName = "Sci-Fi Hanger/Sci-Fi Hanger";
                break;

            case "sci-fi-handgun-n01": // FBX must be unzipped twice!
                fileName = "gun";
                break;

            case "skull-sword":
                fileName = "Sword"; // by Mora
                break;

            case "steampunk-underwater-explorer": // OBJ must be unzipped twice!
                fileName = "Explorer";
                break;

            case "stesla-elephant-steam-engines":
                // FBX is missing texture "FlooplaneT.png"
                // GLB has multiple bones named "pasted__group"
                fileName = "f1b4f9a0dcbc46898cc358f52cdd34c0.fbx";
                break;

            case "street-asset-pack": // FBX is missing many textures
                fileName = "Street Prop Pack";
                break;

            case "tanto-knife-samurai-game-ready-asset":
                fileName = "model/model"; // DAE must be unzipped twice!
                break;

            case "the-strawberry-elephant":
                // FBX is missing texture "DELFIN_TEXTURA.tga"
                // TODO materials look too dark
                fileName = "Elephant"; // by Mora
                break;

            case "zophrac": // FBX is missing many textures
                fileName = "Gunan_animated";
                break;

            default:
                return null;
        }
        String result = "source/" + fileName + fileExtension;

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
        String path = String.format(rootPathFormat, assetName);
        String fileSeparator = System.getProperty("file.separator");
        String result = path.replace("/", fileSeparator);

        File file = new File(result);
        if (!file.canRead()) {
            if (rootPathFormat.endsWith(".zip")) {
                // Try the unzipped directory instead:
                int length = result.length();
                result = result.substring(0, length - 4);
            }
        }

        return result;
    }
}
