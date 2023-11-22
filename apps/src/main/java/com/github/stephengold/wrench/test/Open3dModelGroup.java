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
 * AssetGroup for assets downloaded from Open3DModel.com .
 *
 * https://open3dmodel.com
 *
 * @author Stephen Gold sgold@sonic.net
 */
class Open3dModelGroup implements AssetGroup {
    // *************************************************************************
    // constants and loggers

    /**
     * message logger for this class
     */
    final private static Logger logger
            = Logger.getLogger(Open3dModelGroup.class.getName());
    // *************************************************************************
    // fields

    /**
     * true if accessible, otherwise false
     */
    final private boolean isAccessible;
    /**
     * names of the assets in ascending lexicographic order (not empty)
     */
    final private String[] namesArray;
    // *************************************************************************
    // constructors

    /**
     * Instantiate a group for Open3DModel.com downloads.
     */
    Open3dModelGroup() {
        // Test for overall accessibility:
        String testPath = "../downloads/Open3dModel/";
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

        // Populate the list of asset names:
        String[] fileNames = testDir.list();
        if (fileNames == null) {
            this.namesArray = null;
            return;
        }
        Set<String> nameSet = new TreeSet<>();
        for (String fileName : fileNames) {
            if (fileName.endsWith("_open3dmodel.zip")) {
                String name
                        = MyString.removeSuffix(fileName, "_open3dmodel.zip");
                nameSet.add(name);
            } else if (fileName.endsWith("_open3dmodel.com")) {
                String name
                        = MyString.removeSuffix(fileName, "_open3dmodel.com");
                nameSet.add(name);
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
        String fileName;
        switch (assetName) {
            case "106978": // Square Truss (File ID 476655)
                return "30-21-SquareTrussStraightSegment-21-3DS"
                        + "/SquareTrussStraightSegment_21_3ds.3DS";

            case "107149": // Bicycle Helmet (File ID 477012)
                return "helmet_v2_L3.123cb1572a1e-ec65-4957-9ef3-c05f51a2d45a"
                        + "/helmet.obj";

            case "11728": // Bengal Tiger (File ID 31882)
                // needs "%4$s.fbm/%2$s%3$s", animations garbled
                fileName = "open3dmodel.com/Models_E0106A028/Tiger.fbx";
                break;

            case "18348": // Black Cat (File ID 45161)
                fileName = "open3dmodel.com/Models_E0503A046/Black.fbx";
                break;

            case "2674": // Low Poly Deer (File ID 6932)
                fileName = "Deer.obj";
                break;

            case "2492": // House (File ID 5880) triggers JME issue #2135
                fileName = "House01/House01.obj";
                break;

            case "4141": // Disney Frozen Character (File ID 9633)
                fileName = "olafdidofranza/File/FBX/Studio Pose OLAF.fbx";
                break;

            case "460": // David Head Sculpture (File ID 2291)
                fileName = "Sculpture N181213.3DS";
                break;

            case "52929": // Gaming Mouse (File ID 295044)
                return "source/mouse.blend";

            case "53008": // UFO Spacecraft (File ID 295202)
                return "source/Bob Lazar UFO.blend";

            case "59008": // Mercedes Benz G500 (File ID 307292)
                return "Mercedes-Benz G500 W463 2008.dae";

            case "85122": // Spaceship Star Citizen (File ID 363518)
                return "source/ANVIL_ARROW2.stl";

            case "9244": // Human Body (File ID 20474)
                // 2 missing textures
                fileName = "1/3.3DS";
                break;

            default:
                return null;
        }
        String result = assetName + "_open3dmodel/" + fileName;

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
        String path = String.format(
                "../downloads/Open3dModel/%s_open3dmodel.zip", assetName);
        String fileSeparator = System.getProperty("file.separator");
        String result = path.replace("/", fileSeparator);

        File file = new File(result);
        if (!file.canRead()) {
            // Use the unzipped directory instead:
            int length = result.length();
            result = result.substring(0, length - 3) + "com/";
        }

        return result;
    }
}
