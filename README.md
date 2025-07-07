<img height="150" src="https://i.imgur.com/ivp31XQ.png" alt="MonkeyWrench Project logo">

[The MonkeyWrench Project][project] is developing
a software library to load 3-D assets into [JMonkeyEngine][jme].

It contains 2 subprojects:

1. lib: the MonkeyWrench [JVM] runtime library based on lwjgl-assimp
2. apps: related applications,
   including examples and non-automated test software

MonkeyWrench attempts to load 3-D models and animations
in a wide variety of formats, including:

+ [3D Studio Max (.3ds)][3ds]
+ [3-D Manufacturing Format (.3mf)][3mf]
+ [Blender] (.blend) *Support for this format is deprecated.*
+ [Biovision Hierarchy (.bvh)][bvh]
+ [COLLADA] (.dae)
+ [Autodesk Filmbox (.fbx)][fbx] *best for versions 7.1 through 7.4; other versions are unsupported*
+ [Khronos glTF (.glb and .gltf)][gltf], including Draco, version 1.0, and version 2.0
+ [LightWave Model (.lwo)][lwo]
+ [Ogre mesh (.mesh.xml)][ogre] (but not .scene files)
+ [Wavefront (.obj)][obj]
+ [Polygon File Format (.ply)][ply], both ASCII and binary
+ [Stereolithography (.stl)][stl], both ASCII and binary

Complete source code (in [Java]) is provided under
[a 3-clause BSD license][license].


<a name="toc"></a>

## Contents of this document

+ [How to add MonkeyWrench to an existing project](#add)
+ [How to build MonkeyWrench from source](#build)
+ [Related applications](#apps)
+ [Acknowledgments](#acks)


<a name="add"></a>

## How to add MonkeyWrench to an existing project

MonkeyWrench comes pre-built as a single library that depends on:
+ [LWJGL version 3][lwjgl],
+ lwjgl-assimp,
+ jme3-core,
+ jme3-desktop,
+ jme3-lwjgl3,
+ [imageio-tga][twelve],
+ [imageio-webp][twelve],
+ [Heart], and
+ [Wes].

(Since MonkeyWrench depends on jme3-desktop and jme3-lwjgl3,
it incompatible with LWJGL version 2 (jme3-lwjgl),
nor will it run on mobile platforms such as Android.
However, loaded assets can be saved as J3O for use in any JME application.)

Adding MonkeyWrench to an existing [jMonkeyEngine][jme] project
begins with ensuring that these libraries are on the classpath.

For projects built using [Maven] or [Gradle], it is sufficient to add a
dependency on the MonkeyWrench Library.
The build tool should automatically resolve the remaining dependencies.

### Gradle-built projects

Add to the project’s "build.gradle" or "build.gradle.kts" file:

    repositories {
        mavenCentral()
    }
    dependencies {
        implementation("com.github.stephengold:MonkeyWrench:1.0.0")
    }

For some older versions of Gradle,
it's necessary to replace `implementation` with `compile`.

### Maven-built projects

Add to the project’s "pom.xml" file:

    <repositories>
      <repository>
        <id>mvnrepository</id>
        <url>https://repo1.maven.org/maven2/</url>
      </repository>
    </repositories>

    <dependency>
      <groupId>com.github.stephengold</groupId>
      <artifactId>MonkeyWrench</artifactId>
      <version>1.0.0</version>
    </dependency>

### Configuring the asset manager

The MonkeyWrench loader class is named `LwjglAssetLoader`.

Once the classpath is configured, the next step is to
configure the application's `AssetManager`
to use `LwjglAssetLoader` in place of its default loaders.
In Java:

    import com.github.stephengold.wrench.LwjglAssetLoader;
    // ...
    assetManager.registerLoader(LwjglAssetLoader.class,
            "3ds", "3mf", "blend", "bvh", "dae", "fbx", "glb", "gltf",
            "lwo", "meshxml", "mesh.xml", "obj", "ply", "stl");

### Further considerations

#### Verbose logging

For more diagnostic information while loading assets,
invoke `loadModel()` on an `LwjglAssetKey` with verbose logging enabled.
In Java:

    LwjglAssetKey key = new LwjglAssetKey("Models/m/m.mesh.xml");
    key.setVerboseLogging(true);
    Spatial m = assetManager.loadModel(key);

#### Choice of file format

If the asset to be loaded is available in multiple file formats,
the best choice would be glTF version 2.0 (either .glb or .gltf format).
For best results, convert assets in .blend or .fbx format
to glTF _before_ loading them using MonkeyWrench.

The next-best choice would be Collada (.dae) format.

The most efficient format
for loading model assets into JMonkeyEngine is ".j3o".
Best practice is to convert assets to .j3o at build time, not during gameplay.

#### Structure of loaded assets

An asset loaded using MonkeyWrench might be structured differently from
the same asset loaded using jme3-core or jme3-plugins.
In particular:

+ MonkeyWrench always uses
  JMonkeyEngine's new animation system (com.jme3.anim package),
  not the old one (com.jme3.animation package).
  Jme-plugins still uses the old animation system when loading some assets.
+ Scene-graph controls (such as `AnimComposer` and `SkinningControl`)
  might be added to different spatials.
+ It might have a different number of nodes, joints, or mesh vertices.
+ Its spatials, materials, animation clips, joints, lights,
  and cameras might not have the same names or indices.

JMonkeyEngine applications
should minimize their assumptions about asset structure.

#### Finding external textures

Many 3-D models rely on textures
stored in separate files and referenced by name.
If the files get moved around or converted to different formats,
the filenames stored in the main asset may become outdated.
Even though the textures are available, the asset manager doesn't find them,
so it replaces them with placeholders.

To address this issue,
MonkeyWrench embeds a configurable `TextureLoader` in each `LwjglAssetKey`.
The `TextureLoader` manipulates texture filenames
to help MonkeyWrench find textures.

A `TextureLoader` consists of 2 parts:
+ a preliminary transformation, encoded as a `PathEdit` enum value and
+ a search path, consisting of one or more
  [format strings](https://docs.oracle.com/javase/8/docs/api/java/util/Formatter.html).

Before the preliminary transformation is applied, the texture filename
is URL encoded (converting each blank to "%20", for instance).

The preliminary transformation is either:
+ `NoOp` which does nothing or
+ `LastComponent` which, if the filename contains backslash,
  deletes everything up to and including the final backslash.

After the preliminary transformation is applied,
the filename is split into 2 parts:
+ the base name (the part before the final ".") and
+ the extension (the final "." and everything after it)

Finally, the `TextureLoader` attempts to load the texture
using the filenames generated by each format string in its search path.

In this context, format strings have the following semantics:
+ "%1$s" is replaced by
  the name of the folder from which the main asset was loaded
+ "%2$s" is replaced by the texture's base name
+ "%3$s" is replaced by the texture's extension
+ "%4$s" is replaced by the name of the main asset with its extension removed
+ "%5$s" is replaced by the texture's base name converted to lowercase

For example, if the main asset was loaded using

    TextureLoader textureLoader
            = new TextureLoader(PathEdit.LastComponent,
                    "%s%s%s",
                    "%stextures/%s.JPG",
                    "Textures/%2$s%3$s");
    LwjglAssetKey key = new LwjglAssetKey("source/diorama.fbx", textureLoader);

and the stored filename is "C:\My Documents\UV_Barn_Assets.psd", then

+ "%1$s" will be replaced by "source/",
+ "%2$s" will be replaced by "UV_Barn_Assets",
+ "%3$s" will be replaced by ".psd",
+ "%4$s" will be replaced by "source/diorama", and
+ "%5$s" will be replaced by "uv_barn_assets".

MonkeyWrench will try the following filenames, in sequence:

1. "source/UV_Barn_Assets.psd"
2. "source/textures/UV_Barn_Assets.JPG"
3. "Textures/UV_Barn_Assets.psd"

MonkeyWrench will generate a placeholder
only if no texture is found at any of those filenames.

[Jump to the table of contents](#toc)


<a name="build"></a>

## How to build MonkeyWrench from source

1. Install a [Java Development Kit (JDK)][adoptium],
   if you don't already have one.
2. Point the `JAVA_HOME` environment variable to your JDK installation:
   (In other words, set it to the path of a directory/folder
   containing a "bin" that contains a Java executable.
   That path might look something like
   "C:\Program Files\Eclipse Adoptium\jdk-17.0.3.7-hotspot"
   or "/usr/lib/jvm/java-17-openjdk-amd64/" or
   "/Library/Java/JavaVirtualMachines/zulu-17.jdk/Contents/Home" .)
  + using Bash or Zsh: `export JAVA_HOME="` *path to installation* `"`
  + using [Fish]: `set -g JAVA_HOME "` *path to installation* `"`
  + using Windows Command Prompt: `set JAVA_HOME="` *path to installation* `"`
  + using PowerShell: `$env:JAVA_HOME = '` *path to installation* `'`
3. Download and extract the MonkeyWrench source code from GitHub:
  + using [Git]:
    + `git clone https://github.com/stephengold/MonkeyWrench.git`
    + `cd MonkeyWrench`
    + `git checkout -b latest 1.0.0`
  + using a web browser:
    + browse to [the latest release][latest]
    + follow the "Source code (zip)" link
    + save the ZIP file
    + extract the contents of the saved ZIP file
    + `cd` to the extracted directory/folder
4. Run the [Gradle] wrapper:
  + using Bash or Fish or PowerShell or Zsh: `./gradlew build`
  + using Windows Command Prompt: `.\gradlew build`

After a successful build,
Maven artifacts will be found in "lib/build/libs".

You can install the artifacts to your local Maven repository:
+ using Bash or Fish or PowerShell or Zsh: `./gradlew install`
+ using Windows Command Prompt: `.\gradlew install`

You can restore the project to a pristine state:
+ using Bash or Fish or PowerShell or Zsh: `./gradlew clean cleanDownloads`
+ using Windows Command Prompt: `.\gradlew clean cleanDownloads`

[Jump to the table of contents](#toc)


<a name="apps"></a>

## Related applications

The following desktop applications are found in the "apps" subproject:

### ImportMixamo

A console application to import characters and animations from [Mixamo],
for use with JMonkeyEngine.

Before running it, download a single character (and any desired
animations for that character) to the "downloads/Mixamo/dae/" directory.

All assets should be downloaded in Collada (.dae) format.
Mixamo automatically archives each downloaded asset using "zip".

`ImportMixamo` expects each zip archive
to contain a .dae file with the same name.
If any downloaded assets get renamed (due to filename conflicts)
you'll need to unzip them, rename the .dae files, and re-zip them.
Other than that, it shouldn't be necessary to modify downloaded assets.

The character should be downloaded in "T-pose".
Each animation should be downloaded "in place" (if possible) and without
skin.

You can run the application from the command line:
+ using Bash or Fish or PowerShell or Zsh: `./gradlew ImportMixamo`
+ using Windows Command Prompt: `.\gradlew ImportMixamo`

Afterwards, the imported assets should be in the
"apps/Written Assets" directory.

### CompareLoaders

A graphical tool for comparing MonkeyWrench
to the asset loaders built into JMonkeyEngine.

You can run it from the command line:
+ using Bash or Fish or PowerShell or Zsh: `./gradlew CompareLoaders`
+ using Windows Command Prompt: `.\gradlew CompareLoaders`

#### Test data

The first time CompareLoaders is run,
the buildscript downloads about 230 MBytes of test data from maven.org .
On subsequent runs, startup should go more quickly.

The application also looks for (optional) test data in
"../ext/assimp-mdb/model-db" relative to the project root.
These data (about 10 MBytes) can be installed using Bash and [Git]:
+ `mkdir -p ../ext`
+ `cd ../ext`
+ `git clone https://github.com/assimp/assimp-mdb.git`
+ `cd ../CompareLoaders`

The application also looks for (optional) test data in
"../ext/glTF-Sample-Assets" relative to the project root.
These data (about 1.9 GBytes) can be installed using Bash and [Git]:
+ `mkdir -p ../ext`
+ `cd ../ext`
+ `git clone https://github.com/KhronosGroup/glTF-Sample-Assets.git`
+ `cd ../CompareLoaders`

The application also looks for (optional) test data in
"../ext/glTF-Sample-Models" relative to the project root.
These data (about 3.7 GBytes) can be installed using Bash and [Git]:
+ `mkdir -p ../ext`
+ `cd ../ext`
+ `git clone https://github.com/KhronosGroup/glTF-Sample-Models.git`
+ `cd ../CompareLoaders`

You can also install (optional) test data (about 890 MBytes)
from the Amazon Lumberyard:
+ using Bash or Fish or PowerShell or Zsh: `./gradlew bistro`
+ using Windows Command Prompt: `.\gradlew bistro`

#### Test status

The application opens a window and displays status using 8 lines of text
in its upper left corner.

<img height="400" src="https://i.imgur.com/Xl1luE4.png"
  alt="Rectangular window with a light blue background
       showing 2 green-skinned humanoids, 3 axis arrows, and 8 lines of text
       (7 on the left and one on the right)">

At any given time, one text line is selected, indicated by a yellow arrow.
Lines that can be selected are hereafter referred to as _fields_.

The status lines indicate:
+ which asset loader(s) are selected:
  + "Default" for jme3-core, jme3-blender, and jme3-plugins
  + "Lwjgl" for MonkeyWrench
  + "LwjglVerbose" for MonkeyWrench with verbose logging enabled
  + "SideBySide" to compare "Default" and "Lwjgl" side-by-side
+ which asset group is selected
+ which asset in the group is selected for loading
+ which animation (if any) in the loaded assets (if any) is running
+ which material(s) in the loaded assets (if any) are rendered
+ the brightness of the ambient light source
+ whether hardware (GPU) skinning is used

#### User controls

The CompareLoaders application is designed for use with a keyboard and mouse.

The documentation below assumes a keyboard with the "US" (QWERTY) layout.
On keyboards with other layouts, some keys may be labeled differently.
Refer to the help overlay (F1) for localized key labels.

##### Selecting fields and values

+ DownArrow or Numpad2 : selects the next field (cyclic)
+ UpArrow or Numpad8 : selects the previous field (cyclic)
+ Tab or "=" or Numpad6 : selects the next value for the selected field (cyclic)
+ Backspace or "-" or Numpad4 :
  selects the previous value for the selected field (cyclic)
+ Numpad7 : sets the selected field back by 7 values (cyclic)
+ Numpad9 : advances the selected field by 7 values (cyclic)

##### Running tests

+ L or Return or Numpad5 : loads the selected asset using the selected loader
+ N : advances to the next animation (if any) in the loaded asset (if any)

##### Controlling the camera (viewpoint)

+ W and S : dollies the camera forward and backward, respectively
+ A and D : strafes the camera left and right, respectively
+ Q and Z : moves the camera up and down, respectively
+ LeftArrow and RightArrow :
  causes the camera to orbit left and right, respectively
+ R or F7 : resets the camera to its initial position
+ F8 : toggles between orthographic and perspective views

To rotate the camera, drag with the left mouse button (LMB).

To zoom the camera, turn the scroll wheel.

##### Other useful keys

+ H or F1: toggles the help overlay between minimized and full-sized versions
+ Esc : closes the window and ends the application
+ P : prints the scene graph to standard output
+ Shift+P : prints a more detailed description of the scene graph
+ Ctrl+P : prints an even more detailed description of the scene graph
+ Spacebar : toggles the world axes between visible and hidden
+ V or F3: toggles the armatures (if any) between visible and hidden
+ "." or Pause : toggles the loaded animation (if any) between paused and running
+ C : print details about the default `Camera` (viewpoint) to standard output
+ M : print details heap usage (memory) to standard output

### TestIssue5232

A console app to reproduce [Assimp issue 5232](https://github.com/assimp/assimp/issues/5232).

### TestIssue5253

A console app to reproduce [Assimp issue 5253](https://github.com/assimp/assimp/issues/5253).

### TestIssue5289

A console app to reproduce [Assimp issue 5289](https://github.com/assimp/assimp/issues/5289).

### TestIssue5303

A console app to test for [Assimp issue 5303](https://github.com/assimp/assimp/issues/5303).

[Jump to the table of contents](#toc)


<a name="acks"></a>

## Acknowledgments

Wyatt Gillette (a.k.a capdevon) kindly contributed
the "AssimpProcessFlag.java" file (pull request #4).

Like most projects, MonkeyWrench builds on the work of many who
have gone before.  I therefore acknowledge
the creators of (and contributors to) the following software:

+ the [Open Asset Importer Library][assimp]
+ the [Checkstyle] tool
+ the [Firefox] web browser
+ the [Git] revision-control system and GitK commit viewer
+ the [GitKraken] client
+ the [Gradle] build tool
+ the [IntelliJ IDEA][idea] and [NetBeans] integrated development environments
+ the [Java] compiler, standard doclet, and runtime environment
+ [jMonkeyEngine][jme] and the jME3 Software Development Kit
+ the [Linux Mint][mint] operating system
+ [LWJGL], the Lightweight Java Game Library
+ the [Markdown] document-conversion tool
+ the [Meld] visual merge tool
+ Microsoft Windows
+ the [TwelveMonkeys ImageIO library][twelve]

I am grateful to [GitHub], [Sonatype], and [Imgur]
for providing free hosting for this project
and many other open-source projects.

I'm also grateful to my dear Holly, for keeping me sane.

If I've misattributed anything or left anyone out, please let me know, so I can
correct the situation: sgold@sonic.net

[Jump to the table of contents](#toc)


[3ds]: https://en.wikipedia.org/wiki/Autodesk_3ds_Max "Autodesk 3ds Max"
[3mf]: https://3mf.io/ "The 3MF Consortium"
[acorus]: https://stephengold.github.io/Acorus "Acorus Project"
[adoptium]: https://adoptium.net/releases.html "Adoptium Project"
[assimp]: https://www.assimp.org/ "The Open Asset Importer Library"
[bvh]: https://en.wikipedia.org/wiki/Biovision_Hierarchy "Biovision Hierachy"
[blender]: https://docs.blender.org "Blender Project"
[checkstyle]: https://checkstyle.org "Checkstyle"
[collada]: https://en.wikipedia.org/wiki/COLLADA "COLLADA file format"
[fbx]: https://en.wikipedia.org/wiki/FBX "Autodesk FBX file format"
[firefox]: https://www.mozilla.org/en-US/firefox "Firefox"
[fish]: https://fishshell.com/ "Fish command-line shell"
[git]: https://git-scm.com "Git"
[github]: https://github.com "GitHub"
[gitkraken]: https://www.gitkraken.com "GitKraken client"
[gltf]: https://www.khronos.org/gltf "glTF Project"
[gradle]: https://gradle.org "Gradle Project"
[heart]: https://github.com/stephengold/Heart "Heart Project"
[idea]: https://www.jetbrains.com/idea/ "IntelliJ IDEA"
[imgur]: https://imgur.com/ "Imgur"
[java]: https://en.wikipedia.org/wiki/Java_(programming_language) "Java programming language"
[jme]: https://jmonkeyengine.org "jMonkeyEngine Project"
[jvm]: https://en.wikipedia.org/wiki/Java_virtual_machine "Java virtual machine"
[latest]: https://github.com/stephengold/MonkeyWrench/releases/latest "latest release"
[license]: https://github.com/stephengold/MonkeyWrench/blob/master/LICENSE "MonkeyWrench license"
[log]: https://github.com/stephengold/MonkeyWrench/blob/master/lib/release-log.md "release log"
[lwjgl]: https://www.lwjgl.org "Lightweight Java Game Library"
[lwo]: https://lightwave3d.com/assets/plugins/entry/lwo3-loader/ "LightWave file format"
[markdown]: https://daringfireball.net/projects/markdown "Markdown Project"
[maven]: https://maven.apache.org "Maven Project"
[meld]: https://meldmerge.org "Meld merge tool"
[mint]: https://linuxmint.com "Linux Mint Project"
[mixamo]: https://www.mixamo.com "Mixamo Project"
[netbeans]: https://netbeans.org "NetBeans Project"
[obj]: https://en.wikipedia.org/wiki/Wavefront_.obj_file "Wavefront OBJ file format"
[ogre]: http://www.ogre3d.org "Ogre Project"
[ply]: https://en.wikipedia.org/wiki/PLY_(file_format) "PLY file format"
[project]: https://github.com/stephengold/MonkeyWrench "MonkeyWrench Project"
[sonatype]: https://www.sonatype.com "Sonatype"
[stl]: https://en.wikipedia.org/wiki/STL_(file_format) "STL file format"
[twelve]: https://github.com/haraldk/TwelveMonkeys "Twelve Monkeys"
[wes]: https://github.com/stephengold/Wes "Wes Project"
