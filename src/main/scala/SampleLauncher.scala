import java.io.File

import scala.collection.JavaConverters._
import org.apache.commons.io.FileUtils


object SampleLauncher {
  def main(args: Array[String]): Unit = {
    println("Hello")
    val ite = FileUtils.lineIterator(new File("D:\\tmp\\TradeSheet_OMS_TKY_FID2CAD332_356435_20181210075815027.log"))

    val regex = """(.*)\[(.*)\]\[(.*)\](.*)\[(.*)\]\[(.*)\]""".r
    val format = new java.text.SimpleDateFormat("yyyy-MM-dd hh:mm:ss.SSS ")
    var handlerStartTime = 0
    ite.asScala.foreach(s => {
      val regex(datetimeStr, level, apl, message, thread, cls) = s
      val datetime = format.parse(datetimeStr)
      println(s"$datetime $message")
      handlerStartTime = if ("Handler start.".equals(message)) datetime
      println(s"$handlerStartTime")

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
