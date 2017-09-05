package commands.music

import com.sedmelluq.discord.lavaplayer.player.AudioPlayer
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager
import com.sedmelluq.discord.lavaplayer.player.event.AudioEventAdapter
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException
import com.sedmelluq.discord.lavaplayer.track.AudioTrack
import com.sedmelluq.discord.lavaplayer.track.AudioTrackEndReason
import com.sedmelluq.discord.lavaplayer.track.playback.AudioFrame
import com.wrapper.spotify.exceptions.BadRequestException
import main.managers
import main.playerManager
import main.spotifyApi
import net.dv8tion.jda.core.audio.AudioSendHandler
import net.dv8tion.jda.core.entities.Guild
import net.dv8tion.jda.core.entities.Member
import net.dv8tion.jda.core.entities.TextChannel
import net.dv8tion.jda.core.entities.User
import utils.*
import java.time.Instant
import java.util.*
import java.util.concurrent.LinkedBlockingDeque

class AudioPlayerSendHandler(private val audioPlayer: AudioPlayer) : AudioSendHandler {
    private var lastFrame: AudioFrame? = null

    override fun canProvide(): Boolean {
        lastFrame = audioPlayer.provide()
        return lastFrame != null
    }

    override fun provide20MsAudio(): ByteArray {
        return lastFrame!!.data
    }

    override fun isOpus(): Boolean {
        return true
    }
}

data class ArdentTrack(val author: String, val channel: String?, var track: AudioTrack) {
    private val votedToSkip = ArrayList<String>()

    fun addSkipVote(user: User): Boolean {
        if (votedToSkip.contains(user.id)) return false
        votedToSkip.add(user.id)
        return true
    }
}


class GuildMusicManager(manager: AudioPlayerManager, channel: TextChannel?, guild: Guild) {
    val scheduler: TrackScheduler
    internal val player: AudioPlayer = manager.createPlayer()

    init {
        scheduler = TrackScheduler(player, channel, guild)
        player.addListener(scheduler)
    }

    internal val sendHandler: AudioPlayerSendHandler get() = AudioPlayerSendHandler(player)
}

class ArdentMusicManager(val player: AudioPlayer, var textChannel: String? = null, var lastPlayedAt: Instant? = null) {
    var queue = LinkedBlockingDeque<ArdentTrack>()
    var current: ArdentTrack? = null

    fun getChannel(): TextChannel? {
        if (textChannel == null) return null
        return textChannel!!.toChannel()
    }

    fun setChannel(channel: TextChannel?) {
        textChannel = channel?.id
    }

    val isTrackCurrentlyPlaying: Boolean get() = current != null

    fun queue(track: ArdentTrack) {
        if (!player.startTrack(track.track, true)) queue.offer(track)
        else current = track
    }

    fun nextTrack() {
        val track = queue.poll()
        if (track != null) {
            val set: Boolean = track.track.position != 0.toLong()
            try {
                player.startTrack(track.track, false)
            } catch (e: Exception) {
                player.startTrack(track.track.makeClone(), false)
            }
            if (set && player.playingTrack != null) player.playingTrack.position = track.track.position
            current = track
        } else {
            player.startTrack(null, false)
            current = null
        }
    }

    fun addToQueue(track: ArdentTrack) {
        queue(track)
        lastPlayedAt = Instant.now()
    }

    fun resetQueue() {
        this.queue = LinkedBlockingDeque<ArdentTrack>()
    }

    fun shuffle() {
        val tracks = ArrayList<ArdentTrack>()
        tracks.addAll(queue)
        Collections.shuffle(tracks)
        queue = LinkedBlockingDeque(tracks)
    }

    fun removeFrom(user: User): Int {
        var count = 0
        val iterator = queue.iterator()
        while (iterator.hasNext()) {
            if (iterator.next().author == user.id) {
                count++
                iterator.remove()
            }
        }
        return count
    }

    val queueAsList: MutableList<ArdentTrack> get() = queue.toMutableList()

    fun addToBeginningOfQueue(track: ArdentTrack?) {
        if (track == null) return
        track.track = track.track.makeClone()
        queue.addFirst(track)
    }

    fun removeAt(num: Int?): Boolean {
        if (num == null) return false
        val track = queue.toList().getOrNull(num) ?: return false
        queue.removeFirstOccurrence(track)
        return true
    }
}

class TrackScheduler(player: AudioPlayer, var channel: TextChannel?, val guild: Guild) : AudioEventAdapter() {
    var manager: ArdentMusicManager = ArdentMusicManager(player, channel?.id)
    var autoplay = true
    override fun onTrackStart(player: AudioPlayer, track: AudioTrack) {
        autoplay = true
        if (channel?.guild?.getData()?.musicSettings?.announceNewMusic == true) {
            val builder = guild.selfMember.embed("Now Playing: ${track.info.title}")
            builder.setThumbnail("https://s-media-cache-ak0.pinimg.com/736x/69/96/5c/69965c2849ec9b7148a5547ce6714735.jpg")
            builder.addField("Title", track.info.title, true)
                    .addField("Author", track.info.author, true)
                    .addField("Duration", track.getDurationFancy(), true)
                    .addField("URL", track.info.uri, true)
                    .addField("Is Stream", track.info.isStream.toString(), true)
            channel?.send(builder)
        }
    }

    override fun onTrackEnd(player: AudioPlayer, track: AudioTrack, endReason: AudioTrackEndReason) {
        if (manager.queue.size == 0 && guild.getData().musicSettings.autoQueueSongs && guild.selfMember.voiceChannel() != null && autoplay) {
            try {
                val songSearch = spotifyApi.searchTracks(track.info.title.rmCharacters("()").rmCharacters("[]").replace("ft.", "").replace("feat", "").replace("feat.", "")).build()
                val get = songSearch.get()
                if (get.items.size == 0) {
                    channel?.send("Couldn't find this song in the Spotify database, no autoplay available.")
                    return
                }
                val songId = get.items[0].id
                spotifyApi.getRecommendations().tracks(mutableListOf(songId)).build().get()[0].name.load(guild.selfMember,
                        channel ?: guild.defaultChannel!!, null, false, autoplay = true)
            } catch (ignored: Exception) {
            }
        } else manager.nextTrack()
    }

    private fun String.rmCharacters(characterSymbol: String): String {
        return when {
            characterSymbol.contains("[]") -> this.replace("\\s*\\[[^\\]]*\\]\\s*".toRegex(), " ")
            characterSymbol.contains("{}") -> this.replace("\\s*\\{[^\\}]*\\}\\s*".toRegex(), " ")
            else -> this.replace("\\s*\\([^\\)]*\\)\\s*".toRegex(), " ")
        }
    }

    override fun onTrackStuck(player: AudioPlayer, track: AudioTrack, thresholdMs: Long) {
        guild.getGuildAudioPlayer(channel).scheduler.manager.nextTrack()
        channel?.send("${Emoji.BALLOT_BOX_WITH_CHECK} The player got stuck... attempting to skip now now (this is Discord's fault) - If you encounter this multiple " +
                "times, please type ${guild.getPrefix()}leave")
    }

    override fun onTrackException(player: AudioPlayer, track: AudioTrack, exception: FriendlyException) {
        onException(exception)
    }


    private fun onException(exception: FriendlyException) {
        manager.current = null
        manager.nextTrack()
        try {
            manager.getChannel()?.sendMessage("I wasn't able to play that track, skipping... **Reason: **${exception.localizedMessage}")?.queue()
        } catch (e: Exception) {
            e.log()
        }
    }
}

fun AudioTrack.getDurationFancy(): String {
    val length = info.length
    val seconds = (length / 1000).toInt()
    val minutes = seconds / 60
    val hours = minutes / 60
    return "[${String.format("%02d", hours % 60)}:${String.format("%02d", minutes % 60)}:${String.format("%02d", seconds % 60)}]"
}

fun ArrayList<ArdentTrack>.getDuration(): String {
    val length: Long = this.map { it.track.duration }.sum()
    val seconds = (length / 1000).toInt()
    val minutes = seconds / 60
    val hours = minutes / 60
    return "[${String.format("%02d", hours % 60)}:${String.format("%02d", minutes % 60)}:${String.format("%02d", seconds % 60)}]"
}

fun AudioTrack.getCurrentTime(): String {
    val current = position
    val seconds = (current / 1000).toInt()
    val minutes = seconds / 60
    val hours = minutes / 60

    val length = info.length
    val lengthSeconds = (length / 1000).toInt()
    val lengthMinutes = lengthSeconds / 60
    val lengthHours = lengthMinutes / 60

    return "[${String.format("%02d", hours % 60)}:${String.format("%02d", minutes % 60)}:${String
            .format("%02d", seconds % 60)} / ${String.format("%02d", lengthHours % 60)}:${String
            .format("%02d", lengthMinutes % 60)}:${String.format("%02d", lengthSeconds % 60)}]"
}

@Synchronized
fun Guild.getGuildAudioPlayer(channel: TextChannel?): GuildMusicManager {
    val guildId = id.toLong()
    var musicManager = managers[guildId]
    if (musicManager == null) {
        musicManager = GuildMusicManager(playerManager, channel, this)
        audioManager.sendingHandler = musicManager.sendHandler
        managers.put(guildId, musicManager)
    } else {
        val ardentMusicManager = musicManager.scheduler.manager
        if (ardentMusicManager.getChannel() == null) {
            ardentMusicManager.setChannel(channel)
        }
    }
    return musicManager
}

val teenParty = mutableListOf<AudioTrack>()
val todaysTopHits = mutableListOf<AudioTrack>()
val rapCaviar = mutableListOf<AudioTrack>()
val powerGaming = mutableListOf<AudioTrack>()
val usTop50 = mutableListOf<AudioTrack>()
val vivaLatino = mutableListOf<AudioTrack>()
val hotCountry = mutableListOf<AudioTrack>()

fun String.getSpotifyPlaylist(channel: TextChannel, member: Member) {
    when (this) {
        "https://open.spotify.com/user/spotify/playlist/37i9dQZF1DXcBWIGoYBM5M" -> {
            if (todaysTopHits.size > 0) todaysTopHits.forEach { play(channel, member, ArdentTrack(member.id(), channel.id, it.makeClone())) }
            else searchAndLoadPlaylists(channel, member)
        }
        "https://open.spotify.com/user/spotify/playlist/37i9dQZF1DX0XUsuxWHRQd" -> {
            if (rapCaviar.size > 0) rapCaviar.forEach { play(channel, member, ArdentTrack(member.id(), channel.id, it.makeClone())) }
            else searchAndLoadPlaylists(channel, member)
        }
        "https://open.spotify.com/user/spotify/playlist/37i9dQZF1DX6taq20FeuKj" -> {
            if (powerGaming.size > 0) powerGaming.forEach { play(channel, member, ArdentTrack(member.id(), channel.id, it.makeClone())) }
            else searchAndLoadPlaylists(channel, member)
        }
        "https://open.spotify.com/user/spotify/playlist/37i9dQZF1DX1N5uK98ms5p" -> {
            if (teenParty.size > 0) teenParty.forEach { play(channel, member, ArdentTrack(member.id(), channel.id, it.makeClone())) }
            else searchAndLoadPlaylists(channel, member)
        }
        "https://open.spotify.com/user/spotifycharts/playlist/37i9dQZEVXbLRQDuF5jeBp" -> {
            if (usTop50.size > 0) usTop50.forEach { play(channel, member, ArdentTrack(member.id(), channel.id, it.makeClone())) }
            else searchAndLoadPlaylists(channel, member)
        }
        "https://open.spotify.com/user/spotify/playlist/37i9dQZF1DX10zKzsJ2jva" -> {
            if (vivaLatino.size > 0) vivaLatino.forEach { play(channel, member, ArdentTrack(member.id(), channel.id, it.makeClone())) }
            else searchAndLoadPlaylists(channel, member)
        }
        "https://open.spotify.com/user/spotify/playlist/37i9dQZF1DX1lVhptIYRda" -> {
            if (hotCountry.size > 0) hotCountry.forEach { play(channel, member, ArdentTrack(member.id(), channel.id, it.makeClone())) }
            else searchAndLoadPlaylists(channel, member)
        }
        else -> searchAndLoadPlaylists(channel, member)
    }
}

fun String.searchAndLoadPlaylists(channel: TextChannel, member: Member) {
    try {
        val info = this.removePrefix("https://open.spotify.com/user/").split("/playlist/")
        val playlist = spotifyApi.getPlaylist(info[0], info[1]).build().get()
        if (playlist == null || playlist.tracks.items.size == 0) channel.send("No playlist was found with this query")
        else {
            channel.sendMessage("Beginning track loading from Spotify playlist **${playlist.name}**... This could take a few minutes. I'll add progress bars as the " +
                    "playlist is processed")
                    .queue { message ->
                        var percentage = 0.1
                        var current = 0
                        playlist.tracks.items.forEach { playlistTrack ->
                            "${playlistTrack.track.name} ${playlistTrack.track.artists[0].name}".getSingleTrack(member.guild, { foundTrack ->
                                current++
                                val ardentTrack = ArdentTrack(member.id(), channel.id, foundTrack)
                                play(channel, member, ardentTrack)
                                when (this) {
                                    "https://open.spotify.com/user/spotify/playlist/37i9dQZF1DXcBWIGoYBM5M" -> todaysTopHits.add(foundTrack)
                                    "https://open.spotify.com/user/spotify/playlist/37i9dQZF1DX0XUsuxWHRQd" -> rapCaviar.add(foundTrack)
                                    "https://open.spotify.com/user/spotify/playlist/37i9dQZF1DX6taq20FeuKj" -> powerGaming.add(foundTrack)
                                    "https://open.spotify.com/user/spotify/playlist/37i9dQZF1DX1N5uK98ms5p" -> teenParty.add(foundTrack)
                                    "https://open.spotify.com/user/spotifycharts/playlist/37i9dQZEVXbLRQDuF5jeBp" -> usTop50.add(foundTrack)
                                    "https://open.spotify.com/user/spotify/playlist/37i9dQZF1DX10zKzsJ2jva" -> vivaLatino.add(foundTrack)
                                    "https://open.spotify.com/user/spotify/playlist/37i9dQZF1DX1lVhptIYRda" -> hotCountry.add(foundTrack)
                                }
                                if (current / playlist.tracks.items.size.toDouble() >= percentage && percentage <= 1) {
                                    percentage += 0.2
                                    when (percentage) {
                                        0.1 -> message.addReaction(Emoji.KEYCAP_DIGIT_ONE.symbol).queue()
                                        0.2 -> message.addReaction(Emoji.KEYCAP_DIGIT_TWO.symbol).queue()
                                        0.3 -> message.addReaction(Emoji.KEYCAP_DIGIT_THREE.symbol).queue()
                                        0.4 -> message.addReaction(Emoji.KEYCAP_DIGIT_FOUR.symbol).queue()
                                        0.5 -> message.addReaction(Emoji.KEYCAP_DIGIT_FIVE.symbol).queue()
                                        0.6 -> message.addReaction(Emoji.KEYCAP_DIGIT_SIX.symbol).queue()
                                        0.7 -> message.addReaction(Emoji.KEYCAP_DIGIT_SEVEN.symbol).queue()
                                        0.8 -> message.addReaction(Emoji.KEYCAP_DIGIT_EIGHT.symbol).queue()
                                        0.9 -> message.addReaction(Emoji.KEYCAP_DIGIT_NINE.symbol).queue()
                                        1.0 -> message.addReaction(Emoji.KEYCAP_TEN.symbol).queue()
                                    }
                                }
                                if (current / playlist.tracks.items.size.toDouble() == 1.0) {
                                    message.addReaction(Emoji.HEAVY_CHECK_MARK.symbol).queue()
                                }
                                Thread.sleep(750)
                            })
                        }
                    }
        }
    } catch (e: BadRequestException) {
        channel.send("You specified an invalid url.. Please try again after checking the link")
    }
}