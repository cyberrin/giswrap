package com.cyberrin.giswrap.ui.common

import android.content.Context
import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import com.cyberrin.giswrap.domain.model.AppLanguage
import java.util.Locale

// Per-app language without androidx.appcompat, which would be a whole library for
// one setting, and without LocaleManager, which is API 33+ against a minSdk of 26.
//
// Overrides LocalResources and nothing else, so every label switches with no
// Activity restart and nothing outside this file knows the setting exists.
//
// NOT LocalContext: createConfigurationContext returns a bare ContextImpl, not a
// wrapper around the Activity, so providing it severs the chain hiltViewModel()
// walks to find one -- "Expected an activity context for creating a
// HiltViewModelFactory but instead found: android.app.ContextImpl".
//
// LocalResources is what stringResource and booleanResource actually read, so
// this is both narrower and the only part that was ever needed.
@Composable
fun AppLanguageProvider(language: AppLanguage, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current

    val resources = remember(language, context, configuration) {
        language.tag
            ?.let { context.withLocale(Locale.forLanguageTag(it)).resources }
            ?: context.resources
    }

    CompositionLocalProvider(LocalResources provides resources, content = content)
}

// Also used off the composition, by the widget, which has a Context but no tree.
fun Context.withLocale(locale: Locale): Context {
    val configuration = Configuration(resources.configuration).apply {
        setLocale(locale)
        setLayoutDirection(locale)
    }
    return createConfigurationContext(configuration)
}

// What the widget and any non-Compose caller need: the chosen locale, or the
// system's own when the user has not overridden it.
fun AppLanguage.localeOr(system: Locale): Locale =
    tag?.let { Locale.forLanguageTag(it) } ?: system

// The locale the composition is currently resolving resources against -- which is
// the override when there is one, and the system's choice when there is not.
@Composable
fun appLocale(): Locale = LocalResources.current.configuration.locales[0]

// For the widget: it lives in the launcher's process with a Context but no
// composition, so the override has to be applied to the Context by hand.
fun Context.forLanguage(language: AppLanguage): Context =
    language.tag?.let { withLocale(Locale.forLanguageTag(it)) } ?: this
