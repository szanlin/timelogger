package com.y3lc.timelogger.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DirectionsBike
import androidx.compose.material.icons.automirrored.rounded.DirectionsWalk
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.DirectionsBus
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.Flight
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LocalTaxi
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.SelfImprovement
import androidx.compose.material.icons.rounded.SportsKabaddi
import androidx.compose.material.icons.rounded.Subway
import androidx.compose.material.icons.rounded.Surfing
import androidx.compose.material.icons.rounded.Train
import androidx.compose.material.icons.rounded.Work
import androidx.compose.ui.graphics.vector.ImageVector

data class ActivityTypeIcon(
    val key: String,
    val label: String,
    val imageVector: ImageVector,
)

object ActivityTypeIcons {
    val all = listOf(
        ActivityTypeIcon("sleep", "睡觉", Icons.Rounded.Bedtime),
        ActivityTypeIcon("walk", "走路", Icons.AutoMirrored.Rounded.DirectionsWalk),
        ActivityTypeIcon("cycle", "骑车", Icons.AutoMirrored.Rounded.DirectionsBike),
        ActivityTypeIcon("meeting", "开会", Icons.Rounded.Groups),
        ActivityTypeIcon("book", "阅读", Icons.AutoMirrored.Rounded.MenuBook),
        ActivityTypeIcon("work", "工作", Icons.Rounded.Work),
        ActivityTypeIcon("study", "学习", Icons.Rounded.School),
        ActivityTypeIcon("exercise", "运动", Icons.Rounded.FitnessCenter),
        ActivityTypeIcon("meal", "饮食", Icons.Rounded.Restaurant),
        ActivityTypeIcon("home", "家务", Icons.Rounded.Home),
        ActivityTypeIcon("music", "娱乐", Icons.Rounded.MusicNote),
        ActivityTypeIcon("travel", "出行", Icons.Rounded.Flight),
        ActivityTypeIcon("meditation", "冥想", Icons.Rounded.SelfImprovement),
        ActivityTypeIcon("train", "高铁", Icons.Rounded.Train),
        ActivityTypeIcon("subway", "地铁", Icons.Rounded.Subway),
        ActivityTypeIcon("taxi", "打车", Icons.Rounded.LocalTaxi),
        ActivityTypeIcon("bus", "公交", Icons.Rounded.DirectionsBus),
        ActivityTypeIcon("surf", "冲浪", Icons.Rounded.Surfing),
        ActivityTypeIcon("pair_sport", "双人运动", Icons.Rounded.SportsKabaddi),
    )

    private val iconsByKey = all.associateBy(ActivityTypeIcon::key)

    fun isSupported(key: String): Boolean = key in iconsByKey

    fun getByKey(key: String): ActivityTypeIcon = iconsByKey[key] ?: iconsByKey.getValue("meeting")
}
