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
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class ComplianceService {
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private String complianceUrl;
    private WebClient client;

    public ComplianceService(WebClient client) {
        this.client = client;
        this.complianceUrl = "https://compliance.lab.gaia-x.eu/v1-staging/api/credential-offers";
    }

    /*
    Returns the Gaia-X Compliance VC for the VCs provided
    */
    public Future<JsonObject> getComplianceVC(
            List<JsonObject> VCs,
            String vcId
    ) {
        // Create VP to submit to compliance
        JsonObject vp = new JsonObject();

        JsonArray context = new JsonArray();
        context.add("https://www.w3.org/2018/credentials/v1");
        vp.put("@context", context);
        vp.put("type", "VerifiablePresentation");

        JsonArray VCsArray = new JsonArray();
        for (JsonObject vc : VCs) {
            VCsArray.add(vc);
        }
        vp.put("verifiableCredential", VCsArray);

        // Request Compliance VC
        Promise<JsonObject> promise = Promise.promise();
        String complianceUrl = this.complianceUrl + "?vcid=" + vcId;
        client.postAbs(complianceUrl)
                .putHeader("Content-Type", "application/json")
                .sendJsonObject(vp, ar -> {
                    if (ar.succeeded()) {
                        JsonObject complianceVC = ar.result().bodyAsJsonObject();
                        promise.complete(complianceVC);
                    } else {
                        promise.fail(ar.cause());
                    }
                });

        return promise.future();
    }
}
