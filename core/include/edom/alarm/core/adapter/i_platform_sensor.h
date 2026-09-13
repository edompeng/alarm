#pragma once

#include <cstdint>

namespace edom::alarm::core {

class ISensorObserver {
   public:
    virtual ~ISensorObserver() = default;
    virtual void OnDeviceFlippedFaceDown() = 0;
    virtual void OnDevicePickedUp() = 0;
    virtual void OnShakeProgress(int32_t current_count, int32_t target_count) = 0;
};

class IPlatformSensor {
   public:
    virtual ~IPlatformSensor() = default;

    // Registers sensor listeners for active alarm gestures and shake challenge.
    virtual bool StartListening(ISensorObserver* observer, int32_t target_shake_count) = 0;

    // Unregisters sensors to guarantee 0% idle battery consumption.
    virtual void StopListening() = 0;
};

}  // namespace edom::alarm::core
