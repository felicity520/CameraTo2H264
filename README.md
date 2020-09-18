# CameraTo2H264
使用不同分辨率测试主板H264编码性能。
首先camera1预览有一个流，然后用libyuv对码流进行旋转缩放，然后再单独显示在一个surface上，预览也有一个画面，libyuv处理之后也会有一个画面，就是两路的码流，再分别传进H264进行编码。

参考链接：
https://github.com/lesliebeijing/LibyuvDemo
https://www.jianshu.com/p/b785dde9c0f0
https://juejin.im/post/6844903949074432007

