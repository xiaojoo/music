package com.xiaojoo.lightplayer.videosource

import kotlinx.coroutines.delay

class FakeSource(
    override val id: String,
    override val capabilities: Set<Capability> = setOf(Capability.Search, Capability.Resolve),
    override val hosts: Set<String> = setOf("$id.example"),
    private val items: List<MusicVideo> = emptyList(),
    private val searchFailure: SourceFailure? = null,
    private val searchDelayMs: Long = 0,
    private val resolveDelaysMs: List<Long> = emptyList(),
    private val resolveFailures: List<SourceFailure> = emptyList(),
    private val playable: Playable = Playable("https://$id.example/x.mp4"),
) : MusicVideoSource {

    override val displayName: String get() = id

    var searchCalls = 0
        private set
    var resolveCalls = 0
        private set

    override suspend fun search(query: String, limit: Int): List<MusicVideo> {
        searchCalls++
        if (searchDelayMs > 0) delay(searchDelayMs)
        searchFailure?.let { throw SourceFailureException(it) }
        return items.filter { relevance(query, it.title, it.artist) > 0 }.take(limit)
    }

    override suspend fun resolve(video: MusicVideo): Playable {
        resolveCalls++
        resolveDelaysMs.getOrNull(resolveCalls - 1)?.let { delay(it) }
        resolveFailures.getOrNull(resolveCalls - 1)?.let { throw SourceFailureException(it) }
        return playable
    }
}

fun mv(sourceId: String, remoteId: String, title: String, artist: String = "周杰伦") =
    MusicVideo(sourceId, remoteId, title, artist, 240_000, "https://$sourceId.example/p/$remoteId")
