package dev.aaa1115910.biliapi.repositories

import dev.aaa1115910.biliapi.entity.SubscribedCollectionData
import dev.aaa1115910.biliapi.entity.SubscribedCollectionMetadata
import dev.aaa1115910.biliapi.http.BiliHttpApi
import org.koin.core.annotation.Single

@Single
class SubscriptionRepository(
    private val authRepository: AuthRepository
) {
    suspend fun getSubscribedCollections(
        mid: Long,
        pageSize: Int = 20,
        pageNumber: Int = 1
    ): List<SubscribedCollectionMetadata> {
        val result = mutableListOf<SubscribedCollectionMetadata>()
        var currentPage = pageNumber
        var hasMore: Boolean

        do {
            val data = BiliHttpApi.getCollectedFavoriteFolders(
                mid = mid,
                pageSize = pageSize,
                pageNumber = currentPage,
                sessData = authRepository.sessionData ?: ""
            ).getResponseData()

            result += data.list
                .filter {
                    it.type == SubscribedCollectionMetadata.TYPE_SEASON &&
                            it.id > 0L &&
                            it.title.trim() != SubscribedCollectionMetadata.INVALID_SEASON_TITLE
                }
                .map { SubscribedCollectionMetadata.fromHttpSubscriptionItem(it) }

            hasMore = data.hasMore
            currentPage++
        } while (hasMore)

        return result
    }

    suspend fun getSubscribedCollectionData(
        metadata: SubscribedCollectionMetadata,
        pageSize: Int = 20,
        pageNumber: Int = 1
    ): SubscribedCollectionData {
        metadata.upper?.mid?.let { mid ->
            val data = BiliHttpApi.getSeasonArchivesList(
                mid = mid,
                seasonId = metadata.id,
                pageSize = pageSize,
                pageNumber = pageNumber,
                sessData = authRepository.sessionData ?: ""
            ).getResponseData()

            return SubscribedCollectionData.fromHttpSeasonArchivesData(
                metadata = metadata,
                data = data
            )
        }

        val data = BiliHttpApi.getFavoriteSeasonList(
            seasonId = metadata.id,
            pageSize = pageSize,
            pageNumber = pageNumber,
            sessData = authRepository.sessionData ?: ""
        ).getResponseData()

        return SubscribedCollectionData.fromHttpSubscriptionSeasonData(data)
    }
}
