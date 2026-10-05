#pragma once

#include <string>

namespace edom::alarm::core {

enum class StreamingPlatform { kQqMusic, kNetEaseMusic, kCustomStream, kLocal };

class StreamingRingtoneResolver {
   public:
    // Must stay a URI the platform can actually play: android.resource:// URIs
    // require an owning package, so the system alarm sound is used instead.
    static constexpr const char* kDefaultFallbackUri = "content://settings/system/alarm_alert";

    // Resolves streaming URI with fallback protection
    static std::string ResolvePlayableUri(const std::string& primary_uri,
                                          const std::string& fallback_uri,
                                          bool is_network_available);

    static StreamingPlatform IdentifyPlatform(const std::string& uri);
};

}  // namespace edom::alarm::core
