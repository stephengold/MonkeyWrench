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
package com.github.stephengold.wrench.example;

import com.github.stephengold.wrench.LwjglAssetKey;
import com.github.stephengold.wrench.LwjglAssetLoader;
import com.github.stephengold.wrench.LwjglReader;
import com.github.stephengold.wrench.test.AssetGroup;
import com.github.stephengold.wrench.test.MixamoData;
import com.jme3.anim.AnimClip;
import com.jme3.anim.AnimComposer;
import com.jme3.anim.Armature;
import com.jme3.app.state.AppState;
import com.jme3.asset.TextureKey;
import com.jme3.export.FormatVersion;
import com.jme3.scene.Mesh;
import com.jme3.scene.Spatial;
import com.jme3.scene.plugins.bvh.SkeletonMapping;
import com.jme3.system.JmeContext;
import com.jme3.texture.Image;
import com.jme3.texture.Texture;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.awt.image.RenderedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Scanner;
import java.util.logging.Level;
import java.util.logging.Logger;
import jme3utilities.Heart;
import jme3utilities.MyMesh;
import jme3utilities.MySkeleton;
import jme3utilities.MySpatial;
import jme3utilities.MyString;
import jme3utilities.ui.ActionApplication;
import jme3utilities.ui.Locators;
import jme3utilities.wes.AnimationEdit;

/**
 * A headless ActionApplication to convert downloaded Mixamo assets into
 * JMonkeyEngine assets.
 * <p>
 * Before running this application, download a single character (and any desired
 * animations for that character) to the "../downloads/Mixamo/dae/" directory.
 * All downloaded assets should be in Collada (.dae) format. It shouldn't be
 * necessary to unzip them.
 * <p>
 * The character should be downloaded in "T-pose".
 * <p>
 * Each animation should be downloaded "in place" (if possible) and without
 * skin.
 * <p>
 * After running the application, the imported assets should be in the "Written
 * Assets" directory.
 *
 * @author Stephen Gold sgold@sonic.net
 */
final class ImportMixamo extends ActionApplication {
    // *************************************************************************
    // constants and loggers

    /**
     * message logger for this class
     */
    final private static Logger logger
            = Logger.getLogger(ImportMixamo.class.getName());
    // *************************************************************************
    // constructors

    /**
     * Instantiate an ActionApplication without any initial appstates.
     */
    private ImportMixamo() {
        super((AppState[]) null);
    }
    // *************************************************************************
    // new methods exposed

    /**
     * Main entry point for the ImportMixamo application.
     *
     * @param arguments array of command-line arguments (not null)
     */
    public static void main(String[] arguments) {
        // Mute the chatty loggers found in some imported packages:
        Logger.getLogger("jme3utilities.NamedAppState").setLevel(Level.WARNING);
        Logger.getLogger("jme3utilities.ui.DefaultInputMode")
                .setLevel(Level.SEVERE);

        // Instantiate the application.
        ImportMixamo application = new ImportMixamo();

        // Designate the sandbox directory where assets will be written:
        try {
            ActionApplication.designateSandbox("./Written Assets");
        } catch (IOException exception) {
            throw new RuntimeException(exception);
        }

        application.start(JmeContext.Type.Headless);
    }
    // *************************************************************************
    // ActionApplication methods

    /**
     * Initialize this application.
     */
    @Override
    public void acorusInit() {
        String mwVersion = LwjglReader.version();
        System.out.printf(
                "Using version %s of the MonkeyWrench library%n", mwVersion);

        String assimpGitHash = loadResourceAsString(
                "/META-INF/linux/x64/org/lwjgl/assimp/libassimp.so.git");
        System.out.printf(
                "Using Assimp (Git Hash: %s)%n", assimpGitHash.substring(0, 7));

        assetManager.registerLoader(LwjglAssetLoader.class, "dae");

        AssetGroup group = new MixamoData("dae");
        if (!group.isAccessible()) {
            logger.severe("Mixamo assets are not accessible! Quitting...");
            stop();
            return;
        }

        Spatial characterRoot = null;
        String characterName = null;
        String[] assetNames = group.listAssets();
        if (assetNames == null) {
            logger.severe("No Mixamo assets found! Quitting...");
            stop();
            return;
        }
        int numAssets = assetNames.length;
        List<Spatial> animationNodes = new ArrayList<>(numAssets);
        List<String> clipNames = new ArrayList<>(numAssets);

        // Load every asset found in "../downloads/Mixamo/dae/":
        for (String assetName : assetNames) {
            setupAssetLocators(group, assetName);

            String assetPath = group.assetPath(assetName);
            LwjglAssetKey key = new LwjglAssetKey(assetPath);
            //key.setVerboseLogging(true);

            Spatial assetRoot = assetManager.loadModel(key);

            String quotedName = MyString.quote(assetName);
            int numVertices = MySpatial.countVertices(assetRoot);
            if (numVertices > 0) {
                System.out.println(" loaded character " + quotedName);
                if (characterRoot != null) {
                    logger.severe("Multiple characters found! Quitting...");
                    stop();
                    return;
                }
                characterRoot = assetRoot;
                characterName = assetName;
            } else {
                System.out.println(" loaded animation " + quotedName);
                animationNodes.add(assetRoot);
                clipNames.add(assetName);
            }
        }

        if (characterRoot == null) {
            logger.severe("No character found! Quitting...");
            stop();
            return;
        }
        /*
         * Relocate the character's textures to "Models/Mixamo/..." and the
         * write their underlying images to the Acorus sandbox:
         */
        String pathPrefix = "Models/Mixamo/" + characterName + "/";
        List<Texture> textures = MySpatial.listTextures(characterRoot, null);
        for (Texture texture : textures) {
            relocate(texture, pathPrefix);
            writeImage(texture);
        }

        // Find the character's Armature and AnimComposer:
        List<Armature> armatures
                = MySkeleton.listArmatures(characterRoot, null);
        assert armatures.size() == 1;
        Armature characterArmature = armatures.get(0);

        List<AnimComposer> composers = MySpatial.listControls(
                characterRoot, AnimComposer.class, null);
        assert composers.size() == 1;
        AnimComposer characterComposer = composers.get(0);

        // Add the downloaded animation clips to the composer:
        int numAnimations = clipNames.size();
        for (int animationI = 0; animationI < numAnimations; ++animationI) {
            Spatial assetRoot = animationNodes.get(animationI);

            armatures = MySkeleton.listArmatures(assetRoot, null);
            assert armatures.size() == 1;
            Armature armature = armatures.get(0);

            composers = MySpatial.listControls(
                    assetRoot, AnimComposer.class, null);
            assert composers.size() == 1;
            AnimComposer composer = composers.get(0);

            Collection<AnimClip> clips = composer.getAnimClips();
            assert clips.size() == 1;
            AnimClip sourceClip = Heart.first(clips);

            SkeletonMapping map = new SkeletonMapping(armature);
            String clipName = clipNames.get(animationI);
            AnimClip retargeted = AnimationEdit.retargetAnimation(
                    sourceClip, armature, characterArmature, map, clipName);
            characterComposer.addAnimClip(retargeted);
        }

        // Save the resulting C-G model in J3O format:
        String savePath = ActionApplication.filePath(pathPrefix + "scene.j3o");
        Heart.writeJ3O(savePath, characterRoot);

        printSummary(characterRoot, characterComposer);

        stop();
    }
    // *************************************************************************
    // private methods

    /**
     * Load UTF-8 text from the named resource. TODO use the Heart library
     *
     * @param resourceName the name of the classpath resource to load (not null)
     * @return the text (possibly multiple lines)
     */
    private static String loadResourceAsString(String resourceName) {
        InputStream inputStream
                = ImportMixamo.class.getResourceAsStream(resourceName);
        if (inputStream == null) {
            String q = MyString.quote(resourceName);
            throw new RuntimeException("resource not found:  " + q);
        }

        // Parse the stream's data into one long text string.
        String charsetName = StandardCharsets.UTF_8.name();
        String result;
        try (Scanner scanner = new Scanner(inputStream, charsetName)) {
            scanner.useDelimiter("\\Z");
            result = scanner.next();
        }

        return result;
    }

    /**
     * Determine the color of a single pixel in a JME image.
     *
     * @param format the image format (not null, limited subset)
     * @param bytes the pixel's ByteBuffer data (not null, unaffected)
     * @return a new Color
     */
    private static Color pixelColor(Image.Format format, int[] bytes) {
        float a; // alpha channel
        float b; // blue channel
        float g; // green channel
        float r; // red channel
        int numBytes = bytes.length;

        switch (format) {
            case ABGR8:
                assert numBytes == 4 : numBytes;
                a = bytes[0] / 255f;
                b = bytes[1] / 255f;
                g = bytes[2] / 255f;
                r = bytes[3] / 255f;
                break;

            case ARGB8:
                assert numBytes == 4 : numBytes;
                a = bytes[0] / 255f;
                r = bytes[1] / 255f;
                g = bytes[2] / 255f;
                b = bytes[3] / 255f;
                break;

            case Alpha8:
                assert numBytes == 1 : numBytes;
                a = bytes[0] / 255f;
                b = g = r = 1f;
                break;

            case BGR8:
                assert numBytes == 3 : numBytes;
                b = bytes[0] / 255f;
                g = bytes[1] / 255f;
                r = bytes[2] / 255f;
                a = 1f;
                break;

            case BGRA8:
                assert numBytes == 4 : numBytes;
                b = bytes[0] / 255f;
                g = bytes[1] / 255f;
                r = bytes[2] / 255f;
                a = bytes[3] / 255f;
                break;

            case Luminance8:
                assert numBytes == 1 : numBytes;
                b = g = r = bytes[0] / 255f;
                a = 1f;
                break;

            case Luminance8Alpha8:
                assert numBytes == 2 : numBytes;
                b = g = r = bytes[0] / 255f;
                a = bytes[1] / 255f;
                break;

            case RGB8:
                assert numBytes == 3 : numBytes;
                r = bytes[0] / 255f;
                g = bytes[1] / 255f;
                b = bytes[2] / 255f;
                a = 1f;
                break;

            case RGBA8:
                assert numBytes == 4 : numBytes;
                r = bytes[0] / 255f;
                g = bytes[1] / 255f;
                b = bytes[2] / 255f;
                a = bytes[3] / 255f;
                break;

            // TODO handle more formats
            default:
                String message = "format = " + format;
                throw new IllegalArgumentException(message);
        }

        Color result = new Color(r, g, b, a);
        return result;
    }

    /**
     * Summarize of the specified model asset to {@code System.out}.
     *
     * @param modelRoot the model's root spatial (not null, unaffected)
     * @param composer the model's AnimComposer (not null, unaffected)
     */
    private static void printSummary(
            Spatial modelRoot, AnimComposer composer) {
        List<Mesh> meshList = MyMesh.listMeshes(modelRoot, null);
        int numMeshes = meshList.size();

        Collection<AnimClip> clips = composer.getAnimClips();
        int numClips = clips.size();

        int numVertices = MySpatial.countVertices(modelRoot);

        System.err.flush();
        System.out.printf("version-%d J3O model asset with %d mesh%s, "
                + "%d animation clip%s, and %d vert%s%n", FormatVersion.VERSION,
                numMeshes, (numMeshes == 1) ? "" : "es",
                numClips, (numClips == 1) ? "" : "s",
                numVertices, (numVertices == 1) ? "ex" : "ices");
    }

    /**
     * Relocate the specified texture to a new asset path.
     *
     * @param texture the texture to be relocated (not null)
     * @param pathPrefix text to prepend to the texture's asset path (not null)
     */
    private static void relocate(Texture texture, String pathPrefix) {
        TextureKey key = (TextureKey) texture.getKey();
        String assetPath = key.getName();
        String newPath = pathPrefix + assetPath;

        boolean flipY = key.isFlipY();
        int anisotropy = key.getAnisotropy();
        boolean generateMips = key.isGenerateMips();
        Texture.Type hint = key.getTextureTypeHint();

        TextureKey newKey = new TextureKey(newPath, flipY);
        newKey.setAnisotropy(anisotropy);
        newKey.setGenerateMips(generateMips);
        newKey.setTextureTypeHint(hint);

        texture.setKey(newKey);
    }

    /**
     * Set up asset locators for the specified group and asset.
     *
     * @param group the asset group to use (not null)
     * @param assetName the name of an asset in the group (not null)
     */
    private static void setupAssetLocators(AssetGroup group, String assetName) {
        Locators.unregisterAll();

        String rootPath = group.rootPath(assetName);
        assert rootPath != null;
        rootPath = Heart.fixPath(rootPath);
        Locators.registerFilesystem(rootPath);

        // A classpath locator is needed for J3MDs and such:
        Locators.registerDefault();
    }

    /**
     * Convert a JME image to an AWT image.
     *
     * @param imageIn the input Image (not null, 2-D, single buffer, limited
     * subset of formats, unaffected)
     * @param flipY true&rarr;flip the Y coordinate, false&rarr;don't flip
     * @param awtType the desired output format, such as
     * BufferedImage.TYPE_4BYTE_ABGR
     * @return a new BufferedImage
     */
    private static BufferedImage toBufferedImage(
            Image imageIn, boolean flipY, int awtType) {
        int depth = imageIn.getDepth();
        assert depth <= 1 : depth; // 3-D images aren't handled

        int numBuffers = imageIn.getData().size();
        assert numBuffers == 1 : numBuffers; // multiple buffers aren't handled

        Image.Format format = imageIn.getFormat();
        int bpp = format.getBitsPerPixel();
        assert (bpp % 8) == 0 : bpp; // pixels must be a whole number of bytes
        int bytesPerPixel = bpp / 8;

        int height = imageIn.getHeight();
        int width = imageIn.getWidth();
        int numBytes = height * width * bytesPerPixel;
        ByteBuffer byteBuffer = imageIn.getData(0);
        int capacity = byteBuffer.capacity();
        assert capacity >= numBytes : "capacity = " + capacity
                + ", numBytes = " + numBytes;

        BufferedImage result = new BufferedImage(width, height, awtType);
        int[] pixelBytes = new int[bytesPerPixel];
        Graphics2D graphics = result.createGraphics();
        int byteOffset = 0;
        for (int yIn = 0; yIn < height; ++yIn) {
            int yOut;
            if (flipY) {
                yOut = height - 1 - yIn;
            } else {
                yOut = yIn;
            }

            for (int x = 0; x < width; ++x) {
                for (int byteI = 0; byteI < bytesPerPixel; ++byteI) {
                    int pixelByte = byteBuffer.get(byteOffset);
                    pixelBytes[byteI] = 0xff & pixelByte;
                    ++byteOffset;
                }
                Color color = pixelColor(format, pixelBytes);
                graphics.setColor(color);
                graphics.fillRect(x, yOut, 1, 1);
            }
        }

        return result;
    }

    /**
     * Use AWT to write the image of the specified texture to the sandbox.
     *
     * @param texture the texture to write (not null)
     */
    private static void writeImage(Texture texture) {
        TextureKey key = (TextureKey) texture.getKey();
        String assetPath = key.getName();

        int awtType = BufferedImage.TYPE_4BYTE_ABGR;
        String lowerCase = assetPath.toLowerCase();
        if (lowerCase.endsWith(".bmp")
                || lowerCase.endsWith(".jpg")
                || lowerCase.endsWith(".jpeg")) {
            awtType = BufferedImage.TYPE_3BYTE_BGR;
        }

        Image image = texture.getImage();
        boolean flipY = key.isFlipY();
        RenderedImage renderedImage = toBufferedImage(image, flipY, awtType);

        String filePath = filePath(assetPath);
        try {
            Heart.writeImage(filePath, renderedImage);
        } catch (IOException exception) {
            throw new RuntimeException(exception);
        }
    }
}
