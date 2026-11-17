// file: app/src/main/cpp/native-lib.cpp
#include <jni.h>
#include <string>
#include <android/log.h>
#include <unistd.h>

// Пытаемся найти стандартную точку входа
extern "C" int main(int argc, char* argv[]);

extern "C" JNIEXPORT jint JNICALL
Java_com_plum_vpn_NativeBridge_runByeDpi(JNIEnv *env, jobject thiz, jobjectArray args) {
    jsize argc = env->GetArrayLength(args);
    char** argv = new char*[argc + 1];

    for (int i = 0; i < argc; i++) {
        jstring string = (jstring) env->GetObjectArrayElement(args, i);
        const char* rawString = env->GetStringUTFChars(string, 0);
        argv[i] = strdup(rawString);
        env->ReleaseStringUTFChars(string, rawString);
        env->DeleteLocalRef(string);
    }
    argv[argc] = NULL;

    __android_log_print(ANDROID_LOG_INFO, "PlumDuck", "Запуск нативного движка...");

    // Вызываем main()
    int result = main(argc, argv);

    for (int i = 0; i < argc; i++) {
        free(argv[i]);
    }
    delete[] argv;

    return result;
}
