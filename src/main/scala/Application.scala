import java.io.File
import java.sql.Timestamp
import java.util.Date

import jp.co.nri.nefs.tool.models.Dialog

import scala.collection.JavaConverters._
import org.apache.commons.io.FileUtils

import scala.collection.mutable.ListBuffer
import scala.util.matching.Regex
import com.typesafe.scalalogging.LazyLogging

import slick.jdbc.H2Profile.api._
import scala.concurrent.ExecutionContext.Implicits.global

import scala.concurrent.Await
import scala.concurrent.duration.Duration

import scala.collection.mutable.ArrayBuffer

//TODO 2019/03/31 Completeの場合、Button Pressedの前にDialog名が表示されない。
//GetDialogNameの引数にClazzを追加し、DialogがNoneの場合Clazzを利用
object Application extends LazyLogging{


  class DialogDetail(tag: Tag) extends Table[Dialog](tag, "DIALOG_DETAIL"){
//    case class Dialog(handler: String, dialogName: Option[String], action: Option[String],
    //                    destinationType: Option[String], userName: String, tradeDate: String, time: Date, startupTime: Long)
    def handler = column[String]("HANDLER")
    def dialogName = column[Option[String]]("DIALOG_NAME")
    def action = column[Option[String]]("ACTION")
    def destinationType = column[Option[String]]("DESTINATION_TYPE")
    def userName = column[String]("USER_NAME")
    def tradeDate = column[String]("TRADE_DATE")
    def time = column[Timestamp]("TIME")
    def startupTime = column[Long]("STARTUP_TIME")
    def * = (handler, dialogName, action, destinationType, userName, tradeDate, time, startupTime) <> (Dialog.tupled, Dialog.unapply)
  }

  val details = TableQuery[DialogDetail]

  /**
    * キー：dialogName
    * バリュー：Dialogクラスのリスト。追加する必要があるためmutableなListBufferを用いる
    */
  private var dialogMap = Map[Option[String], ListBuffer[Dialog]]()

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
    val datetime = new Timestamp(format.parse(datetimeStr).getTime)
  }

  private def getLineInfo(line: String): LineInfo = {
    lazy val regex = """(2[0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]\s[0-9][0-9]:[0-9][0-9]:[0-9][0-9]\.[0-9][0-9][0-9])\s\[(.*)\]\[TradeSheet\](.*)\[(.*)\]\[(j.c.*)\]$""".r
    val regex(datetimeStr, logLevel, message, thread, clazz) = line
    LineInfo.apply(datetimeStr, logLevel, message, thread, clazz)
  }
  private def getDialogName(message: String): Option[String] = {
    lazy val regex = """\[(.*)\].*""".r
    regexOption(regex, message)
  }

  private def getButtonAction(message: String) : Option[String] = {
    lazy val regex = """.*\((.*)\).*""".r
    regexOption(regex, message)
  }

  private def regexOption(regex: Regex, message: String):Option[String] = {
    message match {
     case regex(contents) => return Some(contents)
     case _ => return None
    }
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
    var handler : String = ""
    var handlerStartTime = new Date()
    var handlerEndTime = new Date()


    //var dialog = Dialog.apply()
    ite.asScala.foreach(line => {
      val lineInfo = getLineInfo(line)
      //val regex(datetimeStr, logLevel, message, thread, clazz) = line
      if (lineInfo.message contains "Handler start.") {
        handlerStartTime = lineInfo.datetime
        handler = lineInfo.clazz
      }
      //[New Basket]Dialog opened.[main][j.c.n.n.o.r.p.d.b.NewBasketDialog$1]
      //[TradeSheet]Opened.[main][j.c.n.n.o.r.p.d.c.QuestionDialog]
      else if ((lineInfo.message contains "Dialog opened.") || (lineInfo.message contains "Opened.")){
        val dialogName = if (lineInfo.message contains "Dialog opened.") {
          getDialogName(lineInfo.message)
        } else {
          //Completeのときは[TradeSheet]Opened.[main][j.c.n.n.o.r.p.d.c.QuestionDialog]
          //といった感じなので、clazzをdialogNameとする
          Some(lineInfo.clazz)
        }

      //else if ("Dialog opened.".equals(message)) {
        handlerEndTime = lineInfo.datetime
        val startupTime = handlerEndTime.getTime - handlerStartTime.getTime
        val dialog = Dialog.apply(handler, dialogName, None, None, fileInfo.userName,
          fileInfo.tradeDate, lineInfo.datetime, startupTime)
        println(s"dialog = $dialog")
        //たとえばNewOrderListのDialogがOpenされた後にSelect Basketが起動するケースは
        //handelerをNewOrderListとする
        handler = dialogName.getOrElse("")
        dialogMap.get(dialogName) match {
          case Some(buf) => buf += dialog
          case None => dialogMap += (dialogName -> ListBuffer(dialog))
        }
      }
      else if (lineInfo.message contains "Button event ends") {
        val dialogName = getDialogName(lineInfo.message)
        val action = getButtonAction(lineInfo.message)
        dialogMap.get(dialogName) match {
          case Some(buf) => buf.update(buf.length-1, buf.last.copy(action = action))
          case None => println("Error")
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

    println(dialogMap)
    //logger.info("abc")

    val a = for ((k, v) <- dialogMap) yield v.last
    println(s"a=$a")
    val db = Database.forConfig("h2mem1")

    try {
      val setup = DBIO.seq(
        details.schema.create,
        //suppliers += (101, "Acme, Inc.",      "99 Market Street", "Groundsville", "CA", "95199"),
        details ++= a
      )

      val setupFuture = db.run(setup)
      val resultFuture = setupFuture.flatMap { _ =>
        db.run(details.result).map(_.foreach {
          //case class Dialog(handler: String, dialogName: Option[String], action: Option[String],
          //                  destinationType: Option[String], userName: String, tradeDate: String, time: Timestamp, startupTime: Long)
          case Dialog(handler, dialogName, action, destinationType, userName, tradeDate, time, startupTime) =>
            println(dialogName)
          case _ => println("error")
        })
      }
      Await.result(resultFuture, Duration.Inf)

    } finally db.close
    println("end")



  }
}
