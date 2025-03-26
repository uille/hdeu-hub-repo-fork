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


import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.file.FileSystem;

public class StorageService {
    FileSystem fileSystem;

    public StorageService(FileSystem fileSystem) {
        this.fileSystem = fileSystem;
    }

    public Future<Void> writeFile(String fileName, String fileContent) {
        // Sanitize the fileName by replacing ':' with '-'
        String sanitizedFileName = fileName.replace(":", "-");

        // Extract directory path
        int lastSlashIndex = sanitizedFileName.lastIndexOf("/");
        String directoryPath = (lastSlashIndex != -1) ? sanitizedFileName.substring(0, lastSlashIndex + 1) : "";
        String pureFileName = (lastSlashIndex != -1) ? sanitizedFileName.substring(lastSlashIndex + 1) : sanitizedFileName;

        // Ensure directory exists
        Future<Void> directoryFuture = ensureDirectory(directoryPath);

        // Write the file after directory is ensured
        Future<Void> writeFuture = directoryFuture.compose(v -> {
            Promise<Void> writePromise = Promise.promise();
            fileSystem.writeFile(directoryPath + pureFileName, Buffer.buffer(fileContent), result -> {
                if (result.succeeded()) {
                    writePromise.complete();
                } else {
                    writePromise.fail(result.cause());
                }
            });
            return writePromise.future();
        });

        return writeFuture;
    }


    public Future<String> readFile(String filePath) {
        Promise<String> promise = Promise.promise();

        // Asynchronously read the file content
        fileSystem.readFile(filePath, result -> {
            if (result.succeeded()) {
                try {
                    String file = result.result().toString();
                    promise.complete(file);
                } catch (Exception e) {
                    promise.fail(e);
                }
            } else {
                promise.fail(result.cause());
            }
        });

        return promise.future();
    }

    /**
     * Ensures that a directory exists, creating it if necessary.
     *
     * @param directoryPath the path of the directory to check/create
     * @return Future representing the result of the directory check/create operation
     */
    private Future<Void> ensureDirectory(String directoryPath) {
        Promise<Void> promise = Promise.promise();

        // Check if the directory exists
        fileSystem.exists(directoryPath, dirCheckResult -> {
            if (dirCheckResult.succeeded()) {
                if (dirCheckResult.result()) {
                    // Directory exists
                    promise.complete();
                } else {
                    // Directory does not exist, create it
                    fileSystem.mkdirs(directoryPath, mkdirResult -> {
                        if (mkdirResult.succeeded()) {
                            promise.complete();
                        } else {
                            promise.fail(mkdirResult.cause());
                        }
                    });
                }
            } else {
                promise.fail(dirCheckResult.cause());
            }
        });

        return promise.future();
    }

}
