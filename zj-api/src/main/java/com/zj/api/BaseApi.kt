@file:Suppress("unused", "UNUSED_PARAMETER")

package com.zj.api

import com.zj.api.base.BaseApiProxy
import com.zj.api.base.BaseRetrofit
import com.zj.api.base.RetrofitFactory
import com.zj.api.interfaces.ErrorHandler //import com.zj.api.rdt.RdtMod
import io.reactivex.Observable
import io.reactivex.Scheduler
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import okhttp3.*
import retrofit2.HttpException
import retrofit2.Response

@Suppress("MemberVisibilityCanBePrivate")
class BaseApi<T : Any> internal constructor(cls: Class<T>, private val factory: RetrofitFactory<T>, private val errorHandler: ErrorHandler? = null, private val preError: Throwable? = null) : BaseRetrofit<T>(cls, factory) {


    companion object {

        @Suppress("unused")
        @JvmStatic
        inline fun <reified T : Any> create(): BaseApiProxy<T, Nothing> {
            return BaseApiProxy(T::class.java)
        }

        @Suppress("unused")
        @JvmStatic
        inline fun <reified T : Any, reified ERROR_HANDLER : ErrorHandler> createE(): BaseApiProxy<T, ERROR_HANDLER> {
            return BaseApiProxy(T::class.java, ERROR_HANDLER::class.java.newInstance())
        }

        @Suppress("unused")
        @JvmStatic
        inline fun <reified T : Any> create(handler: ErrorHandler): BaseApiProxy<T, ErrorHandler> {
            return BaseApiProxy(T::class.java, handler)
        }

        @JvmStatic
        fun <T : Any> create(cls: Class<T>): BaseApiProxy<T, *> {
            return BaseApiProxy<T, Nothing>(cls)
        }
    }

    fun <F> request(observer: (T) -> Observable<F>, subscribe: ((isSuccess: Boolean, data: F?, throwable: HttpException?) -> Unit)? = null) {
        val subscribeSchedulers: Scheduler = Schedulers.io()
        val observableSchedulers: Scheduler = AndroidSchedulers.mainThread()
        this.request(observer, subscribeSchedulers, observableSchedulers, subscribe)
    }

    fun <F> call(observer: (T) -> Observable<F>, subscribe: ((isSuccess: Boolean, data: F?, throwable: HttpException?) -> Unit)? = null): RequestCompo? {
        val subscribeSchedulers: Scheduler = Schedulers.io()
        val observableSchedulers: Scheduler = AndroidSchedulers.mainThread()
        return this.call(observer, subscribeSchedulers, observableSchedulers, subscribe)
    }

    fun <F> request(observer: (T) -> Observable<F>, subscribeSchedulers: Scheduler = Schedulers.io(), observableSchedulers: Scheduler = AndroidSchedulers.mainThread(), subscribe: ((isSuccess: Boolean, data: F?, throwable: HttpException?) -> Unit)? = null) {
        val service = getService()
        if (service == null) {
            subscribe?.invoke(false, null, parseOrCreateHttpException(preError))
            return
        }
        RequestInCompo(observer(service), subscribeSchedulers, observableSchedulers, { data ->
            if (errorHandler?.interruptSuccessBody(data) == true) return@RequestInCompo
            subscribe?.invoke(true, data, null)
        }, { throwable ->
            dealError(throwable, subscribe)
        }).init()
    }

    fun <F> call(observer: (T) -> Observable<F>, subscribeSchedulers: Scheduler = Schedulers.io(), observableSchedulers: Scheduler = AndroidSchedulers.mainThread(), subscribe: ((isSuccess: Boolean, data: F?, throwable: HttpException?) -> Unit)? = null): RequestCompo? {
        val service = getService()
        if (service == null) {
            subscribe?.invoke(false, null, parseOrCreateHttpException(preError))
            return null
        }
        val requestInCompo: RequestInCompo<F>?
        requestInCompo = RequestInCompo(observer(service), subscribeSchedulers, observableSchedulers, { data ->
            if (errorHandler?.interruptSuccessBody(data) == true) return@RequestInCompo
            subscribe?.invoke(true, data, null)
        }, { throwable ->
            dealError(throwable, subscribe)
        })
        requestInCompo.init()
        return object : RequestCompo {
            override fun cancel() {
                requestInCompo.cancel()
            }
        }
    }

    private fun <F> dealError(throwable: Throwable?, subscribe: ((Boolean, F?, HttpException?) -> Unit)?) {
        if (errorHandler?.onError(throwable) != true) {
            val thr = throwable as? HttpException
            subscribe?.invoke(thr?.code() == 204, null, thr)
        }
    }

    private fun parseOrCreateHttpException(throwable: Throwable?, codeDefault: Int = 400): HttpException {
        if (throwable is HttpException) return throwable
        val sb = StringBuilder().append("{").append("\"message\":\"parsed unknown error with : ").append(throwable?.message).append("\"")
        val responseBody = ResponseBody.create(MediaType.get("Application/json"), sb.toString())
        val raw = okhttp3.Response.Builder().body(responseBody).code(codeDefault).message(sb.toString()).protocol(Protocol.HTTP_1_1).request(Request.Builder().url(factory.urlProvider?.url() ?: "https://unkown-host").headers(Headers.of(factory.header ?: mapOf())).build()).build()
        return HttpException(Response.error<ResponseBody>(responseBody, raw))
    }
}