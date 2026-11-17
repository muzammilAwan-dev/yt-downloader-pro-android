package io.github.muzammilawan.clipharbor.feature.queue

import io.github.muzammilawan.clipharbor.download.TaskEntity
import io.github.muzammilawan.clipharbor.download.TaskRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

/**
 * Read-only view of the durable task queue. Writes are HomeController's job; this class only exposes
 * [TaskRepository.observeAll] as a hot StateFlow for the UI.
 */
class QueueViewModel(
    scope: CoroutineScope,
    private val repository: TaskRepository,
) {
    val tasks: StateFlow<List<TaskEntity>> =
        repository.observeAll().stateIn(scope, SharingStarted.Eagerly, emptyList())
}
