package com.mlreef.rest.testcommons

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.mlreef.rest.Account
import org.junit.jupiter.api.extension.ExtendWith
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.restdocs.RestDocumentationExtension
import org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders
import org.springframework.test.context.junit.jupiter.SpringExtension
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
import org.springframework.test.web.servlet.result.MockMvcResultMatchers

@ExtendWith(value = [RestDocumentationExtension::class, SpringExtension::class])
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
abstract class AbstractRestTest {

    lateinit var mockMvc: MockMvc

    companion object {
        private val log = LoggerFactory.getLogger(this::class.java)
        const val HEADER_PRIVATE_TOKEN = "PRIVATE-TOKEN"
        const val EPF_HEADER = "EPF-BOT-TOKEN"
    }

    @Autowired protected lateinit var objectMapper: ObjectMapper

    protected fun acceptContentAuth(
        requestBuilder: MockHttpServletRequestBuilder,
        account: Account? = null,
        token: String? = null
    ): MockHttpServletRequestBuilder {
        val finalToken = token ?: account?.bestToken?.token
        ?: throw RuntimeException("No valid token to execute Gitlab request")
        return requestBuilder
            .accept(MediaType.APPLICATION_JSON)
            .header(HEADER_PRIVATE_TOKEN, finalToken)
            .contentType(MediaType.APPLICATION_JSON)
    }

    protected fun acceptAnonymousAuth(requestBuilder: MockHttpServletRequestBuilder): MockHttpServletRequestBuilder {
        return requestBuilder
            .accept(MediaType.APPLICATION_JSON)
            .contentType(MediaType.APPLICATION_JSON)
    }

    protected fun defaultAcceptContentEPFBot(token: String, requestBuilder: MockHttpServletRequestBuilder): MockHttpServletRequestBuilder {
        return requestBuilder
            .accept(MediaType.APPLICATION_JSON)
            .header(EPF_HEADER, token)
            .contentType(MediaType.APPLICATION_JSON)
    }

    protected fun performPost(url: String, account: Account? = null, body: Any? = null) =
        mockMvc.perform(
            generateRequestBuilder(url, account, body, HttpMethod.POST)
        )

    protected fun performPut(url: String, account: Account? = null, body: Any? = null) =
        mockMvc.perform(
            generateRequestBuilder(url, account, body, HttpMethod.PUT)
        )

    protected fun performGet(url: String, account: Account? = null) =
        mockMvc.perform(
            generateRequestBuilder(url, account, null, HttpMethod.GET)
        )

    protected fun performDelete(url: String, account: Account? = null) =
        mockMvc.perform(
            generateRequestBuilder(url, account, null, HttpMethod.DELETE)
        )

    protected fun performEPFPut(token: String, url: String, body: Any? = null) =
        if (body != null) {
            this.mockMvc.perform(this.defaultAcceptContentEPFBot(token, RestDocumentationRequestBuilders.put(url))
                .content(objectMapper.writeValueAsString(body)))
        } else {
            this.mockMvc.perform(this.defaultAcceptContentEPFBot(token, RestDocumentationRequestBuilders.put(url)))
        }

    private fun generateRequestBuilder(url: String, account: Account?, body: Any?, method: HttpMethod = HttpMethod.GET): MockHttpServletRequestBuilder {
        val builder = when (method) {
            HttpMethod.GET -> RestDocumentationRequestBuilders.get(url)
            HttpMethod.POST -> RestDocumentationRequestBuilders.post(url)
            HttpMethod.PUT -> RestDocumentationRequestBuilders.put(url)
            HttpMethod.DELETE -> RestDocumentationRequestBuilders.delete(url)
            else -> throw RuntimeException("Method not implemented")
        }

        if (body != null) {
            builder.content(objectMapper.writeValueAsString(body))
        }

        return if (account == null) {
            acceptAnonymousAuth(builder)
        } else {
            acceptContentAuth(builder, account)
        }
    }

    fun ResultActions.checkStatus(status: HttpStatus): ResultActions {
        return this.andExpect(MockMvcResultMatchers.status().`is`(status.value()))
    }

    fun <T> ResultActions.returnsList(clazz: Class<T>): List<T> {
        return this.andReturn().let {
            val constructCollectionType = objectMapper.typeFactory.constructCollectionType(List::class.java, clazz)
            objectMapper.readValue(it.response.contentAsByteArray, constructCollectionType)
        }
    }

    final inline fun <reified T : Any> ResultActions.returns(): T {
        return this.andReturn().let {
            `access$objectMapper`.readValue(it.response.contentAsByteArray)
        }
    }

    fun <T> ResultActions.returns(clazz: Class<T>): T {
        return this.andReturn().let {
            objectMapper.readValue(it.response.contentAsByteArray, clazz)
        }
    }

    fun <T> ResultActions.returns(valueTypeRef: TypeReference<T>): T {
        return this.andReturn().let {
            objectMapper.readValue(it.response.contentAsByteArray, valueTypeRef)
        }
    }

    fun ResultActions.expectOk(): ResultActions {
        return this.andExpect(MockMvcResultMatchers.status().isOk)
    }

    fun ResultActions.expectForbidden(): ResultActions {
        return this.andExpect(MockMvcResultMatchers.status().isForbidden)
    }

    fun ResultActions.expect4xx(): ResultActions {
        return this.andExpect(MockMvcResultMatchers.status().is4xxClientError)
    }

    fun ResultActions.expectNoContent(): ResultActions {
        return this.andExpect(MockMvcResultMatchers.status().isNoContent)
    }

    fun ResultActions.expectBadRequest(): ResultActions {
        return this.andExpect(MockMvcResultMatchers.status().isBadRequest)
    }

    @PublishedApi
    internal var `access$objectMapper`: ObjectMapper
        get() = objectMapper
        set(value) {
            objectMapper = value
        }

}


