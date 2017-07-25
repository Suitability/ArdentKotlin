package utils

class GuildData(var prefix : String, var musicSettings : MusicSettings, var advancedPermissions : MutableList<String>)

class MusicSettings(var announceNewMusic : Boolean = false, var singleSongInQueueForMembers : Boolean = true, var membersCanMoveBot : Boolean = true, var membersCanSkipSongs : Boolean = false)