# Slim OpenCV (DS-95)

OpenCV 4.14.0 built from source with only the modules DocVault calls: `core`, `imgproc`, `imgcodecs` (JPEG only),
and the Java bindings. The ARM HALs (Carotene, KleidiCV) are kept for speed. ABIs: `arm64-v8a`, `armeabi-v7a`.

| | Maven `org.opencv:opencv:4.14.0` | This module |
|---|---|---|
| `libopencv_java4.so`, arm64-v8a | 24.7 MB | 9.4 MB |

The sources in `src/main/java` and the libraries in `src/main/jniLibs` are **generated — do not edit them**. To rebuild
(needs Android SDK `ndk;28.2.13676358` and `cmake;4.1.2`, Python 3, JDK 17+):

```bash
bash scripts/build_opencv.sh <opencv-4.14.0-source> <work-dir> arm64-v8a
bash scripts/build_opencv.sh <opencv-4.14.0-source> <work-dir> armeabi-v7a
bash scripts/install_opencv.sh <work-dir>
```

If DocVault starts using another OpenCV module (for example `calib3d` or `photo`), add it to `BUILD_LIST` in
`build_opencv.sh` and rebuild; the app will not compile against a class that is not generated here.
