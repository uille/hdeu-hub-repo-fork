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

import java.util.Formatter;



class CatalogueLogger extends PiveauLogger {

    private String catalogueId;


    CatalogueLogger(String catalogueId, Class clazz) {
        super(clazz);
        formatString = catalogueId != null&& !catalogueId.isEmpty()?"[%s (piveau-hub)] [- (%s)] [%s] ":"[%s (piveau-hub)] [- (-)] [%s] ";
        this.catalogueId = catalogueId;
    }

    @Override
    protected String getPrefixMessage() {
        StringBuilder sb = new StringBuilder();
        try (Formatter formatter = new Formatter(sb)) {
            formatter.format(formatString, component, catalogueId, baseUri);
        }
        return sb.toString();
    }

}
