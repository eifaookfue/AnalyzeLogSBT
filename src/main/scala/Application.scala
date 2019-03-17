import java.io.File
import java.util.Date

import jp.co.nri.nefs.tool.models.Dialog

import scala.collection.JavaConverters._
import org.apache.commons.io.FileUtils
import scala.collection.mutable.ListBuffer


object Application {

  /**
    * キー：dialogNameとhandlerのタプル
    * バリュー：Dialogクラスのリスト。追加する必要があるためmutableなListBufferを用いる
    */
  private var dialogMap = Map[(String,Option[String]), ListBuffer[Dialog]]()

  private case class FileInfo(env: String, computer: String, userName: String, startTime: String){
    val tradeDate = startTime.take(8)
  }
  private def getFileInfo(fileName: String): FileInfo = {
    lazy val regex = """TradeSheet_(OMS_.*)_(.*)_([0-9][0-9][0-9][0-9][0-9][0-9])_([0-9]*).log$""".r
    val regex(env, computer, userName, startTime) = fileName
    FileInfo.apply(env, computer, userName, startTime)
  }

  private case class LineInfo(datetimeStr: String, logLevel: String, message: String,
                              thread: String, clazz: String){
    lazy val format = new java.text.SimpleDateFormat("yyyy-MM-dd hh:mm:ss.SSS")
    val datetime = format.parse(datetimeStr)
  }

  private def getLineInfo(line: String): LineInfo = {
    lazy val regex = """(2[0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]\s[0-9][0-9]:[0-9][0-9]:[0-9][0-9]\.[0-9][0-9][0-9])\s\[(.*)\]\[TradeSheet\](.*)\[(.*)\]\[(j.c.*)\]$""".r
    val regex(datetimeStr, logLevel, message, thread, clazz) = line
    LineInfo.apply(datetimeStr, logLevel, message, thread, clazz)
  }

  private def getContents(message: String): String = {
    lazy val regex = """\[(.*)\].*""".r
    val regex(contents) = message
    contents
  }
//run D:\tmp\TradeSheet_OMS_TKY_FID2CAD332_356435_20190315090918535.log
  def main(args: Array[String]): Unit = {
    if (args.size == 0){
      println("run fileName")
      sys.exit(-1)
    }
    val pathname = args(0)
    val file = new File(pathname)
    val fileInfo = getFileInfo(file.getName)
    println(fileInfo)

    val ite = FileUtils.lineIterator(new File(pathname))

    //val regex = """(.*)\[(.*)\]\[(.*)\](.*)\[(.*)\]\[(j.c.*)\]""".r
    //             2019            -03        -15          09        :10        :38        .  045                [OMS:INFO ][TradeSheet]
    //val regex = """(2[0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]\s[0-9][0-9]:[0-9][0-9]:[0-9][0-9]\.[0-9][0-9][0-9])\s\[(.*)\]\[TradeSheet\](.*)\[(.*)\]\[(j.c.*)\]$""".r
    //val messageRe = """\[(.*)\].*""".r
    //val format = new java.text.SimpleDateFormat("yyyy-MM-dd hh:mm:ss.SSS")
    var handler : Option[String] = None

    var handlerStartTime = new Date()
    var handlerEndTime = new Date()

    //var dialog = Dialog.apply()
    ite.asScala.foreach(line => {
      val lineInfo = getLineInfo(line)
      //val regex(datetimeStr, logLevel, message, thread, clazz) = line
      if (lineInfo.message contains "Handler start.") {
        handlerStartTime = lineInfo.datetime
        handler = Some(lineInfo.clazz)
      }
      else if (lineInfo.message contains "Dialog opend."){
        val dialogName = getContents(lineInfo.message)
      //else if ("Dialog opend.".equals(message)) {
        handlerEndTime = lineInfo.datetime
        val startupTime = handlerEndTime.getTime - handlerStartTime.getTime
        val dialog = Dialog.apply(dialogName, handler, None, fileInfo.userName,
          fileInfo.tradeDate, lineInfo.datetime, startupTime)
        println(s"dialog = $dialog")
        //handlerを初期化
        handler = None
        val key = (dialogName, handler)
        dialogMap.get(key) match {
          case Some(buf) => buf += dialog
          case None => dialogMap += (key -> ListBuffer(dialog))
        }
      }

      //println(s.substring(0,23))
      //val re = "\s\[\s\]"
      //re.findFirstMatchIn(s).get

//      s.split(" ") match {
//        case Array(date, time, _*) => println(date)
//      }

//      val ss = s.split(" ")
//      val d = format.parse(ss(0))
//      println(d)
    })
    ite.close
  }
}
