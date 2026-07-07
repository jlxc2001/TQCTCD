这里放本地 ASR 模型文件。
推荐路线：sherpa-onnx streaming zipformer / streaming paraformer 中文或中英模型。
当前工程已经把 UI、跟读匹配、回读上一段逻辑和 ASR 接口解耦，替换 com.jlxc.teleprompter.asr.SherpaOnnxAsrEngine 即可接入真实本地模型。
