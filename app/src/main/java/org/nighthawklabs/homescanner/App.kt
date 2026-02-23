package org.nighthawklabs.homescanner

import android.app.Application
import org.nighthawklabs.homescanner.di.AppContainer

class App : Application() {
    val container: AppContainer by lazy { AppContainer(this) }
}
