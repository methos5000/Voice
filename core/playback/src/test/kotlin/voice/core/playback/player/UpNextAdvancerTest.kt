package voice.core.playback.player

import androidx.media3.common.Player
import io.kotest.matchers.shouldBe
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4
import voice.core.data.BookId
import voice.core.data.Chapter
import voice.core.data.ChapterId
import voice.core.data.repo.BookRepository
import voice.core.playback.MemoryDataStore
import voice.core.playback.session.MediaItemProvider
import voice.core.playback.session.search.book
import java.time.Instant
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class UpNextAdvancerTest {

  private val upNextStore = MemoryDataStore<BookId?>(null)
  private val currentStore = MemoryDataStore<BookId?>(null)
  private val repo = mockk<BookRepository>()
  private val mediaItemProvider = mockk<MediaItemProvider>(relaxed = true)
  private val scope = TestScope()
  private val player = mockk<Player>(relaxed = true)

  private val advancer = UpNextAdvancer(
    upNextBookStore = upNextStore,
    currentBookStore = currentStore,
    bookRepository = repo,
    mediaItemProvider = mediaItemProvider,
    scope = scope,
  ).also { it.attachTo(player) }

  @Test
  fun `no up-next book does nothing`() = scope.runTest {
    advancer.onPlaybackStateChanged(Player.STATE_ENDED)
    advanceUntilIdle()
    verify(exactly = 0) { player.play() }
  }

  @Test
  fun `up-next book found advances playback`() = scope.runTest {
    val targetBook = book(listOf(chapter(), chapter()))
    upNextStore.updateData { targetBook.id }
    coEvery { repo.get(targetBook.id) } returns targetBook
    coEvery { repo.updateBook(any(), any()) } just Runs

    advancer.onPlaybackStateChanged(Player.STATE_ENDED)
    advanceUntilIdle()

    currentStore.data.first() shouldBe targetBook.id
    upNextStore.data.first() shouldBe null
    verify { player.play() }
  }

  @Test
  fun `up-next book not in repository clears store`() = scope.runTest {
    val bookId = BookId(UUID.randomUUID().toString())
    upNextStore.updateData { bookId }
    coEvery { repo.get(bookId) } returns null

    advancer.onPlaybackStateChanged(Player.STATE_ENDED)
    advanceUntilIdle()

    upNextStore.data.first() shouldBe null
    verify(exactly = 0) { player.play() }
  }

  @Test
  fun `non-ended playback state is ignored`() = scope.runTest {
    val targetBook = book(listOf(chapter(), chapter()))
    upNextStore.updateData { targetBook.id }

    advancer.onPlaybackStateChanged(Player.STATE_BUFFERING)
    advanceUntilIdle()

    verify(exactly = 0) { player.play() }
  }
}

private fun chapter() = Chapter(
  id = ChapterId(UUID.randomUUID().toString()),
  name = UUID.randomUUID().toString(),
  duration = 10_000,
  fileLastModified = Instant.EPOCH,
  markData = emptyList(),
)
