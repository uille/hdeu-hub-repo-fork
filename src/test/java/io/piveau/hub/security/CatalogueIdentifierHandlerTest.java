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
package io.piveau.hub.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.piveau.dcatap.DatasetManager;
import io.piveau.dcatap.TripleStoreException;
import io.vertx.core.Future;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.validation.RequestParameter;
import io.vertx.ext.web.validation.RequestParameters;
import io.vertx.ext.web.validation.ValidationHandler;
import org.apache.jena.rdf.model.Resource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * Unit tests for CatalogueIdentifierHandler.
 */
class CatalogueIdentifierHandlerTest {

  @Mock
  private DatasetManager datasetManager;

  @InjectMocks
  private CatalogueIdentifierHandler handler;

  @Mock
  private RoutingContext context;

  @Mock
  private RequestParameters params;

  @Mock
  private RequestParameter queryParam; // only used when non-null

  @BeforeEach
  void setup() {
    // Initialize mocks
    MockitoAnnotations.openMocks(this);
    RequestParameter pathParam = mock(RequestParameter.class);

    // The handler reads RequestParameters from the routing context using this key
    when(context.get(ValidationHandler.REQUEST_CONTEXT_KEY)).thenReturn(params);
    when(params.pathParameter("datasetId")).thenReturn(pathParam); // Common datasetId path para
    when(pathParam.getString()).thenReturn("ds-123");
  }

  @Test
  void whenCatalogueQueryMissing_looksUpCatalogue_putsId_and_callsNext() {
    // Arrange: no "catalogue" query param (null triggers the lookup branch)
    when(params.queryParameter("catalogue")).thenReturn(null);

    // Mock DatasetManager.catalogue success
    String mockedResourceUri = "http://example.org/catalogue/abc"; // any URI that DCATAPUriSchema can parse
    Resource resource = mock(Resource.class);
    when(resource.getURI()).thenReturn(mockedResourceUri);

    // We don't care about the exact argument string here; use anyString()
    when(datasetManager.catalogue(anyString())).thenReturn(Future.succeededFuture(resource));

    // Act
    handler.handle(context);

    // Assert: it should put "catalogueId" and then call next()
    ArgumentCaptor<String> keyCap = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<Object> valCap = ArgumentCaptor.forClass(Object.class);
    verify(context, timeout(250)).put(keyCap.capture(), valCap.capture());
    verify(context, timeout(250)).next();

    // Sanity on the "catalogueId" key
    assertEquals("catalogueId", keyCap.getValue());
    assertNotNull(valCap.getValue(), "catalogueId value should be set");

    // Also verify the lookup was invoked once
    verify(datasetManager, times(1)).catalogue(anyString());
  }

  @Test
  void whenCatalogueQueryPresent_skipsLookup_and_callsNext() {
    // Arrange: non-null query parameter object => branch that skips datasetManager
    when(params.queryParameter("catalogue")).thenReturn(queryParam);

    // Act
    handler.handle(context);

    // Assert: next called, and no lookup performed
    verify(context, timeout(250)).next();
    verify(datasetManager, never()).catalogue(anyString());
    verify(context, never()).put(eq("catalogueId"), any());
  }

  @Test
  void whenLookupFailsWith404_doesNotCallNext() {
    // Arrange
    when(params.queryParameter("catalogue")).thenReturn(null);

    // Fail with a TripleStoreException that reports code 404
    TripleStoreException tse = mock(TripleStoreException.class);
    when(tse.getCode()).thenReturn(404);
    when(datasetManager.catalogue(anyString())).thenReturn(Future.failedFuture(tse));

    // Act
    handler.handle(context);

    // Assert: handler handles the failure (delegates to HubRepo) and does NOT continue the chain
    verify(context, after(200).never()).next();
    verify(context, never()).put(eq("catalogueId"), any());
  }

  @Test
  void whenLookupFailsWithOtherError_doesNotCallNext() {
    // Arrange
    when(params.queryParameter("catalogue")).thenReturn(null);

    RuntimeException boom = new RuntimeException("boom");
    when(datasetManager.catalogue(anyString())).thenReturn(Future.failedFuture(boom));

    // Act
    handler.handle(context);

    // Assert
    verify(context, after(200).never()).next();
    verify(context, never()).put(eq("catalogueId"), any());
  }
}
