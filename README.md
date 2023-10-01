<img height="150" src="https://i.imgur.com/ivp31XQ.png" alt="MonkeyWrench Project logo">

[The MonkeyWrench Project][project] is developing
a software library to import 3-D models in various formats into [JMonkeyEngine][jme].

It contains 2 sub-projects:

1. lib: the MonkeyWrench runtime library
2. apps: non-automated test software

Complete source code (in [Java]) is provided under
[a 3-clause BSD license][license].

As of October 2023, the library is an unreleased work in progress
and not ready for production.


## How to build MonkeyWrench from source

1. Install a [Java Development Kit (JDK)][adoptium],
   if you don't already have one.
2. Point the `JAVA_HOME` environment variable to your JDK installation:
   (The path might be something like "C:\Program Files\Java\jre1.8.0_301"
   or "/usr/lib/jvm/java-8-openjdk-amd64/" or
   "/Library/Java/JavaVirtualMachines/liberica-jdk-17-full.jdk/Contents/Home" .)
  + using Bash or Zsh: `export JAVA_HOME="` *path to installation* `"`
  + using Windows Command Prompt: `set JAVA_HOME="` *path to installation* `"`
  + using PowerShell: `$env:JAVA_HOME = '` *path to installation* `'`
3. Download and extract the MonkeyWrench source code from GitHub:
  + using [Git]:
    + `git clone https://github.com/stephengold/MonkeyWrench.git`
    + `cd MonkeyWrench`
4. Run the [Gradle] wrapper:
  + using Bash or PowerShell or Zsh: `./gradlew build`
  + using Windows Command Prompt: `.\gradlew build`

After a successful build,
the library JAR will be found in "lib/build/libs".

You can restore the project to a pristine state:
+ using Bash or PowerShell or Zsh: `./gradlew clean`
+ using Windows Command Prompt: `.\gradlew clean`


[adoptium]: https://adoptium.net/releases.html "Adoptium Project"
[assimp]: https://www.assimp.org/ "The Open Asset Importer Library"
[git]: https://git-scm.com "Git"
[gradle]: https://gradle.org "Gradle Project"
[java]: https://en.wikipedia.org/wiki/Java_(programming_language) "Java programming language"
[jme]: https://jmonkeyengine.org "jMonkeyEngine Project"
[license]: https://github.com/stephengold/MonkeyWrench/blob/master/LICENSE "MonkeyWrench license"
[lwjgl]: https://www.lwjgl.org "Lightweight Java Game Library"
[project]: https://github.com/stephengold/MonkeyWrench "MonkeyWrench Project"
