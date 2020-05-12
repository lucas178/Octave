package gg.octave.bot.music

import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager
import com.sedmelluq.discord.lavaplayer.source.bandcamp.BandcampAudioSourceManager
import com.sedmelluq.discord.lavaplayer.source.beam.BeamAudioSourceManager
import com.sedmelluq.discord.lavaplayer.source.getyarn.GetyarnAudioSourceManager
import com.sedmelluq.discord.lavaplayer.source.soundcloud.SoundCloudAudioSourceManager
import com.sedmelluq.discord.lavaplayer.source.twitch.TwitchStreamAudioSourceManager
import com.sedmelluq.discord.lavaplayer.source.vimeo.VimeoAudioSourceManager
import com.sedmelluq.discord.lavaplayer.source.youtube.YoutubeAudioSourceManager
import com.sedmelluq.discord.lavaplayer.track.playback.AudioFrameBufferFactory
import com.sedmelluq.discord.lavaplayer.track.playback.NonAllocatingAudioFrameBuffer
import com.sedmelluq.lava.extensions.youtuberotator.YoutubeIpRotatorSetup
import com.sedmelluq.lava.extensions.youtuberotator.planner.AbstractRoutePlanner
import com.sedmelluq.lava.extensions.youtuberotator.planner.RotatingNanoIpRoutePlanner
import com.sedmelluq.lava.extensions.youtuberotator.tools.ip.Ipv6Block
import gg.octave.bot.Launcher
import gg.octave.bot.Launcher.shardManager
import gg.octave.bot.db.OptionsRegistry.ofGuild
import gg.octave.bot.music.sources.caching.CachingSourceManager
import gg.octave.bot.music.sources.spotify.SpotifyAudioSourceManager
import io.sentry.Sentry
import net.dv8tion.jda.api.entities.Guild
import org.slf4j.LoggerFactory
import java.net.InetAddress
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import javax.annotation.Nonnull

class PlayerRegistry(private val bot: Launcher, val executor: ScheduledExecutorService) {
    private val log = LoggerFactory.getLogger("PlayerRegistry")

    val registry: MutableMap<Long, MusicManager>
    val playerManager: AudioPlayerManager

    @Nonnull
    @Throws(MusicLimitException::class)
    operator fun get(guild: Guild?): MusicManager {
        var manager = registry[guild!!.idLong]

        if (manager == null) {
            if (size() >= bot.configuration.musicLimit && !ofGuild(guild).isPremium) {
                throw MusicLimitException()
            }

            manager = MusicManager(bot, guild.id, this, playerManager)
            registry[guild.idLong] = manager
        }

        return manager
    }

    fun getExisting(id: Long): MusicManager? {
        return registry[id]
    }

    fun getExisting(guild: Guild?): MusicManager? {
        return getExisting(guild!!.idLong)
    }

    fun destroy(id: Long) {
        val manager = registry[id]
        if (manager != null) {
            manager.destroy()
            registry.remove(id)
        }
    }

    fun destroy(guild: Guild?) {
        destroy(guild!!.idLong)
    }

    operator fun contains(id: Long): Boolean {
        return registry.containsKey(id)
    }

    operator fun contains(guild: Guild): Boolean {
        return registry.containsKey(guild.idLong)
    }

    fun shutdown() {
        clear(true)
    }

    fun clear(force: Boolean) {
        log.info("Cleaning up players (forceful: $force)")
        val iterator = registry.entries.iterator()

        while (iterator.hasNext()) {
            val entry = iterator.next()
            try {
                //Guild was long gone, dangling manager,
                val musicManager: MusicManager = entry.value
                if (shardManager.getGuildById(musicManager.guildId) == null) {
                    return iterator.remove()
                }
                if (force || !musicManager.guild!!.selfMember.voiceState!!.inVoiceChannel() || musicManager.player.playingTrack == null) {
                    log.debug("Cleaning player {}", musicManager.guild!!.id)
                    musicManager.scheduler.queue.clear()
                    musicManager.destroy()
                    iterator.remove()
                }
            } catch (e: Exception) {
                log.warn("Exception occured while trying to clean up id ${entry.key}", e)
            }
        }

        log.info("Finished cleaning up players.")
    }

    fun size(): Int {
        return registry.size
    }

    init {
        registry = ConcurrentHashMap(bot.configuration.musicLimit)
        executor.scheduleAtFixedRate({ clear(false) }, 20, 10, TimeUnit.MINUTES)

        playerManager = DefaultAudioPlayerManager()
        playerManager.setFrameBufferDuration(5000)
        playerManager.getConfiguration().isFilterHotSwapEnabled = true
        playerManager.getConfiguration().frameBufferFactory =
                AudioFrameBufferFactory{ bufferDuration, format, stopping -> NonAllocatingAudioFrameBuffer(bufferDuration, format, stopping) }

        val youtubeAudioSourceManager = YoutubeAudioSourceManager(true)
        val config = bot.configuration
        val credentials = bot.credentials

        if (config.ipv6Block.isNotEmpty()) {
            var planner: AbstractRoutePlanner
            val block = config.ipv6Block
            val blocks = listOf(Ipv6Block(block))

            if (config.ipv6Exclude.isEmpty()) {
                planner = RotatingNanoIpRoutePlanner(blocks)
            } else {
                try {
                    val blacklistedGW = InetAddress.getByName(config.ipv6Exclude)
                    planner = RotatingNanoIpRoutePlanner(blocks) { it != blacklistedGW }
                } catch (ex: Exception) {
                    planner = RotatingNanoIpRoutePlanner(blocks)
                    Sentry.capture(ex)
                    ex.printStackTrace()
                }
            }

            YoutubeIpRotatorSetup(planner)
                    .forSource(youtubeAudioSourceManager)
                    .setup()
        }

        val spotifyAudioSourceManager = SpotifyAudioSourceManager(
                credentials.spotifyClientId,
                credentials.spotifyClientSecret,
                youtubeAudioSourceManager
        )

        playerManager.registerSourceManager(CachingSourceManager())
        playerManager.registerSourceManager(spotifyAudioSourceManager)
        playerManager.registerSourceManager(youtubeAudioSourceManager)
        playerManager.registerSourceManager(SoundCloudAudioSourceManager.createDefault())
        playerManager.registerSourceManager(GetyarnAudioSourceManager())
        playerManager.registerSourceManager(BandcampAudioSourceManager())
        playerManager.registerSourceManager(VimeoAudioSourceManager())
        playerManager.registerSourceManager(TwitchStreamAudioSourceManager())
        playerManager.registerSourceManager(BeamAudioSourceManager())
    }
}