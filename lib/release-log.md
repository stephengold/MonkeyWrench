# release log for the MonkeyWrench library

## Version 1.0.0 released on 5 May 2025

+ Added code to log warnings when metadata are ignored.
+ Updated LWJGL to v3.3.6, the Heart library to v9.2.0,
  and the JMonkeyEngine libraries to v3.8.0-stable .

## Version 0.6.2 released on 6 December 2024

+ Updated LWJGL to v3.3.3, the Heart library to v9.1.0, the ImageIO library to
  v3.12.0, and the JMonkeyEngine libraries to v3.7.0-stable .

## Version 0.6.1 released on 26 May 2024

+ Added code to convert node metadata (such as glTF extras) to JME user data.
+ Enabled logging node metadata when verbose logging is enabled.

## Version 0.6.0 released on 28 March 2024

+ Bugfix:  memory leak if `aiImportFileEx()` returns `null`
+ Bugfix:  `NullPointerException` in `modifyTextureCoordinates()`
+ Added the capability to convert embedded textures from TGA format.
+ Added logging to diagnose unsupported FBX format versions.
+ Defined semantics for "%4$s" and "%5$s" in texture-asset search paths.
+ Added `aiProcess_GenNormals` to the default post-processing options.
+ Publicized the `defaultFlags` constant in the `LwjglAssetKey` class.
+ Improved how specific Assimp material properties are handled, mostly
  by ignoring those properties.
+ Eliminated re-mapping of `WrapMode` encodings from the `Sampler` class.
+ Updated the Heart library to v9.0.0 and the Wes library to v0.8.1 .

## Version 0.5.3 released on 11 November 2023

+ Bugfix:  index out of range in `SkinningControl` with software skinning
+ Changed so that a BVH import won't try to generate a complete scene.
+ Updated the imageio-webp library to version 3.10.1 .

## Version 0.5.2 released on 8 November 2023

+ Bugfix: `NullPointerException` in `MaterialBuilder.modifyTextureCoordinates()`
+ Bugfix: `NumberFormatException` in `MaterialBuilder.toTexture()`
+ Bugfix: assertion failure while loading the "zophrac" model
+ Disabled verbose logging by default.
+ Implemented a content cache in the `AssetFileSystem` class.
+ Added a `TextureLoader` to each `LwjglAssetKey` instance,
  with a new getter and constructor.
+ Changed how texture asset paths are generated; now a format can override
  the texture's file extension.
+ Added the `TextureEdit` class and the `PathEdit` enum (both public).
+ Added support for embedded textures in WebP format.
+ Added support for materials that specify "flat" shading.
+ Enhanced the `MaterialBuilder` class to handle or ignore
  more Assimp material properties.
+ Changed to sort transparent geometries in the `Transparent` bucket.
+ Implemented recursive dumping of metadata.
+ Enhanced the diagnostics for when a non-embedded texture isn't found
  or when an embedded texture fails to load.
+ Added sanity checks for the dimensions of an embedded texture.
+ Clarified the verbose logging output.
+ Plugged some memory leaks.

## Version 0.5.1 released on 23 October 2023

+ Replaced the `LwjglAssetKey.setFlags()` method with a new
  constructor (API change)
+ Added lwjgl-assimp native libraries for platforms
  other than the build platform.
+ Downgraded LWJGL to v3.3.2 for compatibility with JME v3.6.1.
+ Began ignoring `TransformTrack` keyframes with negative animation times.
+ Added "Textures/" and "%sTextures/" to the texture search path.
+ Added switch cases to handle "$raw.EmissiveColor|" material properties.

## Version 0.5.0 released on 22 October 2023

This was the initial baseline release for testing.
