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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.piveau.hub.dataobjects.DatasetHelper;
import java.lang.reflect.Field;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.slf4j.Logger;

class PiveauLoggerTest {

  // Small helper to set the private "log" field with a mock
  private static void injectLogger(PiveauLogger pl, Logger mock) throws Exception {
    Field f = PiveauLogger.class.getDeclaredField("log");
    f.setAccessible(true);
    f.set(pl, mock);
  }

  @BeforeEach
  void resetBaseUri() {
    // make the prefix deterministic
    PiveauLogger.setBaseUri("https://example.org/");
  }

  @Test
  void prefix_withClassOnlyCtor_hasExpectedFormat() {
    class Dummy {}
    PiveauLogger pl = new PiveauLogger(Dummy.class);

    String expected = String.format(
        "[%s (piveau-hub)] [%s (%s)] [%s] ",
        "Dummy", "", "", "https://example.org/");

    assertEquals(expected, pl.getPrefixMessage());
  }

  @Test
  void prefix_withDatasetAndCatalogueCtor_hasExpectedFormat() {
    class Dummy {}
    PiveauLogger pl = new PiveauLogger("ds-1", "cat-9", Dummy.class);

    String expected = String.format(
        "[%s (piveau-hub)] [%s (%s)] [%s] ",
        "Dummy", "ds-1", "cat-9", "https://example.org/");

    assertEquals(expected, pl.getPrefixMessage());
  }

  @Test
  void prefix_withDatasetHelperCtor_hasExpectedFormat() {
    class Dummy {}

    DatasetHelper helper = mock(DatasetHelper.class, Answers.RETURNS_DEEP_STUBS);
    when(helper.originId()).thenReturn("origin-7");
    when(helper.catalogueId()).thenReturn("cat-7");

    PiveauLogger pl = new PiveauLogger(helper, Dummy.class);

    String expected = String.format(
        "[%s (piveau-hub)] [%s (%s)] [%s] ",
        "Dummy", "origin-7", "cat-7", "https://example.org/");

    assertEquals(expected, pl.getPrefixMessage());
  }

  @Test
  void trace_family_delegatesToSlf4jWithPrefix() throws Exception {
    class Dummy {}
    PiveauLogger pl = new PiveauLogger("ds", "cat", Dummy.class);
    Logger slf4j = mock(Logger.class);
    injectLogger(pl, slf4j);

    String prefix = pl.getPrefixMessage();

    pl.trace("plain");
    verify(slf4j).trace(prefix + "plain");

    pl.trace("fmt {}", 1);
    verify(slf4j).trace(prefix + "fmt {}", 1);

    pl.trace("fmt {} {}", "a", "b");
    verify(slf4j).trace(prefix + "fmt {} {}", "a", "b");

    pl.trace("vargs {} {} {}", 1, 2, 3);
    verify(slf4j).trace(prefix + "vargs {} {} {}", 1, 2, 3);

    RuntimeException ex = new RuntimeException("x");
    pl.trace("boom", ex);
    verify(slf4j).trace(prefix + "boom", ex);
  }

  @Test
  void debug_family_delegatesToSlf4jWithPrefix() throws Exception {
    class Dummy {}
    PiveauLogger pl = new PiveauLogger("ds", "cat", Dummy.class);
    Logger slf4j = mock(Logger.class);
    injectLogger(pl, slf4j);

    String prefix = pl.getPrefixMessage();

    pl.debug("plain");
    verify(slf4j).debug(prefix + "plain");

    pl.debug("fmt {}", 7);
    verify(slf4j).debug(prefix + "fmt {}", 7);

    pl.debug("fmt {} {}", "x", "y");
    verify(slf4j).debug(prefix + "fmt {} {}", "x", "y");

    // Note: The implementation currently ignores varargs (potential issue),
    // but we verify exactly what the code does: it only passes the formatted string.
    pl.debug("vargs {} {} {}", 1, 2, 3);
    verify(slf4j).debug(prefix + "vargs {} {} {}");

    RuntimeException ex = new RuntimeException("e");
    pl.debug("with ex", ex);
    verify(slf4j).debug(prefix + "with ex", ex);
  }

  @Test
  void info_family_delegatesToSlf4jWithPrefix() throws Exception {
    class Dummy {}
    PiveauLogger pl = new PiveauLogger("ds", "cat", Dummy.class);
    Logger slf4j = mock(Logger.class);
    injectLogger(pl, slf4j);

    String prefix = pl.getPrefixMessage();

    pl.info("plain");
    verify(slf4j).info(prefix + "plain");

    pl.info("fmt {}", "v");
    verify(slf4j).info(prefix + "fmt {}", "v");

    pl.info("fmt {} {}", "a", "b");
    verify(slf4j).info(prefix + "fmt {} {}", "a", "b");

    pl.info("vargs {} {} {}", 1, 2, 3);
    verify(slf4j).info(prefix + "vargs {} {} {}", 1, 2, 3);

    Exception ex = new Exception("e");
    pl.info("boom", ex);
    verify(slf4j).info(prefix + "boom", ex);
  }

  @Test
  void warn_family_delegatesToSlf4jWithPrefix() throws Exception {
    class Dummy {}
    PiveauLogger pl = new PiveauLogger("ds", "cat", Dummy.class);
    Logger slf4j = mock(Logger.class);
    injectLogger(pl, slf4j);

    String prefix = pl.getPrefixMessage();

    pl.warn("plain");
    verify(slf4j).warn(prefix + "plain");

    pl.warn("fmt {}", 42);
    verify(slf4j).warn(prefix + "fmt {}", 42);

    pl.warn("vargs {} {} {}", 1, 2, 3);
    verify(slf4j).warn(prefix + "vargs {} {} {}", 1, 2, 3);

    Throwable t = new Throwable("t");
    pl.warn("with t", t);
    verify(slf4j).warn(prefix + "with t", t);
  }

  @Test
  void error_family_delegatesToSlf4jWithPrefix() throws Exception {
    class Dummy {}
    PiveauLogger pl = new PiveauLogger("ds", "cat", Dummy.class);
    Logger slf4j = mock(Logger.class);
    injectLogger(pl, slf4j);

    String prefix = pl.getPrefixMessage();

    pl.error("plain");
    verify(slf4j).error(prefix + "plain");

    pl.error("fmt {}", "E");
    verify(slf4j).error(prefix + "fmt {}", "E");

    pl.error("fmt {} {}", "A", "B");
    verify(slf4j).error(prefix + "fmt {} {}", "A", "B");

    pl.error("vargs {} {} {}", 1, 2, 3);
    verify(slf4j).error(prefix + "vargs {} {} {}", 1, 2, 3);

    Exception ex = new Exception("e");
    pl.error("boom", ex);
    verify(slf4j).error(prefix + "boom", ex);
  }

  @Test
  void enabledFlags_proxyToSlf4j() throws Exception {
    class Dummy {}
    PiveauLogger pl = new PiveauLogger(Dummy.class);
    Logger slf4j = mock(Logger.class);
    injectLogger(pl, slf4j);

    when(slf4j.isTraceEnabled()).thenReturn(true);
    when(slf4j.isDebugEnabled()).thenReturn(false);
    when(slf4j.isInfoEnabled()).thenReturn(true);
    when(slf4j.isWarnEnabled()).thenReturn(true);
    when(slf4j.isErrorEnabled()).thenReturn(true);

    assertTrue(pl.isTraceEnabled());
    assertFalse(pl.isDebugEnabled());
    assertTrue(pl.isInfoEnabled());
    assertTrue(pl.isWarnEnabled());
    assertTrue(pl.isErrorEnabled());
  }
}
