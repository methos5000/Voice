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
      val nextBook = bookRepository.get(nextBookId)
      if (nextBook == null) {
        upNextBookStore.updateData { null }
        return@launch
      }
      currentBookStore.updateData { nextBookId }
      player.setMediaItem(mediaItemProvider.mediaItem(nextBook))
      player.prepare()
      upNextBookStore.updateData { null }
      player.play()
    }
  }
}
