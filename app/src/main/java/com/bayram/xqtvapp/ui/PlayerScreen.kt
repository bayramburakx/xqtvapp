package com.bayram.xqtvapp.ui

import androidx.compose.runtime.Composable
import androidx.activity.compose.BackHandler
import com.bayram.xqtvapp.PlayReq

/** Player yonlendirici: canli ve VOD icin ayri profesyonel oynaticilar. */
@Composable
fun PlayerScreen(req: PlayReq, onBack: () -> Unit) {
    BackHandler { onBack() }
    if (req.isLive) LivePlayerScreen(req = req, onBack = onBack)
    else VodPlayerScreen(req = req, onBack = onBack)
}
