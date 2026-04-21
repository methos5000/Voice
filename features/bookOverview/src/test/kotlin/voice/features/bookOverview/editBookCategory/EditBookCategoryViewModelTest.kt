package voice.features.bookOverview.editBookCategory

import io.kotest.matchers.shouldBe
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test
import voice.core.data.BookContent
import voice.core.data.BookId
import voice.core.data.repo.BookRepository
import voice.features.bookOverview.MemoryDataStore
import voice.features.bookOverview.book
import voice.features.bookOverview.bottomSheet.BottomSheetItem
import voice.features.bookOverview.chapter

class EditBookCategoryViewModelTest {

  private val repo = mockk<BookRepository>()

  private fun viewModel(upNextId: BookId? = null) = EditBookCategoryViewModel(
    repo = repo,
    upNextBookStore = MemoryDataStore(upNextId),
  )

  @Test
  fun `items for current book`() = runTest {
    val book = book() // time=42, first chapter → CURRENT
    coEvery { repo.get(book.id) } returns book
    viewModel().items(book.id) shouldBe listOf(
      BottomSheetItem.BookCategoryMarkAsNotStarted,
      BottomSheetItem.BookCategoryMarkAsCompleted,
    )
  }

  @Test
  fun `items for not-started book`() = runTest {
    val book = book(time = 0) // positionInChapter=0 → NOT_STARTED
    coEvery { repo.get(book.id) } returns book
    viewModel().items(book.id) shouldBe listOf(
      BottomSheetItem.PlayNext,
      BottomSheetItem.BookCategoryMarkAsCurrent,
      BottomSheetItem.BookCategoryMarkAsCompleted,
    )
  }

  @Test
  fun `items for finished book`() = runTest {
    val chapters = listOf(chapter(), chapter())
    val book = book(chapters = chapters, currentChapter = chapters.last().id, time = chapters.last().duration)
    coEvery { repo.get(book.id) } returns book
    viewModel().items(book.id) shouldBe listOf(
      BottomSheetItem.PlayNext,
      BottomSheetItem.BookCategoryMarkAsCurrent,
      BottomSheetItem.BookCategoryMarkAsNotStarted,
    )
  }

  @Test
  fun `items for up-next book returns ClearUpNext`() = runTest {
    val book = book()
    coEvery { repo.get(book.id) } returns book
    viewModel(upNextId = book.id).items(book.id) shouldBe listOf(BottomSheetItem.ClearUpNext)
  }

  @Test
  fun `items when book not found`() = runTest {
    val bookId = BookId("missing")
    coEvery { repo.get(bookId) } returns null
    viewModel().items(bookId) shouldBe emptyList()
  }

  @Test
  fun `PlayNext stores bookId in up-next store`() = runTest {
    val book = book()
    coEvery { repo.get(book.id) } returns book
    val upNextStore = MemoryDataStore<BookId?>(null)
    val vm = EditBookCategoryViewModel(repo, upNextStore)

    vm.onItemClick(book.id, BottomSheetItem.PlayNext)

    upNextStore.data.first() shouldBe book.id
  }

  @Test
  fun `MarkAsCurrent sets first chapter position to 1`() = runTest {
    val book = book()
    coEvery { repo.get(book.id) } returns book
    val updateSlot = slot<(BookContent) -> BookContent>()
    coEvery { repo.updateBook(book.id, capture(updateSlot)) } just Runs

    viewModel().onItemClick(book.id, BottomSheetItem.BookCategoryMarkAsCurrent)

    val updated = updateSlot.captured(book.content)
    updated.currentChapter shouldBe book.chapters.first().id
    updated.positionInChapter shouldBe 1L
  }

  @Test
  fun `MarkAsNotStarted sets first chapter position to 0`() = runTest {
    val book = book()
    coEvery { repo.get(book.id) } returns book
    val updateSlot = slot<(BookContent) -> BookContent>()
    coEvery { repo.updateBook(book.id, capture(updateSlot)) } just Runs

    viewModel().onItemClick(book.id, BottomSheetItem.BookCategoryMarkAsNotStarted)

    val updated = updateSlot.captured(book.content)
    updated.currentChapter shouldBe book.chapters.first().id
    updated.positionInChapter shouldBe 0L
  }

  @Test
  fun `MarkAsCompleted sets last chapter position to its duration`() = runTest {
    val chapters = listOf(chapter(), chapter())
    val book = book(chapters = chapters)
    coEvery { repo.get(book.id) } returns book
    val updateSlot = slot<(BookContent) -> BookContent>()
    coEvery { repo.updateBook(book.id, capture(updateSlot)) } just Runs

    viewModel().onItemClick(book.id, BottomSheetItem.BookCategoryMarkAsCompleted)

    val updated = updateSlot.captured(book.content)
    updated.currentChapter shouldBe chapters.last().id
    updated.positionInChapter shouldBe chapters.last().duration
  }

  @Test
  fun `recategorizing up-next book clears up-next store`() = runTest {
    val book = book()
    coEvery { repo.get(book.id) } returns book
    coEvery { repo.updateBook(any(), any()) } just Runs
    val upNextStore = MemoryDataStore<BookId?>(book.id)
    val vm = EditBookCategoryViewModel(repo, upNextStore)

    vm.onItemClick(book.id, BottomSheetItem.BookCategoryMarkAsCurrent)

    upNextStore.data.first() shouldBe null
  }

  @Test
  fun `recategorizing a different book does not clear up-next store`() = runTest {
    val book = book()
    val otherBookId = BookId("other")
    coEvery { repo.get(book.id) } returns book
    coEvery { repo.updateBook(any(), any()) } just Runs
    val upNextStore = MemoryDataStore<BookId?>(otherBookId)
    val vm = EditBookCategoryViewModel(repo, upNextStore)

    vm.onItemClick(book.id, BottomSheetItem.BookCategoryMarkAsCurrent)

    upNextStore.data.first() shouldBe otherBookId
  }
}
