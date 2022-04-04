package io.supabase.postgrest.http

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.features.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.client.utils.*
import io.ktor.http.*
import io.ktor.util.*
import io.ktor.utils.io.core.*
import kotlin.coroutines.cancellation.CancellationException

class PostgrestHttpClient(val httpClient: HttpClient) {

    suspend inline fun <reified T> execute(
        uri: Url,
        method: HttpMethod,
        headers: Headers = headersOf(),
        body: Any? = null
    ): Result<PostgrestHttpResponse<T>> {
        try {
            val callResult = httpClient.use { httpClient ->
                httpClient.request<HttpResponse>(uri) {
                    this.method = method
                    if (body != null) {
                        this.body = body
                    }

                    buildHeaders {
                        appendAll(headers)
                    }
                }
            }
            val resultProcessed = responseHandler<T>(callResult)

            return Result.success(resultProcessed)
        } catch (ex: Exception) {
            return when (ex) {
                is RedirectResponseException -> {
                    Result.failure(
                        PostgrestHttpException(ex.response.status, ex.response.readText(), ex)
                    )
                }

                is PostgrestHttpException -> {
                    Result.failure(ex)
                }

                else -> {
                    Result.failure(
                        PostgrestHttpException(
                            HttpStatusCode(418, "I'm a teapot"),
                            ex.message,
                            ex
                        )
                    )
                }
            }
        }
    }

}

@Throws(PostgrestHttpException::class, CancellationException::class)
suspend inline fun <reified T> responseHandler(response: HttpResponse): PostgrestHttpResponse<T> {
    val statusSuccessful = response.status.isSuccess()

    if (!statusSuccessful) {
        val entityAsString = response.receive<String>()

        throw PostgrestHttpException(response.status, entityAsString, null)
    }

    val count = extractCount(response.headers.toMap(), response.request.headers.toMap())
    val obj = response.receive<T>()

    return PostgrestHttpResponse(
        status = response.status,
        body = obj,
        count = count
    )
}