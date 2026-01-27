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
package io.piveau.hub.ext.trust.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.file.FileSystem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class StorageServiceTest {

  FileSystem fileSystem;
  StorageService storageService;

  @BeforeEach
  void setUp() {
    fileSystem = mock(FileSystem.class);
    storageService = new StorageService(fileSystem);
  }

  // -------- writeFile --------

  @Test
  void writeFile_directoryExists_writesSuccessfully() {
    when(fileSystem.exists(anyString(), any())).thenAnswer(inv -> {
      Handler<AsyncResult<Boolean>> handler = inv.getArgument(1);
      handler.handle(Future.succeededFuture(true)); // directory exists
      return null;
    });

    when(fileSystem.writeFile(anyString(), any(Buffer.class), any())).thenAnswer(inv -> {
      Handler<AsyncResult<Void>> handler = inv.getArgument(2);
      handler.handle(Future.succeededFuture());
      return null;
    });

    Future<Void> fut = storageService.writeFile("trust/setup/file.json", "{\"a\":1}");
    assertTrue(fut.succeeded());
  }

  @Test
  void writeFile_directoryDoesNotExist_createdAndWritten() {
    when(fileSystem.exists(anyString(), any())).thenAnswer(inv -> {
      Handler<AsyncResult<Boolean>> handler = inv.getArgument(1);
      handler.handle(Future.succeededFuture(false)); // directory missing
      return null;
    });

    when(fileSystem.mkdirs(anyString(), anyString())).thenAnswer(inv -> {
      Handler<AsyncResult<Void>> handler = inv.getArgument(1);
      handler.handle(Future.succeededFuture()); // mkdir succeeds
      return null;
    });

    when(fileSystem.writeFile(anyString(), any(Buffer.class), any())).thenAnswer(inv -> {
      Handler<AsyncResult<Void>> handler = inv.getArgument(2);
      handler.handle(Future.succeededFuture());
      return null;
    });

    Future<Void> fut = storageService.writeFile("trust/setup/file.json", "{\"a\":2}");
    assertFalse(fut.succeeded());
  }

  @Test
  void writeFile_directoryCheckFails_propagatesFailure() {
    when(fileSystem.exists(anyString(), any())).thenAnswer(inv -> {
      Handler<AsyncResult<Boolean>> handler = inv.getArgument(1);
      handler.handle(Future.failedFuture(new RuntimeException("exists fail")));
      return null;
    });

    Future<Void> fut = storageService.writeFile("trust/setup/file.json", "{}");
    assertTrue(fut.failed());
    assertEquals("exists fail", fut.cause().getMessage());
  }

  @Test
  void writeFile_writeFails_propagatesFailure() {
    when(fileSystem.exists(anyString(), any())).thenAnswer(inv -> {
      Handler<AsyncResult<Boolean>> handler = inv.getArgument(1);
      handler.handle(Future.succeededFuture(true));
      return null;
    });

    when(fileSystem.writeFile(anyString(), any(Buffer.class), any())).thenAnswer(inv -> {
      Handler<AsyncResult<Void>> handler = inv.getArgument(2);
      handler.handle(Future.failedFuture(new RuntimeException("write fail")));
      return null;
    });

    Future<Void> fut = storageService.writeFile("trust/setup/file.json", "{}");
    assertTrue(fut.failed());
    assertEquals("write fail", fut.cause().getMessage());
  }

  @Test
  void writeFile_sanitizesFileName() {
    when(fileSystem.exists(contains("trust/setup"), any())).thenAnswer(inv -> {
      Handler<AsyncResult<Boolean>> handler = inv.getArgument(1);
      handler.handle(Future.succeededFuture(true));
      return null;
    });

    when(fileSystem.writeFile(anyString(), any(Buffer.class), any())).thenAnswer(inv -> {
      String path = inv.getArgument(0);
      assertFalse(path.contains(":")); // verify ':' is replaced
      Handler<AsyncResult<Void>> handler = inv.getArgument(2);
      handler.handle(Future.succeededFuture());
      return null;
    });

    Future<Void> fut = storageService.writeFile("trust:setup:file.json", "{}");
    assertFalse(fut.succeeded());
  }

  // -------- readFile --------

  @Test
  void readFile_success_returnsContent() {
    when(fileSystem.readFile(anyString(), any())).thenAnswer(inv -> {
      Handler<AsyncResult<Buffer>> handler = inv.getArgument(1);
      handler.handle(Future.succeededFuture(Buffer.buffer("{\"hello\":\"world\"}")));
      return null;
    });

    Future<String> fut = storageService.readFile("trust/setup/file.json");
    assertTrue(fut.succeeded());
    assertTrue(fut.result().contains("hello"));
  }

  @Test
  void readFile_fails_propagatesFailure() {
    when(fileSystem.readFile(anyString(), any())).thenAnswer(inv -> {
      Handler<AsyncResult<Buffer>> handler = inv.getArgument(1);
      handler.handle(Future.failedFuture(new RuntimeException("read fail")));
      return null;
    });

    Future<String> fut = storageService.readFile("trust/setup/missing.json");
    assertTrue(fut.failed());
    assertEquals("read fail", fut.cause().getMessage());
  }

  @Test
  void readFile_bufferToStringThrows_propagatesFailure() {
    Buffer badBuffer = mock(Buffer.class);
    when(badBuffer.toString()).thenThrow(new RuntimeException("bad buffer"));

    when(fileSystem.readFile(anyString(), any())).thenAnswer(inv -> {
      Handler<AsyncResult<Buffer>> handler = inv.getArgument(1);
      handler.handle(Future.succeededFuture(badBuffer));
      return null;
    });

    Future<String> fut = storageService.readFile("trust/setup/file.json");
    assertTrue(fut.failed());
    assertEquals("bad buffer", fut.cause().getMessage());
  }
}
