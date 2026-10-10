# FFmpeg 解码器由 JNI 按类名反射回调，方法名不能被改
-keep class androidx.media3.decoder.ffmpeg.** { *; }
