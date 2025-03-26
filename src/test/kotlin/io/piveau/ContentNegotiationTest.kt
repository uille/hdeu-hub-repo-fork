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
import io.piveau.hub.util.ContentNegotiation
import io.piveau.hub.util.ContentType
import io.vertx.core.Future
import io.vertx.core.http.HttpHeaders
import io.vertx.core.http.HttpMethod
import io.vertx.core.http.HttpServerRequest
import io.vertx.core.http.HttpServerResponse
import io.vertx.ext.web.MIMEHeader
import io.vertx.ext.web.RoutingContext
import io.vertx.ext.web.validation.RequestParameter
import io.vertx.ext.web.validation.RequestParameters
import io.vertx.ext.web.validation.ValidationHandler
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.EnumSource
import org.junit.jupiter.params.provider.MethodSource
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.stream.Stream

class ContentNegotiationTest {
    private lateinit var context: RoutingContext
    private lateinit var requestParameters: RequestParameters
    private lateinit var requestId: RequestParameter

    @BeforeEach
    fun setUp() {
        context = mock()
        requestParameters = mock()
        requestId = mock()
        whenever(context.get<RequestParameters>(ValidationHandler.REQUEST_CONTEXT_KEY)).thenReturn(requestParameters)
    }

    companion object {
        @JvmStatic
        fun fileExtensions(): Stream<Arguments> {
            return Stream.of(
                Arguments.of("rdf", ContentType.RDF_XML),
                Arguments.of("n3", ContentType.N3),
                Arguments.of("nt", ContentType.N_TRIPLES),
                Arguments.of("nq", ContentType.N_QUADS),
                Arguments.of("ttl", ContentType.TURTLE),
                Arguments.of("trig", ContentType.TRIG),
                Arguments.of("trix", ContentType.TRIX),
                Arguments.of("json", ContentType.JSON),
                Arguments.of("jsonld", ContentType.JSON_LD)
            )
        }
    }
    @Test
    fun testConstructorWithDefaultContentType() {
        val request = mock<HttpServerRequest>()
        val parsedHeaders = mock<io.vertx.ext.web.ParsedHeaderValues>()
        whenever(context.request()).thenReturn(request)
        whenever(request.method()).thenReturn(HttpMethod.GET)
        whenever(requestParameters.pathParameter("id")).thenReturn(null)
        whenever(context.parsedHeaders()).thenReturn(parsedHeaders)
        whenever(parsedHeaders.accept()).thenReturn(listOf())

        val cn = ContentNegotiation(context)

        assertEquals("", cn.id)
        assertEquals(ContentType.DEFAULT.mimeType, cn.acceptType)
    }

    @ParameterizedTest
    @MethodSource("fileExtensions")
    fun testConstructorWithFileExtension(extension: String, expectedContentType: ContentType) {
        val request = mock<HttpServerRequest>()
        whenever(context.request()).thenReturn(request)
        whenever(request.method()).thenReturn(HttpMethod.GET)
        whenever(requestParameters.pathParameter("id")).thenReturn(requestId)
        whenever(requestId.getString()).thenReturn("test.$extension")

        val cn = ContentNegotiation(context)

        assertEquals("test", cn.id)
        assertEquals(expectedContentType.mimeType, cn.acceptType)
    }

    @Test
    fun testConstructorWithAcceptHeader() {
        val request = mock<HttpServerRequest>()
        val parsedHeaders = mock<io.vertx.ext.web.ParsedHeaderValues>()
        val mimeHeader = mock<MIMEHeader>()
        whenever(context.request()).thenReturn(request)
        whenever(request.method()).thenReturn(HttpMethod.GET)
        whenever(requestParameters.pathParameter("id")).thenReturn(null)
        whenever(context.parsedHeaders()).thenReturn(parsedHeaders)
        whenever(mimeHeader.value()).thenReturn(ContentType.JSON.mimeType)
        whenever(parsedHeaders.accept()).thenReturn(listOf(mimeHeader))

        val cn = ContentNegotiation(context)

        assertEquals("", cn.id)
        assertEquals(ContentType.JSON.mimeType, cn.acceptType)
    }

    @ParameterizedTest
    @EnumSource(ContentType::class)
    fun testHeadOrGetResponseHead(contentType: ContentType) {
        val request = mock<HttpServerRequest>()
        val response = mock<HttpServerResponse>()
        whenever(context.request()).thenReturn(request)
        whenever(request.method()).thenReturn(HttpMethod.HEAD)
        whenever(context.response()).thenReturn(response)

        mockPutHeader(response)
        mockEnd(response)

        val cn = ContentNegotiation(context)
        cn.acceptType = contentType.mimeType

        val content = "{\"key\": \"value\"}"
        cn.headOrGetResponse(content)

        verify(response).putHeader(HttpHeaders.CONTENT_TYPE, "${contentType.mimeType}; charset=UTF-8")
        verify(response).putHeader(HttpHeaders.CONTENT_LENGTH, content.toByteArray(Charsets.UTF_8).size.toString())
        verify(response).end()
    }

    @ParameterizedTest
    @EnumSource(ContentType::class)
    fun testHeadOrGetResponseGet(contentType: ContentType) {
        val request = mock<HttpServerRequest>()
        val response = mock<HttpServerResponse>()
        whenever(context.request()).thenReturn(request)
        whenever(request.method()).thenReturn(HttpMethod.GET)
        whenever(context.response()).thenReturn(response)

        mockPutHeader(response)
        mockEnd(response)

        val cn = ContentNegotiation(context)
        cn.acceptType = contentType.mimeType

        val content = "{\"key\": \"value\"}"
        cn.headOrGetResponse(content)

        verify(response).putHeader(HttpHeaders.CONTENT_TYPE, "${contentType.mimeType}; charset=UTF-8")
        verify(response).end(content)
    }

    private fun mockPutHeader(response: HttpServerResponse) {
        whenever(response.putHeader(any<CharSequence>(), any<CharSequence>())).thenReturn(response)
        whenever(response.putHeader(any<String>(), any<String>())).thenReturn(response)
    }

    private fun mockEnd(response: HttpServerResponse) {
        whenever(response.end()).thenReturn(Future.succeededFuture())
        whenever(response.end(any<String>())).thenReturn(Future.succeededFuture())
    }
}
