#include <jni.h>
#include <libyuv.h> // Ensure libyuv is included

extern "C" JNIEXPORT void JNICALL
Java_com_codecrush_mymeeting_NV21Converter_convertRgbaToNv21(
        JNIEnv* env,
        jobject, /* this */
        jbyteArray rgbaInput,
        jint width,
        jint height,
        jbyteArray nv21Output
) {
jbyte* rgba = env->GetByteArrayElements(rgbaInput, nullptr);
jbyte* nv21 = env->GetByteArrayElements(nv21Output, nullptr);

// Convert RGBA (Java byte order) to NV21 using libyuv
libyuv::ABGRToNV21(
        reinterpret_cast<uint8_t*>(rgba), // RGBA buffer
        width * 4,                        // RGBA stride (4 bytes per pixel)
        reinterpret_cast<uint8_t*>(nv21), // Y plane
        width,                            // Y stride
        reinterpret_cast<uint8_t*>(nv21 + width * height), // VU plane
        width,                            // UV stride
        width,
        height
);

env->ReleaseByteArrayElements(rgbaInput, rgba, JNI_ABORT);
env->ReleaseByteArrayElements(nv21Output, nv21, 0);
}