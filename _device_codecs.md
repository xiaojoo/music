
## VIDEO decoders

| media type | decoders | 仅软件 |
|---|---|---|
| video/3gpp | c2.mtk.h263.decoder, c2.android.h263.decoder | some |
| video/av01 | c2.android.av1.decoder | YES-all |
| video/avc | c2.mtk.avc.decoder, c2.android.avc.decoder | some |
| video/hevc | c2.mtk.hevc.decoder, c2.android.hevc.decoder | some |
| video/mp4v-es | c2.mtk.mpeg4.decoder, c2.android.mpeg4.decoder | some |
| video/mpeg2 | c2.mtk.mpeg2.decoder | no |
| video/x-ms-wmv | c2.mtk.vc1.decoder | no |
| video/x-vnd.on2.vp8 | c2.mtk.vpx.decoder, c2.android.vp8.decoder | some |
| video/x-vnd.on2.vp9 | c2.mtk.vp9.decoder, c2.android.vp9.decoder | some |

## AUDIO decoders

| media type | decoders | 仅软件 |
|---|---|---|
| audio/3gpp | c2.android.amrnb.decoder, OMX.google.amrnb.decoder | some |
| audio/ac3 | OMX.dolby.ac3.decoder | no |
| audio/ac4 | OMX.dolby.ac4.decoder | no |
| audio/alac | c2.mtk.alac.decoder | no |
| audio/amr-wb | c2.android.amrwb.decoder, OMX.google.amrwb.decoder | some |
| audio/eac3 | OMX.dolby.eac3.decoder | no |
| audio/eac3-joc | OMX.dolby.eac3-joc.decoder | no |
| audio/flac | c2.android.flac.decoder, OMX.google.flac.decoder | some |
| audio/g711-alaw | c2.android.g711.alaw.decoder, OMX.google.g711.alaw.decoder | some |
| audio/g711-mlaw | c2.android.g711.mlaw.decoder, OMX.google.g711.mlaw.decoder | some |
| audio/gsm | OMX.MTK.AUDIO.DECODER.GSM | no |
| audio/mp4a-latm | c2.android.aac.decoder, OMX.google.aac.decoder | some |
| audio/mpeg | c2.mtk.mp3.decoder, OMX.MTK.AUDIO.DECODER.MP3, c2.android.mp3.decoder, OMX.google.mp3.decoder | some |
| audio/opus | c2.android.opus.decoder, OMX.google.opus.decoder | some |
| audio/raw | c2.android.raw.decoder, OMX.google.raw.decoder | some |
| audio/vorbis | c2.android.vorbis.decoder, OMX.google.vorbis.decoder | some |
| audio/x-adpcm-dvi-ima | c2.mtk.adpcm-dvi-ima.decoder | no |
| audio/x-adpcm-ms | c2.mtk.adpcm-ms.decoder | no |

## key formats presence check (device media type names, re-verified)

- video/avc  H.264      -> HW c2.mtk.avc.decoder + SW          OK
- video/hevc H.265      -> HW c2.mtk.hevc.decoder, size-range 16x16-4096x2176, perf-point 3840x2160@30, Main10/HDR10  OK 4K
- video/x-vnd.on2.vp9   -> HW c2.mtk.vp9.decoder + SW          OK
- video/av01 AV1        -> SW c2.android.av1.decoder ONLY, size-range 2x2-1920x1080  1080p 可, 4K 不可
- video/mp4v-es / mpeg2 / x-ms-wmv(VC1) -> HW                 OK
- audio/mpeg MP3, mp4a-latm AAC, vorbis, opus, flac, alac, gsm, amr, raw(WAV)  OK
- audio/ac3, eac3, eac3-joc, ac4 -> HW OMX.dolby.*           OK
- DTS / DTS-HD          -> 0 decoders (grep -ic dts = 0)       无声
- TrueHD                -> absent                               无声
- extractors: mp4/mov/m4a/m4v, mkv/mka/webm, ts/m2ts/mts, ogg/oga/opus, wav, flac, mp3/mpeg, aac, amr, midi
- 无 extractor: avi, rm/rmvb, asf/wmv, fla? (flac 有)           AVI/RMVB 整文件不可解

## container extractors on device

(见上 extractor 行, 原始 dumpsys media.extractor 输出)
