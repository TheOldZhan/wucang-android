# Third-party notices

The original WuCang application code is licensed under the MIT License in `LICENSE`.
Third-party components retain their own licenses; the project's MIT License does
not replace those licenses.

## Components in the Android application

The following versions were identified in the resolved `debug` package dependency
graph for WuCang 1.0.0. These components use the Apache License, Version 2.0.

| Component | Version | Upstream license |
| --- | --- | --- |
| `androidx.core:core` | 1.9.0 | [AndroidX Apache-2.0](https://github.com/androidx/androidx/blob/androidx-main/LICENSE.txt) |
| `androidx.exifinterface:exifinterface` | 1.3.7 | [AndroidX Apache-2.0](https://github.com/androidx/androidx/blob/androidx-main/LICENSE.txt) |
| `androidx.annotation:annotation-experimental` | 1.3.0 | [AndroidX Apache-2.0](https://github.com/androidx/androidx/blob/androidx-main/LICENSE.txt) |
| `androidx.annotation:annotation` | 1.2.0 | [AndroidX Apache-2.0](https://github.com/androidx/androidx/blob/androidx-main/LICENSE.txt) |
| `androidx.arch.core:core-common` | 2.1.0 | [AndroidX Apache-2.0](https://github.com/androidx/androidx/blob/androidx-main/LICENSE.txt) |
| `androidx.arch.core:core-runtime` | 2.1.0 | [AndroidX Apache-2.0](https://github.com/androidx/androidx/blob/androidx-main/LICENSE.txt) |
| `androidx.collection:collection` | 1.0.0 | [AndroidX Apache-2.0](https://github.com/androidx/androidx/blob/androidx-main/LICENSE.txt) |
| `androidx.concurrent:concurrent-futures` | 1.0.0 | [AndroidX Apache-2.0](https://github.com/androidx/androidx/blob/androidx-main/LICENSE.txt) |
| `androidx.lifecycle:lifecycle-common` | 2.3.1 | [AndroidX Apache-2.0](https://github.com/androidx/androidx/blob/androidx-main/LICENSE.txt) |
| `androidx.lifecycle:lifecycle-runtime` | 2.3.1 | [AndroidX Apache-2.0](https://github.com/androidx/androidx/blob/androidx-main/LICENSE.txt) |
| `androidx.versionedparcelable:versionedparcelable` | 1.1.1 | [AndroidX Apache-2.0](https://github.com/androidx/androidx/blob/androidx-main/LICENSE.txt) |
| `org.jetbrains.kotlin:kotlin-stdlib` | 2.2.10 | [Kotlin Apache-2.0](https://github.com/JetBrains/kotlin/blob/master/license/LICENSE.txt) |
| `org.jetbrains:annotations` | 13.0 | [JetBrains annotations Apache-2.0](https://github.com/JetBrains/java-annotations/blob/master/LICENSE.txt) |
| `com.google.guava:listenablefuture` | 1.0 | [Guava Apache-2.0](https://github.com/google/guava/blob/master/LICENSE) |

The resolved runtime JARs and AARs were inspected for separate `LICENSE`, `NOTICE`,
`COPYING`, and `COPYRIGHT` entries. No separate notice files were present in those
artifacts. Upstream copyright and license terms continue to apply.

## Gradle Wrapper distributed with the source

The Gradle 9.4.1 wrapper scripts and wrapper JAR are distributed under Apache-2.0.
The scripts retain their original copyright and license headers. The wrapper JAR
retains its original `META-INF/LICENSE`; the repository's full Apache-2.0 text was
copied byte-for-byte from that embedded license.

The Gradle distribution and Android build tools are downloaded separately when
building the project and are not included in the application APK.

## Included license texts

- Original application: `LICENSE` in the source archive and
  `assets/licenses/MIT.txt` in the APK.
- Apache License, Version 2.0: `licenses/Apache-2.0.txt` in the source archive and
  `assets/licenses/Apache-2.0.txt` in the APK.
- This component inventory: `THIRD_PARTY_NOTICES.md` in the source archive and
  `assets/licenses/THIRD_PARTY_NOTICES.txt` in the APK.

The APK license files are stored as ordinary Android assets and can also be read
by opening the APK as a ZIP archive.
