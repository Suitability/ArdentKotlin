package events

import net.dv8tion.jda.core.entities.Role
import net.dv8tion.jda.core.entities.TextChannel
import net.dv8tion.jda.core.events.guild.GuildJoinEvent
import net.dv8tion.jda.core.events.guild.GuildLeaveEvent
import net.dv8tion.jda.core.events.guild.member.GuildMemberJoinEvent
import net.dv8tion.jda.core.events.guild.member.GuildMemberLeaveEvent
import net.dv8tion.jda.core.hooks.SubscribeEvent
import utils.*

class JoinRemoveEvents {
    @SubscribeEvent
    fun onGuildAdd(e: GuildJoinEvent) {
        val guild = e.guild
        logChannel?.send("\uD83D\uDC4D Joined guild ${guild.name} - ${guild.members.size} members and ${guild.members.filter { it.user.isBot }.size} bots")
        SimpleLoggedEvent(guild.id, EventType.JOINED_GUILD).insert("events")
        try {
            (guild.defaultChannel ?: guild.getTextChannelById(guild.id))?.sendMessage("")?.queue()
        } catch (e: Exception) {
            return
        }
    }

    @SubscribeEvent
    fun onMemberJoin(e: GuildMemberJoinEvent) {
        val data = e.guild.getData()
        val joinMessage = data.joinMessage
        if (joinMessage != null) {
            val channel: TextChannel? = e.guild.getTextChannelById(joinMessage.second ?: "1")
            if (channel != null) {
                if (joinMessage.first /* Message */ != null && !joinMessage.first!!.isEmpty()) {
                    channel.send(joinMessage.first!!.replace("\$username", e.member.withDiscrim())
                            .replace("\$usermention", e.member.asMention)
                            .replace("\$membercount", e.guild.members.size.toString())
                            .replace("\$servername", e.guild.name)
                    )
                }
            }
        }
        if (data.defaultRole != null) {
            val role: Role? = e.guild.getRoleById(data.defaultRole)
            if (role != null) {
                try {
                    e.guild.controller.addRolesToMember(e.member, role).reason("Default Role - Automatic Addition")
                            .queue({}, {
                                e.member.user.openPrivateChannel().queue({ channel ->
                                    channel.send("Unable to give you the default role **{0}** in **{1}**. Please contact the server owner or administrators to let them know.".tr(e.guild, role.name, e.guild.name))
                                })
                            })
                } catch (e: Exception) {
                }
            }
        }
    }

    @SubscribeEvent
    fun onMemberLeave(e: GuildMemberLeaveEvent) {
        val data = e.guild.getData()
        val leaveMessage = data.leaveMessage
        if (leaveMessage != null) {
            val channel: TextChannel? = e.guild.getTextChannelById(leaveMessage.second ?: "1")
            if (channel != null) {
                if (leaveMessage.first /* Message */ != null && !leaveMessage.first!!.isEmpty()) {
                    channel.send(
                            leaveMessage.first!!.replace("\$username", "**${e.member.withDiscrim()}**")
                                    .replace("\$usermention", "**${e.member.withDiscrim()}**")
                                    .replace("\$membercount", e.guild.members.size.toString())
                                    .replace("\$servername", e.guild.name)
                    )
                }
            }
        }
    }

    @SubscribeEvent
    fun onGuildLeave(e: GuildLeaveEvent) {
        SimpleLoggedEvent(e.guild.id, EventType.LEFT_GUILD).insert("events")
        val guild = e.guild
        guild.owner.user.openPrivateChannel().queue {
            it.send("We're sorry to see you leave. If you had any issues that caused you to remove Ardent, you can join https://discord.gg/sVkfYbX and tell us.")
        }
        logChannel?.send("\uD83D\uDC4E Left guild ${guild.name} - ${guild.members.size} members and ${guild.members.filter { it.user.isBot }.size} bots")
    }
}