package ch.cclerc.luxapp.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import ch.cclerc.luxcom.model.SearchResult

object Progress {
    var numOfTimesStopViewWasOpened: Int by intPref("progress_numOfTimesStopViewWasOpened", 0)
    var numOfTimesTripViewWasOpened: Int by intPref("progress_numOfTimesTripViewWasOpened", 0)

    var shownTripViewSuggestion: Boolean by boolPref("tip_shownTripViewSuggestion") { false }

    var searchResults: List<SearchResult> by mutableStateOf(emptyList())
}
