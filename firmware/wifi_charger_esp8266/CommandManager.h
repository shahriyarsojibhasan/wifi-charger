#ifndef COMMAND_MANAGER_H
#define COMMAND_MANAGER_H

#include "Types.h"

class CommandManager {
public:
  CommandManager();

  CommandAck processCommand(
    const String& commandId,
    const String& targetDeviceId,
    const String& action,
    const String& source,
    uint32_t createdAt,
    uint32_t expiresAt,
    const String& payload,
    String& outMessage
  );

  bool isDuplicate(const String& cmdId) const;

private:
  CommandRecord _history[COMMAND_HISTORY_SIZE];
  uint8_t       _historyIndex;
  uint8_t       _historyCount;

  void recordCommand(const String& cmdId);
};

extern CommandManager cmdMgr;

#endif // COMMAND_MANAGER_H
