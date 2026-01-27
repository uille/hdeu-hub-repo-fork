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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.piveau.HubRepo;
import io.piveau.dcatap.DCATAPUriRef;
import io.piveau.dcatap.DCATAPUriSchema;
import io.piveau.dcatap.DatasetManager;
import io.piveau.dcatap.TripleStoreException;
import io.vertx.core.Future;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.validation.RequestParameter;
import io.vertx.ext.web.validation.RequestParameters;
import io.vertx.ext.web.validation.ValidationHandler;
import io.vertx.serviceproxy.ServiceException;
import org.apache.jena.rdf.model.Resource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;

class DatasetIdentifierHandlerTest {

  private AutoCloseable mocks;

  @Mock
  private DatasetManager manager;

  @InjectMocks
  private DatasetIdentifierHandler handler;

  @BeforeEach
  void setup() {
    mocks = MockitoAnnotations.openMocks(this);
  }

  @AfterEach
  void tearDown() throws Exception {
    mocks.close();
  }

  @Test
  void success_setsDatasetId_andCatalogueId_and_callsNext() {
    String distributionId = "dist-123";
    RequestParameters params = mock(RequestParameters.class);
    RoutingContext context = rcWithParams(distributionId, params);

    // identifyDistribution returns a dataset ref
    DCATAPUriRef datasetRef = mockUriRef("ds-999", "http://example.org/dataset/ds-999");
    when(manager.identifyDistribution(anyString())).thenReturn(Future.succeededFuture(datasetRef));

    // catalogue returns a Jena Resource with a URI
    Resource catRes = mock(Resource.class);
    String catUri = "http://example.org/catalogue/cat-42";
    when(catRes.getURI()).thenReturn(catUri);
    when(manager.catalogue(datasetRef.getUriRef())).thenReturn(Future.succeededFuture(catRes));

    try (MockedStatic<DCATAPUriSchema> schema = mockStatic(DCATAPUriSchema.class)) {
      // The handler calls these statics:
      // DCATAPUriSchema.getDistributionContext(); DCATAPUriSchema.createFor(...); DCATAPUriSchema.parseUriRef(...)

      // Return any non-null for createFor(...) so uriRef.getUriRef() is safe
      DCATAPUriRef distRef = mockUriRef("dist-123", "http://example.org/distribution/dist-123");
      schema.when(() -> DCATAPUriSchema.createFor(anyString(), any())).thenReturn(distRef);

      // Make parseUriRef(...) return a ref whose getId() is "cat-42"
      DCATAPUriRef catRef = mockUriRef("cat-42", catUri);
      schema.when(() -> DCATAPUriSchema.parseUriRef(eq(catUri))).thenReturn(catRef);

      // We don't care what getDistributionContext() returns, so leave it unstubbed (default null),
      // since our createFor(...) stub ignores the 2nd argument anyway.

      // Act
      handler.handle(context);
    }

    // Assert: two puts then next()
    ArgumentCaptor<String> keyCap = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<Object> valCap = ArgumentCaptor.forClass(Object.class);

    verify(context, timeout(300).times(2)).put(keyCap.capture(), valCap.capture());
    verify(context, timeout(300)).next();

    assertEquals("datasetId", keyCap.getAllValues().get(0));
    assertEquals("ds-999", valCap.getAllValues().get(0));

    assertEquals("catalogueId", keyCap.getAllValues().get(1));
    assertEquals("cat-42", valCap.getAllValues().get(1)); // now passes
  }

  @Test
  void identifyDistribution_404_callsFailureResponseWithServiceException404() {
    RequestParameters params = mock(RequestParameters.class);
    RoutingContext context = rcWithParams("dist-x", params);

    // Fail with TripleStoreException code 404
    TripleStoreException tse = mock(TripleStoreException.class);
    when(tse.getCode()).thenReturn(404);
    when(manager.identifyDistribution(anyString()))
        .thenReturn(Future.failedFuture(tse));

    try (MockedStatic<HubRepo> hub = mockStatic(HubRepo.class)) {
      handler.handle(context);

      // next() must NOT be called
      verify(context, after(200).never()).next();

      // failureResponse called with ServiceException 404
      hub.verify(
          () -> HubRepo.failureResponse(eq(context), argThat(t ->
              t instanceof ServiceException se && se.failureCode() == 404)
          ),
          times(1)
      );
    }
  }

  @Test
  void catalogue_404_callsFailureResponseWithServiceException404() {
    RequestParameters params = mock(RequestParameters.class);
    RoutingContext context = rcWithParams("dist-y", params);

    // identifyDistribution succeeds
    DCATAPUriRef datasetRef = mockUriRef("ds-1", "http://example.org/dataset/ds-1");
    when(manager.identifyDistribution(anyString()))
        .thenReturn(Future.succeededFuture(datasetRef));

    // catalogue fails with 404
    TripleStoreException tse = mock(TripleStoreException.class);
    when(tse.getCode()).thenReturn(404);
    when(manager.catalogue(datasetRef.getUriRef())).thenReturn(Future.failedFuture(tse));

    try (MockedStatic<HubRepo> hub = mockStatic(HubRepo.class)) {
      handler.handle(context);

      verify(context, after(200).never()).next();

      hub.verify(() -> HubRepo.failureResponse(eq(context),
              argThat(t -> t instanceof ServiceException
                  && ((ServiceException) t).failureCode() == 404)),
          times(1));
    }
  }

  @Test
  void otherError_callsFailureResponseWithOriginalCause() {
    RequestParameters params = mock(RequestParameters.class);
    RoutingContext context = rcWithParams("dist-z", params);

    // identifyDistribution succeeds
    DCATAPUriRef datasetRef = mockUriRef("ds-2", "http://example.org/dataset/ds-2");
    when(manager.identifyDistribution(anyString()))
        .thenReturn(Future.succeededFuture(datasetRef));

    // catalogue fails with non-404 cause
    RuntimeException boom = new RuntimeException("boom");
    when(manager.catalogue(datasetRef.getUriRef())).thenReturn(Future.failedFuture(boom));

    try (MockedStatic<HubRepo> hub = mockStatic(HubRepo.class)) {
      handler.handle(context);

      verify(context, after(200).never()).next();

      // same throwable instance must be forwarded
      hub.verify(() -> HubRepo.failureResponse(eq(context), same(boom)), times(1));
    }
  }

  // --- helpers ---------------------------------------------------------------
  private static RoutingContext rcWithParams(String distributionId, RequestParameters params) {
    RoutingContext context = mock(RoutingContext.class);
    when(context.get(ValidationHandler.REQUEST_CONTEXT_KEY)).thenReturn(params);

    RequestParameter param = mock(RequestParameter.class);
    when(param.getString()).thenReturn(distributionId);
    when(params.pathParameter("distributionId")).thenReturn(param);
    return context;
  }

  private static DCATAPUriRef mockUriRef(String id, String uri) {
    DCATAPUriRef ref = mock(DCATAPUriRef.class);
    when(ref.getId()).thenReturn(id);
    when(ref.getUriRef()).thenReturn(uri);
    return ref;
  }
}
