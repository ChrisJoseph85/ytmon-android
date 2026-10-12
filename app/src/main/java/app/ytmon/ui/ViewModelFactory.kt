package app.ytmon.ui

import android.app.Application
import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.compose.ui.platform.LocalContext
import app.ytmon.YtMonApp

/** Builds a ViewModel with access to the app-level repository. */
@Composable
inline fun <reified VM : ViewModel> ytViewModel(
    key: String? = null,
    crossinline create: (app: YtMonApp) -> VM,
): VM {
    val context = LocalContext.current
    return viewModel(
        key = key,
        factory = viewModelFactory {
            initializer { create(context.applicationContext as YtMonApp) }
        },
    )
}