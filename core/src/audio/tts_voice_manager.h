#pragma once

#include <string>

namespace edom::alarm::core {

class TtsVoiceManager {
   public:
    // Generates localized speech utterance from label and time
    static std::string FormatUtteranceZh(int hour, int minute, const std::string& label);
    static std::string FormatUtteranceEn(int hour, int minute, const std::string& label);
};

}  // namespace edom::alarm::core
