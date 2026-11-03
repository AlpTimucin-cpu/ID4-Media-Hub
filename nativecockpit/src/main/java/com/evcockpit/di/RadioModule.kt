package com.evcockpit.di

import com.evcockpit.data.radio.RadioBrowserApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object RadioModule {
    @Provides
    @Singleton
    fun radioApi(): RadioBrowserApi = Retrofit.Builder()
        .baseUrl("https://de1.api.radio-browser.info/")
        .client(OkHttpClient.Builder()
            .callTimeout(20, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                chain.proceed(chain.request().newBuilder()
                    .header("User-Agent", "EV-Cockpit/Android")
                    .build())
            }.build())
        .addConverterFactory(GsonConverterFactory.create())
        .build().create(RadioBrowserApi::class.java)
}
