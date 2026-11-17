// file: app/src/main/cpp/native-lib.cpp
#include <jni.h>
#include <string>

// Объявляем функции, которые лежат внутри libbyedpi.so
extern "C" {
    int byedpi_main(int argc, char* argv[]);
}

extern "C" JNIEXPORT jint JNICALL
Java_com_plum_vpn_NativeBridge_runByeDpi(JNIEnv *env, jobject thiz, jobjectArray args) {
    jsize argc = env->GetArrayLength(args);
    char** argv = new char*[argc];

    for (int i = 0; i < argc; i++) {
        jstring string = (jstring) env->GetObjectArrayElement(args, i);
        const char* rawString = env->GetStringUTFChars(string, 0);
        argv[i] = strdup(rawString);
        env->ReleaseStringUTFChars(string, rawString);
        env->DeleteLocalRef(string);
    }

    // Вызов функции из ByeDPI
    int result = byedpi_main(argc, argv);

    // Очистка памяти
    for (int i = 0; i < argc; i++) {
        free(argv[i]);
    }
    delete[] argv;

    return result;
}
