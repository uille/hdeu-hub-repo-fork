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
package io.piveau.hub.services;

import io.piveau.hub.dataobjects.DatasetHelper;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.MessageCodec;

public class DatasetHelperMessageCodec implements MessageCodec<DatasetHelper, DatasetHelper> {

    @Override
    public void encodeToWire(Buffer buffer, DatasetHelper entries) {
    }

    @Override
    public DatasetHelper decodeFromWire(int i, Buffer buffer) {
        return null;
    }

    @Override
    public DatasetHelper transform(DatasetHelper helper) {
        return helper;
    }

    @Override
    public String name() {
        return getClass().getSimpleName();
    }

    @Override
    public byte systemCodecID() {
        return -1;
    }

}
