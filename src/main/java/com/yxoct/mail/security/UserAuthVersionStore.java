package com.yxoct.mail.security;

import java.util.OptionalLong;

public interface UserAuthVersionStore {

  OptionalLong currentVersion(long userId);

  void setVersion(long userId, long version);

  void setVersionIfGreater(long userId, long version);
}
