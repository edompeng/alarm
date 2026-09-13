#include "core/src/ringtone/streaming_ringtone_resolver.h"

namespace edom::alarm::core {

StreamingPlatform StreamingRingtoneResolver::IdentifyPlatform(const std::string& uri) {
    if (uri.find("qqmusic") != std::string::npos) {
        return StreamingPlatform::kQqMusic;
    }
    if (uri.find("cloudmusic") != std::string::npos || uri.find("163") != std::string::npos) {
        return StreamingPlatform::kNetEaseMusic;
    }
    if (uri.find("http://") == 0 || uri.find("https://") == 0) {
        return StreamingPlatform::kCustomStream;
    }
    return StreamingPlatform::kLocal;
}

std::string StreamingRingtoneResolver::ResolvePlayableUri(const std::string& primary_uri,
                                                          const std::string& fallback_uri,
                                                          bool is_network_available) {
    StreamingPlatform platform = IdentifyPlatform(primary_uri);

    // If it's a local URI, no network needed
    if (platform == StreamingPlatform::kLocal) {
        return primary_uri.empty() ? kDefaultFallbackUri : primary_uri;
    }

    // If online streaming requested but network is disconnected, immediately fallback
    if (!is_network_available) {
        return fallback_uri.empty() ? kDefaultFallbackUri : fallback_uri;
    }

    // Network available, proceed with stream URI
    return primary_uri;
}

}  // namespace edom::alarm::core
