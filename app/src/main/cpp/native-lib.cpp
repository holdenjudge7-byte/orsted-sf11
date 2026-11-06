#include <jni.h>
#include <android/log.h>
#include <algorithm>
#include <deque>
#include <mutex>
#include <string>

#include "bitboard.h"
#include "position.h"
#include "search.h"
#include "thread.h"
#include "tt.h"
#include "uci.h"
#include "misc.h"
#include "endgame.h"

#define TAG "OrstedSF11"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

namespace PSQT {
    void init();
}

namespace {
    bool g_initialized = false;
    std::mutex g_engineMutex;
}

extern "C" {

JNIEXPORT void JNICALL
Java_com_orsted_sf11_MainActivity_engineInit(JNIEnv* env, jobject thiz) {
    std::lock_guard<std::mutex> lock(g_engineMutex);
    if (g_initialized) return;

    LOGI("Initializing Stockfish 11");
    try {
        UCI::init(Options);
        PSQT::init();
        Bitboards::init();
        Position::init();
        Bitbases::init();
        Endgames::init();

        Threads.set(1); // Force 1 thread to save RAM
        TT.resize(16);  // 16MB Hash table
        Search::clear();
        g_initialized = true;
        LOGI("Stockfish 11 initialized");
    } catch (...) {
        LOGE("Initialization failed");
    }
}

JNIEXPORT jstring JNICALL
Java_com_orsted_sf11_MainActivity_engineGetBestMove(JNIEnv* env, jobject thiz, jstring fenJ, jint movetime) {
    std::lock_guard<std::mutex> lock(g_engineMutex);
    if (!g_initialized || fenJ == nullptr) return env->NewStringUTF("");

    const char* fenChars = env->GetStringUTFChars(fenJ, nullptr);
    std::string fen(fenChars);
    env->ReleaseStringUTFChars(fenJ, fenChars);

    try {
        Position pos;
        StateListPtr states(new std::deque<StateInfo>(1));

        pos.set(fen, false, &states->back(), Threads.main());

        Search::LimitsType limits;
        limits.startTime = now();
        limits.movetime = std::max(20, std::min((int)movetime, 10000));

        Threads.start_thinking(pos, states, limits);
        Threads.main()->wait_for_search_finished();

        std::string bestMoveStr = "";
        const auto& rootMoves = Threads.main()->rootMoves;
        if (!rootMoves.empty() && !rootMoves[0].pv.empty()) {
            Move bestMove = rootMoves[0].pv[0];
            if (bestMove != MOVE_NONE && bestMove != MOVE_NULL) {
                bestMoveStr = UCI::move(bestMove, pos.is_chess960());
            }
        }
        return env->NewStringUTF(bestMoveStr.c_str());
    } catch (...) {
        return env->NewStringUTF("");
    }
}

JNIEXPORT void JNICALL
Java_com_orsted_sf11_MainActivity_engineSetElo(JNIEnv* env, jobject thiz, jint elo) {
    std::lock_guard<std::mutex> lock(g_engineMutex);
    if (!g_initialized) return;
    if (elo >= 2800) {
        UCI::setoption("name", "UCI_LimitStrength", "value", "false");
    } else {
        UCI::setoption("name", "UCI_LimitStrength", "value", "true");
        UCI::setoption("name", "UCI_Elo", "value", std::to_string(elo));
    }
}

JNIEXPORT void JNICALL
Java_com_orsted_sf11_MainActivity_engineStop(JNIEnv* env, jobject thiz) {
    std::lock_guard<std::mutex> lock(g_engineMutex);
    if (!g_initialized) return;
    Threads.main()->wait_for_search_finished();
}
}
