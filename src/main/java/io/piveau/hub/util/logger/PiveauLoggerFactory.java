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

import io.piveau.hub.dataobjects.DatasetHelper;

public class PiveauLoggerFactory {


    public static PiveauLogger getLogger(DatasetHelper helper, Class clazz) {
        if (helper == null) return new SimpleLogger(clazz);
        return new PiveauLogger(helper, clazz);
    }

    public static PiveauLogger getLogger(String datasetId, String catalogueId, Class clazz) {
        if (datasetId != null && !datasetId.isEmpty() && catalogueId != null && !catalogueId.isEmpty())
            return new PiveauLogger(datasetId, catalogueId, clazz);
        else if ((datasetId == null || datasetId.isEmpty()) && (catalogueId == null || catalogueId.isEmpty()))
            return new SimpleLogger(clazz);
        else if (datasetId == null || datasetId.isEmpty()) return new CatalogueLogger(catalogueId, clazz);
        else return new DatasetLogger(datasetId, clazz);
    }

    public static PiveauLogger getLogger(Class clazz) {
        return new SimpleLogger(clazz);
    }

    public static PiveauLogger getDatasetLogger(String datasetId, Class clazz) {
        return new DatasetLogger(datasetId, clazz);
    }

    public static PiveauLogger getCatalogueLogger(String catalogueId, Class clazz) {
        if (catalogueId == null || catalogueId.isEmpty()) return new SimpleLogger(clazz);
        return new CatalogueLogger(catalogueId, clazz);
    }

}
