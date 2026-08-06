package io.sanato.logkit.format

/**
 * 任何"这段字节不是我们期望的样子"都走这个类型——文件头/帧头 CRC 不过、magic
 * 不对、长度字段超界。解密工具与写入路径的读取端都只需要认识这一种异常。
 */
internal class LogFormatException(
    message: String,
) : Exception(message)
