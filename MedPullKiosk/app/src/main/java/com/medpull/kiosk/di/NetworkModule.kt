package com.medpull.kiosk.di

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.medpull.kiosk.BuildConfig
import com.medpull.kiosk.data.remote.aws.CognitoApiService
import com.medpull.kiosk.utils.Constants
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton

/**
 * Qualifiers for different Retrofit instances
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApiGatewayRetrofit

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class CognitoRetrofit

/**
 * Hilt module for network dependencies
 */
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideGson(): Gson {
        return GsonBuilder()
            .setLenient()
            .create()
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.BODY
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
        }

        return OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .connectTimeout(Constants.Network.CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(Constants.Network.READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(Constants.Network.WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    @ApiGatewayRetrofit
    fun provideApiGatewayRetrofit(
        okHttpClient: OkHttpClient,
        gson: Gson
    ): Retrofit {
        return Retrofit.Builder()
            .baseUrl(Constants.AWS.API_ENDPOINT)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
    }

    @Provides
    @Singleton
    @CognitoRetrofit
    fun provideCognitoRetrofit(
        okHttpClient: OkHttpClient,
        gson: Gson
    ): Retrofit {
        // Cognito endpoint for the region
        val cognitoEndpoint = "https://cognito-idp.${Constants.AWS.REGION}.amazonaws.com/"
        return Retrofit.Builder()
            .baseUrl(cognitoEndpoint)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
    }

    @Provides
    @Singleton
    fun provideCognitoApiService(
        @CognitoRetrofit retrofit: Retrofit
    ): CognitoApiService {
        return retrofit.create(CognitoApiService::class.java)
    }

    @Provides
    @Singleton
    fun provideClaudeApiService(
        okHttpClient: OkHttpClient,
        gson: Gson
    ): com.medpull.kiosk.data.remote.ai.ClaudeApiService {
        return com.medpull.kiosk.data.remote.ai.ClaudeApiService(okHttpClient, gson)
    }

    @Provides
    @Singleton
    fun provideClaudeVisionService(
        gson: Gson
    ): com.medpull.kiosk.data.remote.ai.ClaudeVisionService {
        return com.medpull.kiosk.data.remote.ai.ClaudeVisionService(gson)
    }

    @Provides
    @Singleton
    fun providePdfPageRenderer(): com.medpull.kiosk.data.remote.ai.PdfPageRenderer {
        return com.medpull.kiosk.data.remote.ai.PdfPageRenderer()
    }
}
