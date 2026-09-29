package com.whats.web.scan.webscan.pdfreaderpdffileedit.ai

import javax.inject.Inject
import javax.inject.Singleton

/** The finished AI text, handed to the result screen. A route argument would carry it through a Bundle. */
@Singleton
class AiResultHolder @Inject constructor() {
    data class Result(val job: AiJob, val sourceName: String, val text: String)

    var result: Result? = null
}
