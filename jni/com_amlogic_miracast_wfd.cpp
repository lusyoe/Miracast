/*
 * Copyright 2012, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

//#define LOG_NDEBUG 0
#define LOG_TAG "amlMiracast-jni"

#include <utils/Log.h>
#include <nativehelper/jni.h>
#include <nativehelper/JNIHelp.h>
#include <android_runtime/AndroidRuntime.h>

#include "sink/WifiDisplaySink.h"
#include "source/WifiDisplaySource.h"

#include <media/IRemoteDisplay.h>
#include <media/IRemoteDisplayClient.h>

#include <binder/ProcessState.h>
#include <binder/IServiceManager.h>
#include <media/IMediaPlayerService.h>
#include <media/stagefright/DataSource.h>
#include <media/stagefright/foundation/ADebug.h>
#include <media/stagefright/foundation/AMessage.h>

using namespace android;

sp<ALooper> mSinkLooper = new ALooper;

sp<ANetworkSession> mSession = new ANetworkSession;
sp<WifiDisplaySink> mSink;
bool mStart = false;
bool mInit = false;

struct SinkHandler : public AHandler {
	SinkHandler() {};
protected:
	virtual ~SinkHandler() {};
    virtual void onMessageReceived(const sp<AMessage> &msg);

private:
	enum {
		kWhatSinkNotify,
	};
};

sp<SinkHandler> mHandler;
// static jmethodID notifyRtspError;
// static jmethodID notifyRtpNopacket;
static jobject sinkObject;

static void report_rtsp_error(void) {
    JNIEnv* env = AndroidRuntime::getJNIEnv();
	// env->CallVoidMethod(sinkObject, notifyRtspError);
}
static void report_rtp_nopacket(void) {
    JNIEnv* env = AndroidRuntime::getJNIEnv();
       // env->CallVoidMethod(sinkObject, notifyRtpNopacket);
}

void SinkHandler::onMessageReceived(const sp<AMessage> &msg) {
    switch (msg->what()) {
		case kWhatSinkNotify:
		{
			AString reason;
			msg->findString("reason", &reason);
			ALOGI("SinkHandler received : %s\n", reason.c_str());
			if (strncmp(reason.c_str(), "RTSP_ERROR", 10) == 0) {
				ALOGI("libstagefright_wfd reports RTSP_ERROR");
				report_rtsp_error();
			}else if (strncmp(reason.c_str(),"RTP_NO_PACKET", 13) == 0) {
                                ALOGI("libstagefright_wfd reports no packets");
				if(mStart)
                                report_rtp_nopacket();
                        }
			break;
		}
        default:
            TRESPASS();
    }
}

static int connect(const char *sourceHost, int32_t sourcePort) {
    /*
    ProcessState::self()->startThreadPool();
    DataSource::RegisterDefaultSniffers();

    sp<ANetworkSession> session = new ANetworkSession;
    session->start();

    sp<WifiDisplaySink> sink = new WifiDisplaySink(session);
    mSinkLooper->registerHandler(sink);

    if (sourcePort >= 0) {
        sink->start(sourceHost, sourcePort);
    } else {
        sink->start(sourceHost);
    }

    mSinkLooper->start(true);
    */

    ProcessState::self()->startThreadPool();
    DataSource::RegisterDefaultSniffers();

    mSession->start();

    if(!mInit){
    mInit = true;
    mSink = new WifiDisplaySink(mSession);
	mHandler = new SinkHandler();
    

    mSinkLooper->registerHandler(mSink);
    mSinkLooper->registerHandler(mHandler);

    mSink->setHandlerId(mHandler->id());
    }
    ALOGI("SinkHandler mSink=%d, mHandler=%d", mSink->id(), mHandler->id());
    if (sourcePort >= 0) {
        mSink->start(sourceHost, sourcePort);
    } else {
        mSink->start(sourceHost);
    }

    mStart = true;
    mSinkLooper->start(true /* runOnCallingThread */);

    //ALOGI("connected\n");
    return 0;
}

static void connect_to_wifi_source(JNIEnv* env, jclass clazz, jobject sinkobj, jstring jip, jint jport) {
    const char *ip = env->GetStringUTFChars(jip, NULL);
	
	ALOGI("ref sinkobj");
	sinkObject = env->NewGlobalRef(sinkobj);
	
    ALOGI("connect to wifi source %s:%d\n", ip, jport);

    connect(ip, jport);
    env->ReleaseStringUTFChars(jip, ip);
}

static void connect_to_rtsp_uri(JNIEnv* env, jclass clazz, jstring juri) {
    const char *ip = env->GetStringUTFChars(juri, NULL);

    ALOGI("connect to rtsp uri %s\n", ip);

    connect(ip, -1);
    env->ReleaseStringUTFChars(juri, ip);
}

static void resolutionSettings(JNIEnv * env, jclass clazz, jboolean isHD) {
    unsigned char b = isHD;
    ALOGI("\n c-boolean: %lu  ", b);
    if(b){
        mSink->setResolution(WifiDisplaySink::High);
    }else {
        mSink->setResolution(WifiDisplaySink::Normal);
    }
}

static void disconnectSink(JNIEnv* env, jclass clazz) {
    ALOGI("disconnect sink mStart:%d\n", mStart);

	ALOGI("deref sinkobj");
	env->DeleteGlobalRef(sinkObject);
	
    if(mStart){
	ALOGI("stop WifiDisplaySink");
        mSink->stop();
        mSession->stop();
        //mSinkLooper->unregisterHandler(mSink->id());
        //mSinkLooper->unregisterHandler(mHandler->id());
        //mSinkLooper->stop();
        mStart = false;
    }
}

/*
static void source_start(const char *ip) {
  ProcessState::self()->startThreadPool();

    DataSource::RegisterDefaultSniffers();

  sp<ANetworkSession> session = new ANetworkSession;
    session->start();

  mSourceLooper = new ALooper();
  sp<IRemoteDisplayClient> client;
    sp<WifiDisplaySource> source = new WifiDisplaySource(session, client);
    mSourceLooper->registerHandler(source);

    source->start(ip);

    mSourceLooper->start(true);
}

static void source_stop(JNIEnv* env, jclass clazz) {
  ALOGI("source stop \n");
  mSourceLooper->stop();
}

static void run_as_source(JNIEnv* env, jclass clazz, jstring jip) {
  const char *ip = env->GetStringUTFChars(jip, NULL);

  ALOGI("run as source %s\n", ip);
  
    source_start(ip);
    env->ReleaseStringUTFChars(jip, ip);
}
*/

// ----------------------------------------------------------------------------
static JNINativeMethod gMethods[] = {
    { "nativeConnectWifiSource", "(Lcom/winside/miracast/SinkActivity;Ljava/lang/String;I)V",
            (void*) connect_to_wifi_source },
    //{ "nativeConnectRTSPUri", "(Ljava/lang/String;)V",
    //        (void*) connect_to_rtsp_uri },
  { "nativeDisconnectSink", "()V",
            (void*) disconnectSink },
  {"nativeResolutionSettings", "(Z)V",
            (void*) resolutionSettings},
  //{ "nativeSourceStart", "(Ljava/lang/String;)V",
    //        (void*) run_as_source },
  //{ "nativeSourceStop", "()V",
    //        (void*) source_stop },
};

#define FIND_CLASS(var, className) \
        var = env->FindClass(className); \
        LOG_FATAL_IF(! var, "Unable to find class " className);

#define GET_METHOD_ID(var, clazz, methodName, methodDescriptor) \
        var = env->GetMethodID(clazz, methodName, methodDescriptor); \
        LOG_FATAL_IF(! var, "Unable to find method " methodName);

int register_com_amlogic_miracast_WiFiDirectActivity(JNIEnv *env) {
    static const char* const kClassPathName = "com/winside/miracast/SinkActivity";
	jclass clazz;
	FIND_CLASS(clazz, kClassPathName);
	// GET_METHOD_ID(notifyRtspError, clazz, "notifyRtspError", "()V"); 
	// GET_METHOD_ID(notifyRtpNopacket, clazz, "notifyRtpNopacket", "()V");
    return jniRegisterNativeMethods(env, kClassPathName, gMethods, sizeof(gMethods) / sizeof(gMethods[0]));
}
jint JNI_OnLoad(JavaVM* vm, void* reserved)
{
    JNIEnv* env = NULL;
    jint result = -1;

    if (vm->GetEnv((void**) &env, JNI_VERSION_1_4) != JNI_OK) {
        ALOGI("ERROR: GetEnv failed\n");
        goto bail;
    }
    assert(env != NULL);

  /*
    if (AndroidRuntime::registerNativeMethods(env, "com/android/server/am/ActivityStack", gMethods, NELEM(gMethods)) < 0){
        LOGE("Can't register ActivityStack");
        goto bail;
    }*/

    if(register_com_amlogic_miracast_WiFiDirectActivity(env) < 0){
        ALOGE("Can't register WiFiDirectActivity");
        goto bail;
    }

    /* success -- return valid version number */
    result = JNI_VERSION_1_4;

bail:
    return result;
}


