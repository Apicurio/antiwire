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

/**
 * Create and return an instance of {@link EventListener.Factory}.
 *
 * <p>Upstream declares this as a package-level function on the {@code EventListeners} file
 * facade; the port makes the facade an explicit class.
 */
public final class EventListeners {
  private EventListeners() {
  }

  /**
   * Creates an {@link EventListener.Factory} for the given class name.
   *
   * @param eventListenerFactoryClass a fully qualified class name for a class that implements
   *     {@link EventListener.Factory}. The class must have a no-arguments public constructor.
   */
  public static EventListener.Factory newEventListenerFactory(String eventListenerFactoryClass) {
    return new ClassNameEventListenerFactory(eventListenerFactoryClass);
  }

  /**
   * This event listener factory is serializable (so Gradle can cache targets that use it). It
   * works even if the delegate event listener class is itself not serializable.
   */
  private static final class ClassNameEventListenerFactory implements EventListener.Factory {
    private final String eventListenerFactoryClass;

    private transient EventListener.Factory cachedDelegate;

    ClassNameEventListenerFactory(String eventListenerFactoryClass) {
      this.eventListenerFactoryClass = eventListenerFactoryClass;
    }

    private EventListener.Factory delegate() {
      EventListener.Factory cachedResult = cachedDelegate;
      if (cachedResult != null) return cachedResult;

      Class<?> eventListenerType;
      try {
        eventListenerType = Class.forName(eventListenerFactoryClass);
      } catch (ClassNotFoundException exception) {
        throw new IllegalArgumentException(
            "Couldn't find EventListenerClass '" + eventListenerFactoryClass + "'");
      }

      java.lang.reflect.Constructor<?> constructor;
      try {
        constructor = eventListenerType.getConstructor();
      } catch (NoSuchMethodException exception) {
        throw new IllegalArgumentException(
            "No public constructor on " + eventListenerFactoryClass);
      }

      Object newInstance;
      try {
        newInstance = constructor.newInstance();
      } catch (ReflectiveOperationException exception) {
        throw new RuntimeException(exception);
      }
      if (!(newInstance instanceof EventListener.Factory)) {
        throw new IllegalArgumentException(
            eventListenerFactoryClass + " does not implement EventListener.Factory");
      }
      EventListener.Factory result = (EventListener.Factory) newInstance;
      this.cachedDelegate = result;
      return result;
    }

    @Override public EventListener create() {
      return delegate().create();
    }
  }
}
