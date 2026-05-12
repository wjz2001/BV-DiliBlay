package dev.aaa1115910.biliapi.entity

data class SubscribedCollectionMetadata(
    val id: Long,
    val title: String,
    val cover: String?,
    val upper: Upper?,
    val mediaCount: Int,
    val type: Int,
    val mtime: Long?
) {
    companion object {
        const val TYPE_SEASON = 21
        const val INVALID_SEASON_TITLE = "该合集已失效"

        fun fromHttpSubscriptionItem(
            item: dev.aaa1115910.biliapi.http.entity.user.subscription.SubscriptionItem
        ): SubscribedCollectionMetadata {
            return SubscribedCollectionMetadata(
                id = item.id,
                title = item.title,
                cover = item.cover,
                upper = item.upper?.let { Upper.fromHttpUpper(it) },
                mediaCount = item.mediaCount,
                type = item.type,
                mtime = item.mtime
            )
        }
    }
}

data class SubscribedCollectionData(
    val info: SubscribedCollectionMetadata?,
    val medias: List<SubscribedCollectionVideo>
) {
    companion object {
        fun fromHttpSubscriptionSeasonData(
            data: dev.aaa1115910.biliapi.http.entity.user.subscription.SubscriptionSeasonData
        ): SubscribedCollectionData {
            return SubscribedCollectionData(
                info = data.info?.let { SubscribedCollectionMetadata.fromHttpSubscriptionItem(it) },
                medias = data.medias.map { SubscribedCollectionVideo.fromHttpSubscriptionSeasonMedia(it) }
            )
        }

        fun fromHttpSeasonArchivesData(
            metadata: SubscribedCollectionMetadata,
            data: dev.aaa1115910.biliapi.http.entity.user.subscription.SeasonArchivesData
        ): SubscribedCollectionData {
            val total = data.page?.total ?: data.meta?.total ?: metadata.mediaCount
            return SubscribedCollectionData(
                info = metadata.copy(
                    title = data.meta?.name ?: metadata.title,
                    cover = data.meta?.cover ?: metadata.cover,
                    mediaCount = total
                ),
                medias = data.archives.map { SubscribedCollectionVideo.fromHttpSeasonArchive(it) }
            )
        }
    }
}

data class SubscribedCollectionVideo(
    val id: Long,
    val title: String,
    val cover: String,
    val duration: Int,
    val pubtime: Long?,
    val bvid: String?,
    val play: Int?,
    val danmaku: Int?
) {
    companion object {
        fun fromHttpSubscriptionSeasonMedia(
            media: dev.aaa1115910.biliapi.http.entity.user.subscription.SubscriptionSeasonMedia
        ): SubscribedCollectionVideo {
            return SubscribedCollectionVideo(
                id = media.id,
                title = media.title,
                cover = media.cover,
                duration = media.duration,
                pubtime = media.pubtime,
                bvid = media.bvid,
                play = media.cntInfo?.play,
                danmaku = media.cntInfo?.danmaku
            )
        }

        fun fromHttpSeasonArchive(
            archive: dev.aaa1115910.biliapi.http.entity.user.subscription.SeasonArchivesData.SeasonArchive
        ): SubscribedCollectionVideo {
            return SubscribedCollectionVideo(
                id = archive.aid,
                title = archive.title,
                cover = archive.pic,
                duration = archive.duration,
                pubtime = archive.pubdate,
                bvid = archive.bvid,
                play = archive.stat?.view,
                danmaku = archive.stat?.danmaku
            )
        }
    }
}
