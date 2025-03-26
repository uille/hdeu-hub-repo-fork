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
package io.piveau.hub.util;

import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;
import io.vertx.ext.web.MIMEHeader;
import io.vertx.ext.web.ParsedHeaderValues;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.validation.RequestParameter;
import io.vertx.ext.web.validation.RequestParameters;
import io.vertx.ext.web.validation.ValidationHandler;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;

public class ContentNegotiation {
    private final RoutingContext context;
    private final String id;
    private String acceptType;

    private final HttpMethod method;

    private static final Pattern pattern;

    private static final String CHARSET_UTF8 = "charset=UTF-8";

    static {
        String regex = "^(.+)(\\.)(" + String.join("|", ContentType.getFileExtensions()) + ")$";
        pattern = Pattern.compile(regex);
    }

    public ContentNegotiation(RoutingContext context) {
        this(context, "id", ContentType.DEFAULT);
    }

    public ContentNegotiation(RoutingContext context, String idName, ContentType defaultContentType) {
        this.context = context;
        method = context.request().method();

        RequestParameters parameters = context.get(ValidationHandler.REQUEST_CONTEXT_KEY);
        RequestParameter requestId = parameters != null ? parameters.pathParameter(idName) : null;
        if (requestId == null) {
            id = "";
            acceptType = acceptHeader(defaultContentType);
        } else {
            List<MatchResult> results = pattern.matcher(requestId.getString()).results().toList();

            if (results.isEmpty()) {
                id = requestId.getString();
                acceptType = acceptHeader(defaultContentType);
            } else {
                id = results.get(0).group(1);
                String ext = results.get(0).group(3);
                acceptType = ContentType.valueOfFileExtension(ext).getMimeType();
            }
        }
    }

    public String getId() {
        return id;
    }

    public String getAcceptType() {
        return acceptType;
    }

    public void setAcceptType(String acceptType) {
        this.acceptType = acceptType;
    }

    public void headOrGetResponse(String content) {
        String contentTypeWithCharset = acceptType + "; " + CHARSET_UTF8;
        if (method == HttpMethod.HEAD) {
            if (context.response() != null) {
                context.response()
                        .putHeader(HttpHeaders.CONTENT_TYPE, contentTypeWithCharset)
                        .putHeader(HttpHeaders.CONTENT_LENGTH, String.valueOf(content.getBytes(StandardCharsets.UTF_8).length))
                        .end();
            }
        } else {
            if (context.response() != null) {
                context.response()
                        .putHeader(HttpHeaders.CONTENT_TYPE, contentTypeWithCharset)
                        .end(content);
            }
        }
    }

    private String acceptHeader(ContentType defaultContentType) {
        ParsedHeaderValues parsedHeaderValues = context.parsedHeaders();
        List<MIMEHeader> acceptHeader = parsedHeaderValues != null ? parsedHeaderValues.accept() : List.of();
        if (!acceptHeader.isEmpty() && ContentType.valueOfMimeType(acceptHeader.get(0).value()) != null) {
            return acceptHeader.get(0).value();
        } else {
            return defaultContentType.getMimeType();
        }
    }

}
