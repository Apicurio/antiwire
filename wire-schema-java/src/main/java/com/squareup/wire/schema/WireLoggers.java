/*
 * Copyright (C) 2023 Square, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.squareup.wire.schema;

import com.squareup.wire.WireLogger;
import java.util.Set;
import okio.Path;

/** Port of upstream jvmMain WireLoggers.kt. */
public final class WireLoggers {
  private WireLoggers() {
  }

  /**
   * Create and return an instance of {@link WireLogger.Factory}.
   *
   * @param loggerFactoryClass a fully qualified class name for a class that implements {@link
   *     WireLogger.Factory}. The class must have a no-arguments public constructor.
   */
  public static WireLogger.Factory newLoggerFactory(String loggerFactoryClass) {
    return new ClassNameLoggerFactory(loggerFactoryClass);
  }

  /**
   * This logger factory works even if the delegate logger class is itself not serializable.
   */
  private static final class ClassNameLoggerFactory implements WireLogger.Factory {
    private final String loggerFactoryClass;

    private transient WireLogger.Factory cachedDelegate;

    ClassNameLoggerFactory(String loggerFactoryClass) {
      this.loggerFactoryClass = loggerFactoryClass;
    }

    private WireLogger.Factory delegate() {
      WireLogger.Factory cachedResult = cachedDelegate;
      if (cachedResult != null) return cachedResult;

      Class<?> wireLoggerType;
      try {
        wireLoggerType = Class.forName(loggerFactoryClass);
      } catch (ClassNotFoundException exception) {
        throw new IllegalArgumentException("Couldn't find LoggerClass '" + loggerFactoryClass + "'");
      }

      java.lang.reflect.Constructor<?> constructor;
      try {
        constructor = wireLoggerType.getConstructor();
      } catch (NoSuchMethodException exception) {
        throw new IllegalArgumentException("No public constructor on " + loggerFactoryClass);
      }

      Object instance;
      try {
        instance = constructor.newInstance();
      } catch (ReflectiveOperationException exception) {
        throw new IllegalArgumentException(
            "Couldn't instantiate " + loggerFactoryClass, exception);
      }
      if (!(instance instanceof WireLogger.Factory)) {
        throw new IllegalArgumentException(
            loggerFactoryClass + " does not implement WireLogger.Factory");
      }
      WireLogger.Factory result = (WireLogger.Factory) instance;
      this.cachedDelegate = result;
      return result;
    }

    @Override public WireLogger create() {
      return delegate().create();
    }
  }
}
