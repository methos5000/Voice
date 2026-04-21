package voice.core.playback.player

import androidx.datastore.core.DataStore
import androidx.media3.common.Player
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import voice.core.data.BookId
import voice.core.data.repo.BookRepository
import voice.core.data.store.CurrentBookStore
import voice.core.data.store.UpNextBookStore
import voice.core.playback.session.MediaItemProvider
import voice.core.logging.api.Logger

/**
 * Listens for [Player.STATE_ENDED] and advances to the book queued in [UpNextBookStore].
 */
@Inject
class UpNextAdvancer(
  @UpNextBookStore private val upNextBookStore: DataStore<BookId?>,
  @CurrentBookStore private val currentBookStore: DataStore<BookId?>,
  private val bookRepository: BookRepository,
  private val mediaItemProvider: MediaItemProvider,
  private val scope: CoroutineScope,
) : Player.Listener {

  private lateinit var player: Player

  fun attachTo(player: Player) {
    if (this::player.isInitialized) this.player.removeListener(this)
    this.player = player
    player.addListener(this)
  }

  override fun onPlaybackStateChanged(playbackState: Int) {
    if (playbackState != Player.STATE_ENDED) return
    scope.launch {
      val nextBookId = upNextBookStore.data.first() ?: return@launch
      val nextBook = try {
        bookRepository.get(nextBookId)
      } catch (e: Exception) {
        Logger.w(e, "Failed to load up-next book $nextBookId")
        null
      }
      // Clear before writing CurrentBookStore so the overview never sees the same book in both buckets.
      upNextBookStore.updateData { null }
      if (nextBook == null) return@launch
      currentBookStore.updateData { nextBookId }
      // The raw ExoPlayer needs chapter-level MediaItems (with sourceUri). The book-level
      // MediaItem is a browsable node with no URI and would NPE in ProgressiveMediaSource.
      player.setMediaItems(
        mediaItemProvider.chapters(nextBook),
        nextBook.content.currentChapterIndex,
        nextBook.content.positionInChapter,
      )
      player.prepare()
      player.play()
    }
  }
}
