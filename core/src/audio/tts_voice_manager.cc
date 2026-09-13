#include "core/src/audio/tts_voice_manager.h"

#include <iomanip>
#include <sstream>

namespace edom::alarm::core {

std::string TtsVoiceManager::FormatUtteranceZh(int hour, int minute, const std::string& label) {
    std::ostringstream oss;
    oss << "现在时间是 " << hour << " 点 ";
    if (minute > 0) {
        oss << minute << " 分。";
    } else {
        oss << "整。";
    }
    if (!label.empty()) {
        oss << "闹钟提醒：" << label;
    }
    return oss.str();
}

std::string TtsVoiceManager::FormatUtteranceEn(int hour, int minute, const std::string& label) {
    std::ostringstream oss;
    oss << "The time is " << std::setw(2) << std::setfill('0') << hour << ":" << std::setw(2)
        << std::setfill('0') << minute << ". ";
    if (!label.empty()) {
        oss << "Alarm reminder: " << label;
    }
    return oss.str();
}

}  // namespace edom::alarm::core
