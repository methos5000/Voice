package voice.features.bookOverview.editBookCategory

import androidx.datastore.core.DataStore
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.first
import voice.core.data.BookId
import voice.core.data.repo.BookRepository
import voice.core.data.store.UpNextBookStore
import voice.core.featureflag.FeatureFlag
import voice.core.featureflag.UpNextFeatureFlagQualifier
import voice.features.bookOverview.bottomSheet.BottomSheetItem
import voice.features.bookOverview.bottomSheet.BottomSheetItemViewModel
import voice.features.bookOverview.di.BookOverviewScope
import voice.features.bookOverview.overview.BookOverviewCategory
import voice.features.bookOverview.overview.category
import java.time.Instant

@SingleIn(BookOverviewScope::class)
@ContributesIntoSet(BookOverviewScope::class)
class EditBookCategoryViewModel(
  private val repo: BookRepository,
  @UpNextBookStore private val upNextBookStore: DataStore<BookId?>,
  @UpNextFeatureFlagQualifier private val upNextFeatureFlag: FeatureFlag<Boolean>,
) : BottomSheetItemViewModel {

  override suspend fun items(bookId: BookId): List<BottomSheetItem> {
    val book = repo.get(bookId) ?: return emptyList()
    val upNextEnabled = upNextFeatureFlag.get()
    val isUpNext = upNextEnabled && upNextBookStore.data.first() == bookId
    if (isUpNext) return listOf(BottomSheetItem.ClearUpNext)
    return when (book.category) {
      BookOverviewCategory.CURRENT -> listOf(
        BottomSheetItem.BookCategoryMarkAsNotStarted,
        BottomSheetItem.BookCategoryMarkAsCompleted,
      )
      BookOverviewCategory.NOT_STARTED -> listOfNotNull(
        if (upNextEnabled) BottomSheetItem.PlayNext else null,
        BottomSheetItem.BookCategoryMarkAsCurrent,
        BottomSheetItem.BookCategoryMarkAsCompleted,
      )
      BookOverviewCategory.FINISHED -> listOfNotNull(
        if (upNextEnabled) BottomSheetItem.PlayNext else null,
        BottomSheetItem.BookCategoryMarkAsCurrent,
        BottomSheetItem.BookCategoryMarkAsNotStarted,
      )
      BookOverviewCategory.UP_NEXT -> error("UP_NEXT is a UI-only grouping, never set on Book.category")
    }
  }

  override suspend fun onItemClick(
    bookId: BookId,
    item: BottomSheetItem,
  ) {
    if (item == BottomSheetItem.PlayNext) {
      upNextBookStore.updateData { bookId }
      return
    }
    if (item == BottomSheetItem.ClearUpNext) {
      upNextBookStore.updateData { null }
      return
    }

    val book = repo.get(bookId) ?: return

    val (currentChapter, positionInChapter) = when (item) {
      BottomSheetItem.BookCategoryMarkAsCurrent -> {
        book.chapters.first().id to 1L
      }
      BottomSheetItem.BookCategoryMarkAsNotStarted -> {
        book.chapters.first().id to 0L
      }
      BottomSheetItem.BookCategoryMarkAsCompleted -> {
        val lastChapter = book.chapters.last()
        lastChapter.id to lastChapter.duration
      }
      else -> return
    }

    repo.updateBook(book.id) {
      it.copy(
        currentChapter = currentChapter,
        positionInChapter = positionInChapter,
        lastPlayedAt = Instant.now(),
      )
    }

    if (upNextBookStore.data.first() == bookId) {
      upNextBookStore.updateData { null }
    }
  }
}
