package com.thescarletcoder.keys

import android.content.Context

/**
 * Phone-width screens (the Flip's inner screen is about 411 dp wide) get tighter spacing and slightly smaller labels,
 * so ten keys fit a row comfortably. Same layout and behaviour as the tablet.
 * In landscape the keys also give up height so the app above stays usable (KeyboardView.onMeasure).
 */
object Compact {
    private const val PHONE_MAX_DP = 600

    /** By the screen's short side, so a phone stays a phone in landscape. */
    fun isCompact(context: Context): Boolean =
        context.resources.configuration.smallestScreenWidthDp < PHONE_MAX_DP

    /** Sizes in dp (or sp-like dp for text), tablet first, phone second. */
    data class Sizes(val gap: Float, val pad: Float, val corner: Float, val charText: Float, val wordText: Float, val hint: Float, val strip: Float, val stripText: Float)

    val TABLET = Sizes(gap = 6f, pad = 8f, corner = 10f, charText = 24f, wordText = 15f, hint = 11f, strip = 48f, stripText = 18f)
    val PHONE = Sizes(gap = 4f, pad = 4f, corner = 7f, charText = 21f, wordText = 13f, hint = 9f, strip = 42f, stripText = 16f)

    fun sizes(context: Context) = if (isCompact(context)) PHONE else TABLET
}
