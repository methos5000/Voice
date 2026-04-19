package voice.navigation

sealed interface NavigationCommand {
  data object GoBack : NavigationCommand
  data class GoTo(val destination: Destination) : NavigationCommand
  /**
   * Swaps the top destination in place without growing the back stack.
   * Assumes the back stack is non-empty; on an empty stack the removal silently no-ops and this
   * degenerates to a push.
   */
  data class ReplaceTop(val destination: Destination.Compose) : NavigationCommand
  data class SetRoot(val root: Destination.Compose) : NavigationCommand
}
