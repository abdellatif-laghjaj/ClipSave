package com.abdellatif.clipsave.media

import androidx.core.content.FileProvider

/** FileProvider subclass used to securely expose legacy (Android 8/9) downloads. */
class ClipSaveFileProvider : FileProvider()
