package events

import net.dv8tion.jda.core.entities.ChannelType
import net.dv8tion.jda.core.entities.Guild
import net.dv8tion.jda.core.entities.Member
import net.dv8tion.jda.core.entities.TextChannel
import net.dv8tion.jda.core.events.message.MessageReceivedEvent
import net.dv8tion.jda.core.hooks.SubscribeEvent
import utils.*
import java.awt.Color
import java.util.concurrent.Executors

class CommandFactory {
    val commands = mutableListOf<Command>()
    val executor = Executors.newSingleThreadExecutor()
    fun addCommand(command: Command): CommandFactory {
        commands.add(command)
        return this
    }

    @SubscribeEvent
    fun onMessageEvent(event: MessageReceivedEvent) {
        if (event.author.isBot) return
        val args = event.message.rawContent.split(" ").toMutableList()
        val prefix = event.guild.getPrefix()

        when (args[0]) {
            "ardent" -> {
                args.removeAt(0)
            }
            else -> {
                if (args[0].startsWith(prefix)) {
                    args[0] = args[0].replace(prefix, "")
                } else return
            }
        }

        commands.forEach {
            cmd ->
            if (cmd.containsAlias(args[0])) {
                args.removeAt(0)
                executor.execute { cmd.execute(args, event) }
                return
            }
        }
    }
}

abstract class Command(val category: Category, val name: String, val description: String, vararg val aliases: String) {
    val help = mutableListOf<Pair<String, String>>()
    fun execute(args: MutableList<String>, event: MessageReceivedEvent) {
        if (event.channelType == ChannelType.PRIVATE)
            event.author.openPrivateChannel().queue {
                channel ->
                channel.send(event.author, "Please use commands inside a Discord server!")
            }
        else execute(event.member, event.textChannel, event.guild, args, event)
    }

    abstract fun execute(member: Member, channel: TextChannel, guild: Guild, arguments: MutableList<String>, event: MessageReceivedEvent)

    fun withHelp(syntax: String, description: String): Command {
        help.add(Pair(syntax, description))
        return this
    }

    fun displayHelp(channel: TextChannel, member: Member) {
        val prefix = channel.guild.getPrefix()
        val embed = embed("How can I use $prefix$name ?", member, Color.BLACK)
                .setThumbnail("https://upload.wikimedia.org/wikipedia/commons/f/f6/Lol_question_mark.png")
                .setFooter("Aliases: ${aliases.toList().stringify()}", member.user.avatarUrl)
        embed.appendDescription("*$description*\n")
        help.forEach { embed.appendDescription("\n${Emoji.SMALL_BLUE_DIAMOND}**${it.first}**: *${it.second}*") }
        if (help.size > 0) embed.appendDescription("\n\n**Example**: $prefix$name ${help[0].first}")
        embed.appendDescription("\n\nType ${channel.guild.getPrefix()}help to view a full list of commands")
        channel.send(member, embed)
        help.clear()
    }

    fun containsAlias(arg: String): Boolean {
        return name.equals(arg, true) || aliases.contains(arg)
    }
}

fun String.toCategory(): Category {
    when (this) {
        "Music & Radio" -> return Category.MUSIC
        "Bot & Server Information" -> return Category.INFO
        "Manage" -> return Category.MANAGE
        "Server Administration" -> return Category.ADMINISTRATE
        "Games" -> return Category.GAMES
        else -> return Category.INFO
    }
}

enum class Category(val fancyName: String, val description: String) {
    GAMES("Games", "Compete against your friends or users around the world in classic and addicting games!"),
    MUSIC("Music & Radio", "Play your favorite tracks or listen to the radio, all inside Discord"),
    INFO("Bot & Server Information", "Curious about the status of Ardent? Want to know how to help us continue development? This is the category for you!"),
    MANAGE("Manage", "Manage settings, both for Ardent and your server"),
    ADMINISTRATE("Server Administration", "Administrate your server: this category includes commands like warnings and mutes")
    ;

    override fun toString(): String {
        return fancyName
    }
}