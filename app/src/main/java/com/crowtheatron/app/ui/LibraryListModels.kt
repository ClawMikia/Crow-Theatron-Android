package com.crowtheatron.app.ui

import com.crowtheatron.app.data.VideoEntity

sealed interface LibraryListItem {
    data class Header(
        val folder: String,
        val videoCount: Int,
        var isExpanded: Boolean = true
    ) : LibraryListItem
    data class VideoRow(val entity: VideoEntity) : LibraryListItem
}

fun buildGroupedItems(
    videos: List<VideoEntity>,
    expandedFolders: Set<String> = emptySet(),
    allExpandedInitially: Boolean = true
): List<LibraryListItem> {
    if (videos.isEmpty()) return emptyList()
    val byFolder = videos.groupBy { it.folderGroup }.toSortedMap(String.CASE_INSENSITIVE_ORDER)
    val out = ArrayList<LibraryListItem>()
    for ((folder, list) in byFolder) {
        val expanded = if (expandedFolders.isEmpty() && allExpandedInitially) true else expandedFolders.contains(folder)
        val header = LibraryListItem.Header(folder, list.size, expanded)
        out.add(header)
        if (expanded) {
            list.sortedBy { it.title.lowercase() }.forEach { out.add(LibraryListItem.VideoRow(it)) }
        }
    }
    return out
}

fun playlistIdsInOrder(items: List<LibraryListItem>): LongArray {
    return items.filterIsInstance<LibraryListItem.VideoRow>().map { it.entity.id }.toLongArray()
}
