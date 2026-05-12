/*
 * Copyright (C) 2024 European Union
 *  __
 * Licensed under the EUPL, Version 1.2 or – as soon they will be approved by the
 * European Commission – subsequent versions of the EUPL (the “Licence”);
 * You may not use this work except in compliance with the Licence.
 * You may obtain a copy of the Licence at:
 * *
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 * *
 * Unless required by applicable law or agreed to in writing, software distributed under
 * the Licence is distributed on an “AS IS” basis, WITHOUT WARRANTIES OR CONDITIONS
 * OF ANY KIND, either express or implied. See the Licence for the specific language
 * governing permissions and limitations under the Licence.
 */
package io.piveau.hub.util.logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.lang.reflect.Field;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.slf4j.Logger;

/**
 * Tests for SimpleLogger.
 * Package-private constructor -> tests live in the same package.
 */
class SimpleLoggerTest {

  private static void injectLogger(PiveauLogger pl, Logger mock) throws Exception {
    Field f = PiveauLogger.class.getDeclaredField("log");
    f.setAccessible(true);
    f.set(pl, mock);
  }

  @BeforeEach
  void resetBaseUri() {
    PiveauLogger.setBaseUri("https://example.org/");
  }

  @Test
  void prefix_usesSimpleFormat_componentAndBaseUriOnly() {
    class Dummy {}
    SimpleLogger sl = new SimpleLogger(Dummy.class);

    // expected: "[Dummy (piveau-hub)] [- (-)] [https://example.org/] "
    String expected = String.format("[%s (piveau-hub)] [- (-)] [%s] ",
        "Dummy", "https://example.org/");
    assertEquals(expected, sl.getPrefixMessage());
  }

  @Test
  void trace_family_delegatesWithPrefix() throws Exception {
    class Dummy {}
    SimpleLogger sl = new SimpleLogger(Dummy.class);
    Logger backend = mock(Logger.class, Answers.RETURNS_DEEP_STUBS);
    injectLogger(sl, backend);

    String prefix = sl.getPrefixMessage();

    sl.trace("plain");
    verify(backend).trace(prefix + "plain");

    sl.trace("fmt {}", 1);
    verify(backend).trace(prefix + "fmt {}", 1);

    sl.trace("fmt {} {}", "a", "b");
    verify(backend).trace(prefix + "fmt {} {}", "a", "b");

    sl.trace("varargs {} {} {}", 1, 2, 3);
    verify(backend).trace(prefix + "varargs {} {} {}", 1, 2, 3);

    RuntimeException ex = new RuntimeException("x");
    sl.trace("boom", ex);
    verify(backend).trace(prefix + "boom", ex);
  }

  @Test
  void debug_family_delegatesWithPrefix_currentVarargsBehavior() throws Exception {
    class Dummy {}
    SimpleLogger sl = new SimpleLogger(Dummy.class);
    Logger backend = mock(Logger.class);
    injectLogger(sl, backend);

    String prefix = sl.getPrefixMessage();

    sl.debug("plain");
    verify(backend).debug(prefix + "plain");

    sl.debug("fmt {}", 7);
    verify(backend).debug(prefix + "fmt {}", 7);

    sl.debug("fmt {} {}", "x", "y");
    verify(backend).debug(prefix + "fmt {} {}", "x", "y");

    // NOTE: SimpleLogger.debug(String, Object...) currently IGNORES varargs and
    // calls .debug(String) with the formatted string only.
    sl.debug("varargs {} {} {}", 1, 2, 3);
    verify(backend).debug(prefix + "varargs {} {} {}");

    Exception ex = new Exception("e");
    sl.debug("with ex", ex);
    verify(backend).debug(prefix + "with ex", ex);
  }

  @Test
  void info_family_delegatesWithPrefix() throws Exception {
    class Dummy {}
    SimpleLogger sl = new SimpleLogger(Dummy.class);
    Logger backend = mock(Logger.class);
    injectLogger(sl, backend);

    String prefix = sl.getPrefixMessage();

    sl.info("plain");
    verify(backend).info(prefix + "plain");

    sl.info("fmt {}", "v");
    verify(backend).info(prefix + "fmt {}", "v");

    sl.info("fmt {} {}", "a", "b");
    verify(backend).info(prefix + "fmt {} {}", "a", "b");

    sl.info("vargs {} {} {}", 1, 2, 3);
    verify(backend).info(prefix + "vargs {} {} {}", 1, 2, 3);

    Exception ex = new Exception("e");
    sl.info("boom", ex);
    verify(backend).info(prefix + "boom", ex);
  }

  @Test
  void warn_family_delegatesWithPrefix() throws Exception {
    class Dummy {}
    SimpleLogger sl = new SimpleLogger(Dummy.class);
    Logger backend = mock(Logger.class);
    injectLogger(sl, backend);

    String prefix = sl.getPrefixMessage();

    sl.warn("plain");
    verify(backend).warn(prefix + "plain");

    sl.warn("fmt {}", 42);
    verify(backend).warn(prefix + "fmt {}", 42);

    sl.warn("vargs {} {} {}", 1, 2, 3);
    verify(backend).warn(prefix + "vargs {} {} {}", 1, 2, 3);

    Throwable t = new Throwable("t");
    sl.warn("with t", t);
    verify(backend).warn(prefix + "with t", t);
  }

  @Test
  void error_family_delegatesWithPrefix() throws Exception {
    class Dummy {}
    SimpleLogger sl = new SimpleLogger(Dummy.class);
    Logger backend = mock(Logger.class);
    injectLogger(sl, backend);

    String prefix = sl.getPrefixMessage();

    sl.error("plain");
    verify(backend).error(prefix + "plain");

    sl.error("fmt {}", "E");
    verify(backend).error(prefix + "fmt {}", "E");

    sl.error("fmt {} {}", "A", "B");
    verify(backend).error(prefix + "fmt {} {}", "A", "B");

    sl.error("vargs {} {} {}", 1, 2, 3);
    verify(backend).error(prefix + "vargs {} {} {}", 1, 2, 3);

    Exception ex = new Exception("e");
    sl.error("boom", ex);
    verify(backend).error(prefix + "boom", ex);
  }
}
