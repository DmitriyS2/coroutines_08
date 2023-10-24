package ru.netology.coroutines

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.*
import okhttp3.*
import ru.netology.coroutines.dto.Post
import okhttp3.logging.HttpLoggingInterceptor
import ru.netology.coroutines.dto.Author
import ru.netology.coroutines.dto.Comment
import ru.netology.coroutines.dto.PostWithComments
import java.io.IOException
import java.time.Instant
import java.util.concurrent.TimeUnit
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine


private val gson = Gson()
private val BASE_URL = "http://127.0.0.1:9999"
private val client = OkHttpClient.Builder()
    .addInterceptor(HttpLoggingInterceptor(::println).apply {
        level = HttpLoggingInterceptor.Level.BODY
    })
    .connectTimeout(30L, TimeUnit.SECONDS)
    .build()

//fun main() {
//    with(CoroutineScope(EmptyCoroutineContext)) {
//        launch {
//            try {
//                val posts = getPosts(client)
//                    .map { post ->
//                        post.author = getAuthor(client, post.authorId)
//                        async {
//                            PostWithComments(post, getComments(client, post.id)
//                                .onEach { comment ->
//                                    comment.author = getAuthor(client, comment.authorId)
//                                }
//                            )
//                        }
//                    }.awaitAll()
//                println(posts)
//            } catch (e: Exception) {
//                e.printStackTrace()
//            }
//        }
//    }
//    Thread.sleep(30_000L)
//}

fun main() {
    val start = Instant.now().toEpochMilli()
    with(CoroutineScope(EmptyCoroutineContext)) {
        launch {
            try {
                val posts = getPosts(client)
                    .map { post ->
                        async {
                            post.author = getAuthor(client, post.authorId)
                            PostWithComments(post, getComments(client, post.id)
                                .onEach { comment ->
                                    comment.author = getAuthor(client, comment.authorId)
                                }
                            )
                        }
                    }.awaitAll()
                println(posts)
                println(Instant.now().toEpochMilli() - start)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    Thread.sleep(30_000L)
}

suspend fun OkHttpClient.apiCall(url: String): Response {
    return suspendCoroutine { continuation ->
        Request.Builder()
            .url(url)
            .build()
            .let(::newCall)
            .enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    continuation.resumeWithException(e)
                }

                override fun onResponse(call: Call, response: Response) {
                    continuation.resume(response)
                }
            })
    }
}

suspend fun <T> makeRequest(url: String, client: OkHttpClient, typeToken: TypeToken<T>): T =
    withContext(Dispatchers.IO) {
        client.apiCall(url)
            .let { response ->
                if (!response.isSuccessful) {
                    response.close()
                    throw RuntimeException(response.message)
                }
                val body = response.body ?: throw RuntimeException("response body is null")
                gson.fromJson(body.string(), typeToken.type)
            }
    }

suspend fun getPosts(client: OkHttpClient): List<Post> =
    makeRequest("$BASE_URL/api/slow/posts", client, object : TypeToken<List<Post>>() {})

suspend fun getComments(client: OkHttpClient, id: Long): List<Comment> =
    makeRequest("$BASE_URL/api/slow/posts/$id/comments", client, object : TypeToken<List<Comment>>() {})

suspend fun getAuthor(client: OkHttpClient, id: Long): Author =
    makeRequest("$BASE_URL/api/slow/authors/$id", client, object : TypeToken<Author>() {})
