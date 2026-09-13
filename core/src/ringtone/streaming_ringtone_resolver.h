#pragma once

#include <string>

namespace edom::alarm::core {

enum class StreamingPlatform { kQqMusic, kNetEaseMusic, kCustomStream, kLocal };

class StreamingRingtoneResolver {
   public:
    static constexpr const char* kDefaultFallbackUri = "android.resource://system/alarm_gentle";

    // Resolves streaming URI with fallback protection
    static std::string ResolvePlayableUri(const std::string& primary_uri,
                                          const std::string& fallback_uri,
                                          bool is_network_available);

    static StreamingPlatform IdentifyPlatform(const std::string& uri);
};

}  // namespace edom::alarm::core
