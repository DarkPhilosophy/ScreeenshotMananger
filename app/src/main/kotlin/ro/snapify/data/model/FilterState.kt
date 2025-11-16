package ro.snapify.data.model

/**
 * Represents the current filter state for the screenshot list.
 * Contains selected tags and selected folders for filtering.
 */
data class FilterState(
    val selectedTags: Set<ScreenshotTab> = setOf(
        ScreenshotTab.MARKED,
        ScreenshotTab.KEPT,
        ScreenshotTab.UNMARKED
    ), // Default: all selected
    val selectedFolders: Set<String> = emptySet() // Empty means all folders
) {
    /**
     * Returns true if all possible tags are selected.
     */
    fun isAllTagsSelected(): Boolean = selectedTags.size == 3 // MARKED, KEPT, UNMARKED

    /**
     * Returns true if no tags are selected (treat as all).
     */
    fun isNoTagsSelected(): Boolean = selectedTags.isEmpty()

    /**
     * Returns true if all folders are selected (empty set means all).
     */
    fun isAllFoldersSelected(): Boolean = selectedFolders.isEmpty()
}
