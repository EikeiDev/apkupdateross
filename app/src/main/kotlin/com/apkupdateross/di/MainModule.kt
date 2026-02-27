package com.apkupdateross.di

import androidx.work.WorkManager
import com.apkupdateross.BuildConfig
import com.apkupdateross.R
import com.apkupdateross.data.ui.FdroidSource
import com.apkupdateross.data.ui.IzzySource
import com.apkupdateross.prefs.Prefs
import com.apkupdateross.repository.ApkMirrorRepository
import com.apkupdateross.repository.ApkPureRepository
import com.apkupdateross.repository.AppsRepository
import com.apkupdateross.repository.AptoideRepository
import com.apkupdateross.repository.FdroidRepository
import com.apkupdateross.repository.GitHubRepository
import com.apkupdateross.repository.GitLabRepository
import com.apkupdateross.repository.PlayRepository
import com.apkupdateross.repository.RuStoreRepository
import com.apkupdateross.repository.SearchRepository
import com.apkupdateross.repository.UpdatesRepository
import com.apkupdateross.service.ApkMirrorService
import com.apkupdateross.service.ApkPureService
import com.apkupdateross.service.AptoideService
import com.apkupdateross.service.FdroidService
import com.apkupdateross.service.GitHubService
import com.apkupdateross.service.RuStoreService
import com.apkupdateross.service.GitLabService
import com.apkupdateross.util.Badger
import com.apkupdateross.util.Clipboard
import com.apkupdateross.util.Downloader
import com.apkupdateross.util.InstallLog
import com.apkupdateross.util.SessionInstaller
import com.apkupdateross.util.SnackBar
import com.apkupdateross.util.Stringer
import com.apkupdateross.util.Themer
import com.apkupdateross.util.UpdatesNotification
import com.apkupdateross.util.addUserAgentInterceptor
import com.apkupdateross.util.play.PlayHttpClient
import com.apkupdateross.viewmodel.AppsViewModel
import com.apkupdateross.viewmodel.MainViewModel
import com.apkupdateross.viewmodel.SearchViewModel
import com.apkupdateross.viewmodel.SettingsViewModel
import com.apkupdateross.viewmodel.UpdatesViewModel
import com.google.gson.GsonBuilder
import com.kryptoprefs.preferences.KryptoBuilder
import okhttp3.Cache
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.core.qualifier.named
import org.koin.dsl.module
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.File


val mainModule = module {

	single { GsonBuilder().create() }

	single { Cache(androidContext().cacheDir, 1024 * 1024 * 1024) }

	single {
		HttpLoggingInterceptor().apply {
			level = HttpLoggingInterceptor.Level.BODY
		}
	}

	single {
		OkHttpClient.Builder()
			.cache(get())
			.addUserAgentInterceptor("APKUpdater-v" + BuildConfig.VERSION_NAME)
			//.addInterceptor(get<HttpLoggingInterceptor>())
			.build()
	}

	single {
		Retrofit.Builder()
			.client(get())
			.baseUrl("https://www.apkmirror.com")
			.addConverterFactory(GsonConverterFactory.create(get()))
			.build()
			.create(ApkMirrorService::class.java)
	}

	single {
		Retrofit.Builder()
			.client(get())
			.baseUrl("https://api.github.com")
			.addConverterFactory(GsonConverterFactory.create(get()))
			.build()
			.create(GitHubService::class.java)
	}

	single {
		Retrofit.Builder()
			.client(get())
			.baseUrl("https://gitlab.com")
			.addConverterFactory(GsonConverterFactory.create(get()))
			.build()
			.create(GitLabService::class.java)
	}

	single {
		Retrofit.Builder()
			.client(get())
			.baseUrl("https://f-droid.org/repo/")
			.addConverterFactory(GsonConverterFactory.create(get()))
			.build()
			.create(FdroidService::class.java)
	}

	single {
		val client = OkHttpClient.Builder()
			.cache(get())
			.addUserAgentInterceptor(AptoideRepository.UserAgent)
			.build()

		Retrofit.Builder()
			.client(client)
			.baseUrl("https://ws75.aptoide.com/api/7/")
			.addConverterFactory(GsonConverterFactory.create(get()))
			.build()
			.create(AptoideService::class.java)
	}

	single {
		Retrofit.Builder()
			.client(get())
			.baseUrl("https://tapi.pureapk.com/")
			.addConverterFactory(GsonConverterFactory.create(get()))
			.build()
			.create(ApkPureService::class.java)
	}

	single {
		val client = OkHttpClient.Builder().followRedirects(true).cache(get()).build()
		val auroraClient = OkHttpClient.Builder().followRedirects(true).cache(get()).addUserAgentInterceptor("Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Safari/537.36").build()
		val apkPureClient = OkHttpClient.Builder().followRedirects(true).cache(get()).addUserAgentInterceptor("APKPure/3.19.39 (Aegon)").build()
		val dir = File(androidContext().cacheDir, "downloads").apply { mkdirs() }
		Downloader(client, apkPureClient, auroraClient, dir)
	}

	single { ApkMirrorRepository(get(), get(), androidContext().packageManager) }

	single { AppsRepository(get(), get()) }

	single { GitHubRepository(get(), get()) }

	single { GitLabRepository(get(), get()) }

	single { ApkPureRepository(get(), get(), get()) }

	single { AptoideRepository(get(), get(), get()) }

	single {
		Retrofit.Builder()
			.client(get())
			.baseUrl("https://backapi.rustore.ru/")
			.addConverterFactory(GsonConverterFactory.create(get()))
			.build()
			.create(RuStoreService::class.java)
	}

	single { RuStoreRepository(get(), get()) }

	single { PlayRepository(get(), get(), get(), get()) }

	single(named("main")) { FdroidRepository(get(), "https://f-droid.org/repo/", FdroidSource, get()) }

	single(named("izzy")) { FdroidRepository(get(), "https://apt.izzysoft.de/fdroid/repo/", IzzySource, get()) }

	single { UpdatesRepository(get(), get(), get(), get(named("main")), get(named("izzy")), get(), get(), get(), get(), get(), get()) }

	single { SearchRepository(get(), get(named("main")), get(named("izzy")), get(), get(), get(), get(), get(), get(), get()) }

	single { KryptoBuilder.nocrypt(get(), androidContext().getString(R.string.app_name)) }

	single { Prefs(get(), true) }

	single { UpdatesNotification(get()) }

	single { Clipboard(androidContext()) }

	single { SessionInstaller(get(), get()) }

	single { SnackBar() }

	single { Badger() }

	single { Themer(get()) }

	single { Stringer(androidContext()) }

	single { InstallLog() }

	single { PlayHttpClient(get()) }

	viewModel { MainViewModel(get(), get()) }

	viewModel { AppsViewModel(get(), get(), get()) }

	viewModel { UpdatesViewModel(get(), get(), get(), get(), get(), get(), get(), get()) }

	viewModel { SettingsViewModel(get(), get(), WorkManager.getInstance(get()), get(), get(), get(), get(), get(), get()) }

	viewModel { SearchViewModel(get(), get(), get(), get(), get(), get(), get(), get()) }

}
